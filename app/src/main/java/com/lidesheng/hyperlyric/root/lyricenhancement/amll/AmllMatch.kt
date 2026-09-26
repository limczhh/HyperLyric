package com.lidesheng.hyperlyric.root.lyricenhancement.amll

/**
 * AMLL 条目的客户端校验结论
 *
 * 命中结果会被**永久缓存**，因此「接受」必须分级留证：结论决定是否接受，日志记录依据。
 */
internal enum class AmllMatchVerdict {
    /** 严格通过：歌名互为包含，且请求歌手在条目中的命中占比达标 */
    STRICT,

    /** 兜底通过：歌名近似相等（只差版本装饰），且至少一位请求歌手可在条目中确认 */
    IDENTITY,

    /** 拒绝：歌名对不上，或歌手证据不足 */
    REJECT
}

/**
 * AMLL 搜索/探测结果的客户端交叉校验（自 main 分支 AmllTtmlClient 移植的纯函数）
 *
 * 服务端排序不保证语义一致（翻唱/Live/串烧可能排在原版之前），`artistName` 只做
 * 「库中**单个**歌手元素包含查询整串」的粗筛（不切分分隔符、不做多值 OR），
 * 而命中结果会被**永久缓存**，因此客户端必须对返回条目做 title/artist 交叉校验：
 * 校验过宽会把「同曲异版」当作目标曲目永久缓存，校验过松会让本可命中的歌曲回落原歌词。
 */
internal object AmllMatch {

    /**
     * 歌手接受阈值：请求的歌手 token 至少有一半能在条目里确认（占比 ≥ 50%）。
     *
     * 库中只登记部分歌手是常态，但「同曲异版」通常只有个别和声歌手同现，占比会明显偏低；
     * 按占比判定即可把后者拒之门外，而「任一 token 命中即通过」会把它放行并永久写入缓存。
     * 单歌手请求（请求 token 数 = 1）下阈值退化为「该歌手必须命中」，判定依旧严格。
     */
    private const val ARTIST_MATCH_RATIO_THRESHOLD = 0.5

    /**
     * 版本装饰关键词：带上它们只会改变「编排或来源」，不改变歌曲身份。
     * 用于兜底判定。
     */
    private val VERSION_MARKERS = listOf(
        "伴奏", "纯音乐", "和声", "女声", "男声", "童声", "合唱", "混音", "原版", "新版",
        "旧版", "重置版", "重制版", "重置", "正式版", "典藏版", "特别版", "豪华版", "纪念版",
        "单曲版", "专辑版", "现场版", "演唱会", "录音室", "试听", "片段", "剪辑", "完整版",
        "翻唱", "翻自", "中文版", "粤语版", "日语版", "英文版", "高音质", "无损", "高清", "超清",
        "remix", "remaster", "remastered", "version", "ver", "live", "cover", "inst",
        "instrumental", "karaoke", "acoustic", "reprise", "edit", "extended", "radio",
        "single", "sped up", "slowed", "off vocal", "tv size", "tv ver", "full ver", "short ver"
    )

    /** 成对括号的闭合 → 开启映射（用于剥离尾部的括号装饰） */
    private val BRACKET_PAIRS = mapOf(
        '）' to '（', ')' to '(', ']' to '[', '】' to '【', '》' to '《'
    )

    /** ASCII 关键词按整词判定：避免 `edit` 命中 `edition` 内部这类误剥离 */
    private val ASCII_WORD_SPLIT = Regex("[^a-z0-9]+")

