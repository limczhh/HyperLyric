package com.lidesheng.hyperlyric.plugin.amll.ttml

/**
 * AMLL 搜索/探测结果的客户端交叉校验（自 main 分支 AmllTtmlClient 移植的纯函数）
 *
 * 服务端排序不保证语义一致（翻唱/Live/串烧可能排在原版之前），而命中结果会被永久缓存，
 * 因此客户端对返回条目做 title/artist 校验，仅接受可交叉验证的条目。
 */
internal object AmllMatch {

    /**
     * 客户端搜索结果校验：请求携带的 title/artist 须与条目交叉匹配（忽略大小写与多余空白），
     * 提供了哪个参数就校验哪个；条目缺失对应字段时该校验不通过。
     * - title：任一 musicNames 与 title 互为包含
     * - artist：按常见分隔符拆分后，任一 token 对互为包含
     */
    fun isPlausibleMatch(item: SongItem, title: String?, artist: String?): Boolean {
        if (title != null) {
            val names = item.musicNames.orEmpty()
            if (names.isEmpty() || names.none { fuzzyContains(it, title) }) return false
        }
        if (artist != null) {
            val requestTokens = splitArtistTokens(artist)
            val candidateTokens = item.artistNames.orEmpty().flatMap { splitArtistTokens(it) }
            if (requestTokens.isEmpty() || candidateTokens.isEmpty() ||
                requestTokens.none { request -> candidateTokens.any { fuzzyContains(it, request) } }
            ) return false
        }
        return true
    }

    /** 归一化（小写 + 压缩空白）后的双向包含匹配：任一方包含另一方即视为匹配 */
    fun fuzzyContains(a: String, b: String): Boolean {
        val na = a.trim().lowercase().replace(Regex("\\s+"), " ")
        val nb = b.trim().lowercase().replace(Regex("\\s+"), " ")
        if (na.isEmpty() || nb.isEmpty()) return false
        return na.contains(nb) || nb.contains(na)
    }

    /** 按常见艺人分隔符拆分（/ 、 ， , & ; ；），去除空 token */
    fun splitArtistTokens(value: String): List<String> =
        value.split('/', '、', ',', '，', '&', ';', '；')
            .mapNotNull { it.trim().takeIf { token -> token.isNotEmpty() } }
}
