package com.lidesheng.hyperlyric.root.lyricenhancement.amll

import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 处理总预算：进入歌词增强功能时创建，每步网络前检查剩余时间。
 *
 * 宿主对单次功能调用有 40s 硬超时，功能自身设置 34s 总预算，
 * 保证结果在硬超时前产出。
 */
internal class ProcessingBudget(private val budgetMs: Long) {
    private val deadlineAt = System.currentTimeMillis() + budgetMs

    fun remainingMs(): Long = deadlineAt - System.currentTimeMillis()

    fun isExhausted(): Boolean = remainingMs() <= 0

    /** 剩余时间是否足够覆盖一次尝试（约等于 connect 超时） */
    fun hasEnoughForAttempt(minAttemptMs: Long): Boolean = remainingMs() >= minAttemptMs
}

/**
 * 单次搜索请求的参数组合（多策略检索的最小单元）
 *
 * AMLL 服务端对 `artistName` 的判定是「库中**单个**歌手元素包含查询整串」，
 * 既不切分分隔符也不做多值 OR —— 因此多歌手场景只能拆成单个 token 逐个请求，
 * [artist] 为 null 表示本次不携带 `artistName`。
 */
internal data class AmllSearchPlan(
    val musicName: String?,
    val artist: String?,
    val albumName: String?,
)

/**
 * AMLL TTML DataBase 网络客户端（自 main 分支 AmllTtmlClient 移植，OkHttp → HttpURLConnection）
 *
 * - 独立请求语义：connect 超时 5s、read 超时 8s（对齐 main 分支）
 * - HTTP 429/5xx 指数退避重试：初始 1s、倍率 2、最多 2 次（1s/2s；main 为 3 次，
 *   功能受 34s 处理预算约束收紧）
 * - 网络异常（超时/断网/IOException）与其余 HTTP 错误不重试，直接返回 null
 * - 每次尝试与重试前检查线程中断与剩余预算
 * - 搜索按 [buildSearchPlans] 逐个策略请求（服务端 `artistName` 只匹配单个歌手名）
 * - 搜索阶段为 [fetchById] 预留预算，不足则停止扩展策略，避免「搜到了却取不回正文」
 * - 日志打印实际请求 URL、items 计数与 HTTP 404 语义：404 是「该查询无歌词」，
 *   200 空数组是「搜索成功但无结果」，两者必须可区分
 *
 * main 分支在 systemui 混合类加载环境下 Retrofit suspend 反射不可靠的教训
 * （main 提交 938560a/3934f16，需 ProGuard 保留泛型签名）在此天然规避：
 * HttpURLConnection 为平台 API，无反射调用链。
 */
internal class AmllTtmlClient {

    companion object {
        private const val LOG_TAG = "LyricEnhancement/AmllTtml/Client"
        private const val DEFAULT_BASE_URL = "https://api.amll.dev/"
        private const val GET_PATH = "v1/lyrics/get"
        private const val SEARCH_PATH = "v1/lyrics/search"

        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2L
        private const val MAX_RETRIES = 2
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /**
         * 多策略检索的请求次数上限（含 title-only 兜底）。
         *
         * 服务端只认「单个歌手 token」，多歌手必须逐个 token 各发一次请求；
         * 上限用于防超长歌手列表打满 34s 处理预算。
         */
        private const val MAX_SEARCH_PLANS = 8

        /**
         * 搜索阶段为后续「按 id 取正文」预留的预算（等于一次请求尝试的上限）。
         *
         * 搜索结果本身不含歌词，命中后必须再发一次 [GET_PATH] 才能拿到正文；
         * 若搜索把预算耗尽，就会出现「搜到了却取不回正文」的本轮未命中。
         * 故每轮搜索前要求剩余预算不少于「本次尝试的闸门 + 本余量」，
         * 不足则停止扩展策略，把余量留给取正文。
         */
        private const val FETCH_RESERVE_MS = CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS

        /**
         * 日志中 URL 的最大长度。
         *
         * 客户端此前不打印 URL，导致「HTTP 200 但 items 为空」只能靠分支唯一可达性反推；
         * 日志需要逐字看到实际查询参数，但超长 title/artist 会挤爆单行日志，故截断尾部。
         */
        private const val MAX_LOGGED_URL_LENGTH = 300
    }

