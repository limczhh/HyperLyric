package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 处理总预算：进入 processResult 时创建，每步网络前检查剩余时间。
 *
 * 宿主对单次处理器调用有 40s 硬超时（PluginConstants.MAX_PROCESSOR_TIMEOUT_MS），
 * 插件自设 34s 总预算，保证结果在硬超时前产出（spec §3.4）。
 */
internal class ProcessingBudget(private val budgetMs: Long) {
    private val deadlineAt = System.currentTimeMillis() + budgetMs

    fun remainingMs(): Long = deadlineAt - System.currentTimeMillis()

    fun isExhausted(): Boolean = remainingMs() <= 0

    /** 剩余时间是否足够覆盖一次尝试（约等于 connect 超时） */
    fun hasEnoughForAttempt(minAttemptMs: Long): Boolean = remainingMs() >= minAttemptMs
}

/**
 * AMLL TTML DataBase 网络客户端（自 main 分支 AmllTtmlClient 移植，OkHttp → HttpURLConnection）
 *
 * - 独立请求语义：connect 超时 5s、read 超时 8s（对齐 main 分支）
 * - HTTP 429/5xx 指数退避重试：初始 1s、倍率 2、最多 2 次（1s/2s；main 为 3 次，
 *   插件受 34s 处理预算约束收紧，spec §3.4）
 * - 网络异常（超时/断网/IOException）与其余 HTTP 错误不重试，直接返回 null
 * - 每次尝试与重试前检查线程中断与剩余预算
 *
 * main 分支在 systemui 混合类加载环境下 Retrofit suspend 反射不可靠的教训
 * （main 提交 938560a/3934f16，需 ProGuard 保留泛型签名）在此天然规避：
 * HttpURLConnection 为平台 API，无反射调用链。
 */
internal class AmllTtmlClient(private val logger: PluginLogger) {

    companion object {
        private const val BASE_URL = "https://api.amll.dev/"
        private const val GET_PATH = "v1/lyrics/get"
        private const val SEARCH_PATH = "v1/lyrics/search"

        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val RETRY_BACKOFF_MULTIPLIER = 2L
        private const val MAX_RETRIES = 2
        private const val HTTP_TOO_MANY_REQUESTS = 429
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
     * @return 命中且 lyrics 非空时返回 [SongItem]；未命中/空 lyrics/失败返回 null
     */
    fun fetchById(id: Long, budget: ProcessingBudget): SongItem? {
        val body = executeWithRetry(
            requestLabel = "id_$id",
            url = buildUrl(GET_PATH, listOf("id" to id.toString())),
            budget = budget
        ) ?: return null
        return extractWithLyrics(AmllModels.parseGetResponse(body))
    }

    /**
     * 按歌名/歌手/专辑模糊搜索，返回最佳匹配条目。空字段不传，由 AMLL 服务端按 AND 交集匹配。
     * 服务端排序不保证语义一致（翻唱/Live/串烧可能排在原版之前），而命中结果会被永久缓存，
     * 因此客户端对返回条目做 title/artist 校验，仅接受可交叉验证的条目。
     *
     * @return 首个通过客户端校验的条目（不含 lyrics）；无结果/校验失败返回 null
     */
    fun searchByMetadata(
        title: String?,
        artist: String?,
        album: String?,
        budget: ProcessingBudget
    ): SongItem? {
        val musicName = title?.takeIf { it.isNotBlank() }
        val artistName = artist?.takeIf { it.isNotBlank() }
        val albumName = album?.takeIf { it.isNotBlank() }
        if (musicName == null && artistName == null && albumName == null) {
            logger.debug("搜索未执行: 无搜索参数")
            return null
        }
        val params = buildList {
            musicName?.let { add("musicName" to it) }
            artistName?.let { add("artistName" to it) }
            albumName?.let { add("albumName" to it) }
        }
        val body = executeWithRetry(
            requestLabel = "search",
            url = buildUrl(SEARCH_PATH, params),
            budget = budget
        ) ?: return null
        val items = AmllModels.parseSearchResponse(body) ?: return null
        val item = items.firstOrNull { AmllMatch.isPlausibleMatch(it, musicName, artistName) }
        if (item == null) {
            if (items.isEmpty()) {
                logger.debug("搜索未命中: 无结果")
            } else {
                logger.debug(
                    "搜索未命中: 结果均不匹配, total=${items.size}, " +
                            "first=${items.firstOrNull()?.musicNames?.joinToString("/") ?: "-"}"
                )
            }
            return null
        }
        return item
    }

    /** 提取携带非空 lyrics 的条目；status=200 但 lyrics 为空字符串/null 视为未命中 */
    private fun extractWithLyrics(item: SongItem?): SongItem? {
        if (item == null || item.lyrics.isNullOrBlank()) {
            logger.debug("查询命中但歌词为空")
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
                logger.debug("请求被中断: request=$requestLabel")
                return null
            }
            if (!budget.hasEnoughForAttempt(CONNECT_TIMEOUT_MS.toLong())) {
                logger.debug("预算不足，放弃请求: remaining=${budget.remainingMs()}ms, request=$requestLabel")
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
                    return connection.inputStream
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                }
                val retryable = code == HTTP_TOO_MANY_REQUESTS || code in 500..599
                if (!retryable || attempt >= MAX_RETRIES) {
                    logger.debug("请求失败: code=$code, retries=$attempt, request=$requestLabel")
                    return null
                }
                attempt++
                logger.debug(
                    "HTTP 错误重试: code=$code, attempt=$attempt/$MAX_RETRIES, " +
                            "delay=${retryDelay}ms, request=$requestLabel"
                )
            } catch (e: IOException) {
                logger.debug("网络错误: type=${e.javaClass.simpleName}, request=$requestLabel")
                return null
            } catch (e: Exception) {
                // 反序列化等本地异常：不重试（对齐 main：异常不伪装成重试场景）
                logger.debug("请求异常: type=${e.javaClass.simpleName}, request=$requestLabel")
                return null
            } finally {
                connection?.disconnect()
            }

            // 重试前检查预算：等待 + 一次尝试的最小开销
            if (budget.remainingMs() < retryDelay + CONNECT_TIMEOUT_MS) {
                logger.debug("预算不足，放弃请求: remaining=${budget.remainingMs()}ms, request=$requestLabel")
                return null
            }
            try {
                Thread.sleep(retryDelay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.debug("请求被中断: request=$requestLabel")
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
        return "$BASE_URL$path?$query"
    }
}