    /**
     * 条目接受判定（搜索路径与平台探测路径共用的唯一入口）。
     *
     * - 歌名：至少一个 `musicNames` 与请求歌名「互为包含」（strict）或「近似相等」（identity）
     * - 歌手：请求 token 在条目中的命中占比 ≥ [ARTIST_MATCH_RATIO_THRESHOLD]（strict），
     *   或至少一位歌手可在条目中确认（identity）；无歌手信息时只认近似相等的歌名
     *
     * 返回 [AmllMatchVerdict]，调用方据此决定接受或拒绝并记录依据。
     */
    fun judge(item: SongItem, title: String?, artist: String?): AmllMatchVerdict {
        val names = item.musicNames.orEmpty()
        if (title != null && names.isEmpty()) return AmllMatchVerdict.REJECT
        val titleStrict = title == null || names.any { fuzzyContains(it, title) }
        val titleNear = title == null || names.any { isTitleNearEqual(it, title) }
        if (!titleStrict && !titleNear) return AmllMatchVerdict.REJECT

        if (artist == null) {
            // 无歌手信息：唯一的身份证据是歌名。此处不能退回「互为包含」——
            // 双向包含会把「同曲异版」当作目标曲目（见 [isTitleNearEqual]）。
            return if (titleNear) AmllMatchVerdict.IDENTITY else AmllMatchVerdict.REJECT
        }
        val requestTokens = splitArtistTokens(artist)
        if (requestTokens.isEmpty()) return AmllMatchVerdict.REJECT
        val ratio = artistMatchRatio(requestTokens, item)
        if (titleStrict && ratio >= ARTIST_MATCH_RATIO_THRESHOLD) return AmllMatchVerdict.STRICT
        // 库中只登记部分歌手是常态，占比不足不等于「不是这首歌」，
        // 但必须有至少一位歌手可确认，避免无歌手证据的接受
        if (titleNear && ratio > 0.0) return AmllMatchVerdict.IDENTITY
        return AmllMatchVerdict.REJECT
    }

    /**
     * 逐平台探测专用判定（平台 ID 已字面命中时的交叉校验）。
     *
     * 与搜索路径的 [judge] 不同：探测的前提是「平台 ID 精确命中」，条目与请求的相关性
     * 已由 ID 给出，故此处只要求**请求携带的每个字段各有证据**——歌名对得上、
     * 至少一位歌手可在条目中确认——而**不要求**歌手命中占比达到搜索路径的阈值：
     * 库中只登记部分歌手是常态，占比不足不能作为「不是这首歌」的证据，
     * 据此拒绝会让本可命中的歌曲回落原歌词。
     *
     * 跨平台 ID 撞号防护仍然保留：请求携带了哪个字段，该字段就必须有证据；
     * 两个字段都未提供时无法验证，一律不接受。
     */
    fun isProbeMatch(item: SongItem, title: String?, artist: String?): Boolean {
        if (title == null && artist == null) return false
        if (title != null) {
            val names = item.musicNames.orEmpty()
            if (names.isEmpty() || names.none { fuzzyContains(it, title) }) return false
        }
        if (artist != null) {
            val requestTokens = splitArtistTokens(artist)
            if (requestTokens.isEmpty()) return false
            if (artistMatchRatio(requestTokens, item) <= 0.0) return false
        }
        return true
    }

    /**
     * 歌名近似相等：归一化后相等，或只差尾部版本装饰。
     *
     * **不能**退回 [fuzzyContains] 的双向包含：双向包含会把「同曲异版」判为同一首，
     * 而专辑名同样拦不住它（版本后缀也包含原曲名）。
     * 反过来，歌名后缀常带版本标记的同曲上传，剥掉尾部装饰后相等，因此也算近似相等；
     * 但括号内容**必须**是版本装饰关键词才剥离——括号里写的是副标题、
     * 联名或其它版本名时保留原串，否则会把不同的歌判成同一首。
     * 副标题形态的同曲上传由「歌名互为包含」的严格判定覆盖，不受此收紧影响。
     */
    fun isTitleNearEqual(musicName: String, title: String): Boolean {
        val a = baseTitle(musicName)
        val b = baseTitle(title)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        return stripVersionDecoration(a) == stripVersionDecoration(b)
    }