    @Volatile
    private var baseUrl = DEFAULT_BASE_URL

    fun updateBaseUrl(value: String) {
        baseUrl = normalizeBaseUrl(value)
    }

    /**
     * 按平台 ID 精确获取歌词（如网易云 ncmMusicId）。
     *
     * @return 命中且 lyrics 非空时返回 [SongItem]（含 musicNames/artistNames 供交叉校验）；
     * 未命中/空 lyrics/失败返回 null
     */
    fun fetchByPlatformId(
        field: AmllPlatformIdField,
        songId: String,
        budget: ProcessingBudget
    ): SongItem? {
        val body = executeWithRetry(
            requestLabel = "platform_${field.name}",
            url = buildUrl(GET_PATH, listOf(field.queryParam to songId)),
            budget = budget
        ) ?: return null
        return extractWithLyrics(AmllModels.parseGetResponse(body))
    }

    /**
     * 按 AMLL 内部 id 精确获取歌词（search 回退路径使用）。
     *
     * 取正文前先检查预算：搜索结果本身不含歌词，命中后必须再发一次请求才能拿到正文；
     * 若预算不足则明确记录「因预算不足放弃取正文」，与「服务端未命中」区分开——
     * 两者都返回 null，但修复方向完全不同（前者是本地预算问题，后者是检索问题）。
     * 判定口径与 [executeWithRetry] 的闸门一致，不额外收紧。
     *
     * @return 命中且 lyrics 非空时返回 [SongItem]；未命中/空 lyrics/失败返回 null
     */
    fun fetchById(id: Long, budget: ProcessingBudget): SongItem? {
        if (!budget.hasEnoughForAttempt(CONNECT_TIMEOUT_MS.toLong())) {
            HookLogger.d(
                LOG_TAG,
                "预算不足，放弃按 id 取正文: id=$id, remaining=${budget.remainingMs()}ms"
            )
            return null
        }
        val body = executeWithRetry(
            requestLabel = "id_$id",
            url = buildUrl(GET_PATH, listOf("id" to id.toString())),
            budget = budget
        ) ?: return null
        return extractWithLyrics(AmllModels.parseGetResponse(body))
    }

    /**
     * 生成多策略检索的请求序列（纯函数，顺序即优先级）。
     *
     * 服务端把 `artistName` 当作**单个**歌手名做「库中某元素包含查询整串」判定
     * （不切分分隔符、不做多值 OR），因此多歌手串必须拆成单个 token 逐个请求；
     * 先试更具体（更长）的歌手 token，让不依赖歌手的 title-only 兜底排在最后
     * （并为它保留一个名额，不被歌手策略挤掉）。
     */
    fun buildSearchPlans(
        title: String?,
        artist: String?,
        album: String?,
        maxPlans: Int = MAX_SEARCH_PLANS,
    ): List<AmllSearchPlan> {
        if (maxPlans <= 0) return emptyList()
        val musicName = title?.takeIf { it.isNotBlank() }
        val albumName = album?.takeIf { it.isNotBlank() }
        val tokens = artist?.let { AmllMatch.splitArtistTokens(it) }.orEmpty()
        val titlePlans = if (musicName != null) {
            listOf(AmllSearchPlan(musicName, null, albumName))
        } else {
            emptyList()
        }
        val artistSlots = maxPlans - titlePlans.size
        val artistPlans = tokens
            .distinctBy { it.lowercase() }
            .sortedByDescending { it.length }
            .take(artistSlots)
            .map { token -> AmllSearchPlan(musicName, token, albumName) }
        return artistPlans + titlePlans
    }

    /**
     * 按歌名/歌手/专辑模糊搜索：按 [buildSearchPlans] 的顺序逐个策略请求，
     * **每个**候选条目都必须通过 [AmllMatch.judge] 才可能被接受（服务端排序不保证语义一致，
     * 翻唱/Live/串烧可能排在原版之前，而命中结果会被永久缓存）。
     *
     * 关键在于「请求参数」与「校验依据」分离：请求里的 `artistName` 只能是单个歌手 token
     * （服务端语义所限），但校验始终拿**完整**歌手串做交叉验证——若只拿当次 token 校验，
     * 仅参与和声的「同曲异版」也会被判为严格命中。
     *
     * 同一批结果内优先取 [AmllMatchVerdict.STRICT]（歌名互为包含 + 歌手占比达标），
     * 无严格命中时才退到 [AmllMatchVerdict.IDENTITY]（歌名近似相等 + 至少一位歌手可确认）：
     * 库中只登记部分歌手是常态，占比不足不等于「不是这首歌」，而「同曲异版」靠歌名近似相等即可拦下。
     *
     * 严格命中**跨策略**优先：策略循环内先接受 [AmllMatchVerdict.STRICT]，
     * 遇到 [AmllMatchVerdict.IDENTITY] 只记下候选并继续扫描后续策略——
     * 兜底候选不得顶掉后续策略可能给出的严格命中；全部策略都无严格命中时才返回首个兜底候选。
     *
     * @return 首个通过客户端校验的条目（不含 lyrics）；无结果/校验失败返回 null
     */
    fun searchByMetadata(
        title: String?,
        artist: String?,
        album: String?,
        budget: ProcessingBudget
    ): SongItem? {
        val plans = buildSearchPlans(title, artist, album)
        if (plans.isEmpty()) {
            HookLogger.d(LOG_TAG, "搜索未执行: 无搜索参数")
            return null
        }
        // 校验依据：完整的歌手串；纯分隔符/空白视为「无歌手信息」（此时只认歌名近似相等）
        val verifyArtist = artist?.takeIf {
            it.isNotBlank() && AmllMatch.splitArtistTokens(it).isNotEmpty()
        }
        var identityCandidate: SongItem? = null
        for ((index, plan) in plans.withIndex()) {
            if (Thread.currentThread().isInterrupted) {
                HookLogger.d(LOG_TAG, "请求被中断: request=search")
                return identityCandidate
            }
            if (budget.isExhausted()) {
                HookLogger.d(LOG_TAG, "预算耗尽: phase=搜索")
                return identityCandidate
            }
            // 取正文预留：本次搜索尝试的闸门 + 后续 fetchById 的一次尝试余量
            val needed = CONNECT_TIMEOUT_MS.toLong() + FETCH_RESERVE_MS
            if (budget.remainingMs() < needed) {
                HookLogger.d(
                    LOG_TAG,
                    "预算不足，停止扩展搜索策略: remaining=${budget.remainingMs()}ms, " +
                            "needed=${needed}ms, strategy=${index + 1}/${plans.size}"
                )
                return identityCandidate
            }
            val body = executeWithRetry(
                requestLabel = "search",
                url = buildUrl(SEARCH_PATH, searchParams(plan)),
                budget = budget
            ) ?: continue
            val items = AmllModels.parseSearchResponse(body) ?: continue
            val verdict = pickAcceptable(items, plan, verifyArtist, index, plans.size)
            if (verdict.item != null) {
                if (verdict.verdict == AmllMatchVerdict.STRICT) return verdict.item
                if (identityCandidate == null) {
                    identityCandidate = verdict.item
                    HookLogger.d(
                        LOG_TAG,
                        "搜索命中(兜底): 歌名近似相等且至少一位歌手可确认, " +
                                "strategy=${index + 1}/${plans.size}, id=${verdict.item.id}, " +
                                "继续扫描后续策略寻找严格命中"
                    )
                }
            }
            // 区分两种未命中：items=0 是「该查询无结果」（HTTP 200 空数组），
            // items>0 是「服务端有结果但均未通过客户端交叉校验」——二者修复方向完全不同
            if (items.isEmpty()) {
                HookLogger.d(
                    LOG_TAG,
                    "搜索未命中: strategy=${index + 1}/${plans.size}, items=0, " +
                            "artist=${plan.artist ?: "-"}"
                )
            } else if (verdict.item == null) {
                HookLogger.d(
                    LOG_TAG,
                    "搜索未命中: 结果均不匹配, strategy=${index + 1}/${plans.size}, " +
                            "items=${items.size}, artist=${plan.artist ?: "-"}, " +
                            "first=${items.first().musicNames?.joinToString("/") ?: "-"}"
                )
            }
        }
        if (identityCandidate != null) {
            HookLogger.d(
                LOG_TAG,
                "搜索回退取兜底候选: id=${identityCandidate.id}（全策略无严格命中）"
            )
        }
        return identityCandidate
    }