    /**
     * 请求歌手 token 在条目歌手列表中的命中占比（0.0～1.0）。
     *
     * 分母是**请求** token 数（本次请求的歌手有多少能在库中确认）；每个条目 token
     * 最多与一个请求 token 配对（贪心），避免条目重复登记同一歌手抬高占比。
     */
    fun artistMatchRatio(requestTokens: List<String>, item: SongItem): Double {
        if (requestTokens.isEmpty()) return 0.0
        val available = item.artistNames.orEmpty().flatMap { splitArtistTokens(it) }.toMutableList()
        var hit = 0
        for (request in requestTokens) {
            val index = available.indexOfFirst { fuzzyContains(it, request) }
            if (index >= 0) {
                available.removeAt(index)
                hit++
            }
        }
        return hit.toDouble() / requestTokens.size
    }

    /** 归一化（小写 + 压缩空白）后的双向包含匹配：任一方包含另一方即视为匹配 */
    fun fuzzyContains(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        return na.contains(nb) || nb.contains(na)
    }

    /** 剥离尾部的版本装饰关键词（可叠加，反复剥离直到不再变化） */
    fun stripVersionDecoration(value: String): String {
        var result = normalize(value)
        while (result.isNotEmpty()) {
            val next = stripTrailingVersionMarkerOnce(result)
            if (next == result) return result
            result = next
        }
        return result
    }

    /** 按常见艺人分隔符拆分（/ 、 ， , & ; ；），去除空 token */
    fun splitArtistTokens(value: String): List<String> =
        value.split('/', '、', ',', '，', '&', ';', '；')
            .mapNotNull { it.trim().takeIf { token -> token.isNotEmpty() } }

    /** 小写 + 压缩空白 + 去首尾空白 */
    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    /** 反复剥离尾部的**版本装饰**成对括号；括号内容不是版本关键词时保留原串 */
    private fun baseTitle(value: String): String {
        var result = normalize(value)
        while (true) {
            val next = stripTrailingVersionBracketOnce(result) ?: return result
            if (next.isEmpty()) return result
            result = next
        }
    }

    /**
     * 末尾是成对括号且括号内容命中 [VERSION_MARKERS] 时，返回去掉该括号及其内容的结果；
     * 否则返回 null（调用方保留原串）。
     *
     * 只剥离**版本装饰**是安全边界：括号里写版本标记时剥掉才能认出同一首的其它版本；
     * 而括号里是副标题、联名或「另一首歌」的名称时，剥掉会把不同的歌判成同一首，
     * 故一律保留原串。副标题形态的同曲上传仍可由「歌名互为包含」的严格判定接受。
     */
    private fun stripTrailingVersionBracketOnce(value: String): String? {
        val opener = BRACKET_PAIRS[value.lastOrNull() ?: return null] ?: return null
        val index = value.lastIndexOf(opener)
        if (index <= 0) return null
        val inner = value.substring(index + 1, value.length - 1).trim()
        if (!containsVersionMarker(inner)) return null
        return value.substring(0, index).trim()
    }

    /**
     * 文本是否含版本关键词：中文关键词按子串判定（无词边界概念），
     * ASCII 关键词按整词判定（防止 `edit` 命中 `edition` 内部）。
     */
    private fun containsVersionMarker(value: String): Boolean {
        val normalized = normalize(value)
        if (normalized.isEmpty()) return false
        val asciiTokens = ASCII_WORD_SPLIT.split(normalized).filter { it.isNotEmpty() }.toSet()
        return VERSION_MARKERS.any { marker ->
            if (marker.all { it.code < 128 }) {
                asciiTokens.contains(marker)
            } else {
                normalized.contains(marker)
            }
        }
    }

    /** 末尾是版本装饰关键词时剥离一处；否则原样返回 */
    private fun stripTrailingVersionMarkerOnce(value: String): String {
        val separatorIndex = value.lastIndexOfAny(
            charArrayOf(' ', '-', '_', '/', '|', '~', '·', ':', '：')
        )
        if (separatorIndex <= 0) return value
        val tail = value.substring(separatorIndex + 1).trim()
        if (VERSION_MARKERS.any { marker -> tail == marker || tail.startsWith("$marker ") }) {
            return value.substring(0, separatorIndex).trim()
        }
        return value
    }
}