    /** 单批结果的挑选结论：条目 + 其判定等级（全部被拒时条目为 null） */
    private data class SearchPick(
        val item: SongItem?,
        val verdict: AmllMatchVerdict?,
    )

    /** 在一批结果中挑选可接受的条目：严格命中优先，其次兜底命中；全部被拒返回 null */
    private fun pickAcceptable(
        items: List<SongItem>,
        plan: AmllSearchPlan,
        verifyArtist: String?,
        index: Int,
        total: Int,
    ): SearchPick {
        var identity: SongItem? = null
        for (item in items) {
            when (AmllMatch.judge(item, plan.musicName, verifyArtist)) {
                AmllMatchVerdict.STRICT -> {
                    HookLogger.d(
                        LOG_TAG,
                        "搜索命中: strategy=${index + 1}/$total, id=${item.id}, " +
                                "artist=${plan.artist ?: "-"}"
                    )
                    return SearchPick(item, AmllMatchVerdict.STRICT)
                }

                AmllMatchVerdict.IDENTITY -> if (identity == null) identity = item

                AmllMatchVerdict.REJECT -> Unit
            }
        }
        return SearchPick(identity, identity?.let { AmllMatchVerdict.IDENTITY })
    }

    /** 把检索策略转成查询参数：空字段不传，由 AMLL 服务端按 AND 交集匹配 */
    private fun searchParams(plan: AmllSearchPlan): List<Pair<String, String>> = buildList {
        plan.musicName?.let { add("musicName" to it) }
        plan.artist?.let { add("artistName" to it) }
        plan.albumName?.let { add("albumName" to it) }
    }

    /** 提取携带非空 lyrics 的条目；status=200 但 lyrics 为空字符串/null 视为未命中 */
    private fun extractWithLyrics(item: SongItem?): SongItem? {
        if (item == null || item.lyrics.isNullOrBlank()) {
            HookLogger.d(LOG_TAG, "查询命中但歌词为空")
            return null
        }
        return item
    }

    /**
     * 带指数退避的请求执行器：
     * - 每次尝试前检查线程中断与剩余预算
     * - HTTP 429/5xx → 重试（1s/2s，最多 2 次；重试前检查预算能否覆盖等待+尝试）
     * - IOException（含超时/断网）与其余异常 → 不重试
     * - 其余 HTTP 错误 → 不重试
     */
    private fun executeWithRetry(
        requestLabel: String,
        url: String,
        budget: ProcessingBudget
    ): String? {
        var retryDelay = INITIAL_RETRY_DELAY_MS
        var attempt = 0
        while (true) {
            if (Thread.currentThread().isInterrupted) {
                HookLogger.d(LOG_TAG, "请求被中断: request=$requestLabel")
                return null
            }
            if (!budget.hasEnoughForAttempt(CONNECT_TIMEOUT_MS.toLong())) {
                HookLogger.d(LOG_TAG, "预算不足，放弃请求: remaining=${budget.remainingMs()}ms, request=$requestLabel")
                return null
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    HookLogger.d(
                        LOG_TAG,
                        "请求成功: code=200, request=$requestLabel, url=${shorten(url)}"
                    )
                    return connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                }
                val retryable = code == HTTP_TOO_MANY_REQUESTS || code in 500..599
                if (!retryable || attempt >= MAX_RETRIES) {
                    HookLogger.d(
                        LOG_TAG,
                        "请求失败: code=$code, reason=${httpReason(code)}, retries=$attempt, " +
                                "request=$requestLabel, url=${shorten(url)}"
                    )
                    return null
                }
                attempt++
                HookLogger.d(
                    LOG_TAG,
                    "HTTP 错误重试: code=$code, attempt=$attempt/$MAX_RETRIES, " +
                            "delay=${retryDelay}ms, request=$requestLabel"
                )
            } catch (e: IOException) {
                HookLogger.d(LOG_TAG, "网络错误: type=${e.javaClass.simpleName}, request=$requestLabel")
                return null
            } catch (e: Exception) {
                // 反序列化等本地异常：不重试（对齐 main：异常不伪装成重试场景）
                HookLogger.d(LOG_TAG, "请求异常: type=${e.javaClass.simpleName}, request=$requestLabel")
                return null
            } finally {
                connection?.disconnect()
            }

            // 重试前检查预算：等待 + 一次尝试的最小开销
            if (budget.remainingMs() < retryDelay + CONNECT_TIMEOUT_MS) {
                HookLogger.d(LOG_TAG, "预算不足，放弃请求: remaining=${budget.remainingMs()}ms, request=$requestLabel")
                return null
            }
            try {
                Thread.sleep(retryDelay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                HookLogger.d(LOG_TAG, "请求被中断: request=$requestLabel")
                return null
            }
            retryDelay *= RETRY_BACKOFF_MULTIPLIER
        }
    }

    /** 拼接 GET 请求 URL：空参数列表由调用方保证非空；参数值 URL 编码 */
    private fun buildUrl(path: String, params: List<Pair<String, String>>): String {
        val query = params.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "$baseUrl$path?$query"
    }

    /** 日志用 URL：超长时截断并标注实际长度，避免单行日志被第三方元数据撑爆 */
    private fun shorten(url: String): String =
        if (url.length <= MAX_LOGGED_URL_LENGTH) {
            url
        } else {
            url.take(MAX_LOGGED_URL_LENGTH) + "…(len=${url.length})"
        }

    /**
     * HTTP 状态码的 AMLL 语义：仅用于日志，不参与控制流。
     *
     * AMLL 对「查询无对应歌词」返回 **404**，而对「搜索成功但没有结果」返回
     * **200 + items 空数组** —— 两者都是正常的未命中，日志必须能区分，
     * 否则「200 空结果」只能靠分支唯一可达性反推。
     */
    private fun httpReason(code: Int): String = when (code) {
        404 -> "无该查询对应的歌词条目"
        400 -> "查询参数非法"
        HTTP_TOO_MANY_REQUESTS -> "请求过于频繁"
        else -> if (code in 500..599) "服务端错误" else "未知错误"
    }

    private fun normalizeBaseUrl(value: String): String {
        val candidate = value.trim()
        if (candidate.isEmpty() || candidate.any(Char::isWhitespace)) return DEFAULT_BASE_URL
        return runCatching {
            val url = URL(candidate)
            require(url.protocol == "http" || url.protocol == "https")
            require(url.host.isNotBlank())
            candidate.trimEnd('/') + "/"
        }.getOrElse { DEFAULT_BASE_URL }
    }
}
