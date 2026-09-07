package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginCacheDetail
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale

/**
 * 从 TTML 提取元数据与能力标记，映射为可读 label 的键值对，供缓存管理详情页展示
 * （映射参考 amll.dev TTMLMetadata 结构，spec 表 1）。
 *
 * - head 区域 amll:meta / ttm:agent 与 body 区域行内 ttm:agent、x-translation 均参与提取：
 *   meta 组内保持出现顺序、多值去重保序合并（同字段多值换行显示）；
 *   对唱歌词 = 正文行实际使用的不同 ttm:agent ≥ 2；翻译 = 行内 x-translation span
 *   或 head iTunesMetadata 块级 translation，两者信息齐备后提前停止扫描；
 * - 未知 amll:meta 键以原始 key 兜底（rawProperties 语义）；
 * - 任何解析异常返回空列表，绝不抛出；
 * - 输出条目与值做防御截断（label ≤32、value ≤120，对齐宿主 sanitize 预算）。
 */
internal object TtmlMetadataExtractor {

    /** 宿主 sanitize 预算（PluginCacheOperationCodec），写入前防御性对齐 */
    private const val MAX_LABEL_LENGTH = 32
    private const val MAX_VALUE_LENGTH = 120

    /** 参与固定输出的 amll:meta 键；其余键按出现顺序以原始 key 兜底输出 */
    private val KNOWN_KEYS = setOf(
        "musicName",
        "artists",
        "album",
        "isrc",
        "ncmMusicId",
        "appleMusicId",
        "spotifyId",
        "qqMusicId",
        "ttmlAuthorGithub",
        "ttmlAuthorGithubLogin",
    )

    /**
     * 提取 TTML 元数据。
     *
     * @param ttml TTML 原文
     * @param useZhLabels 详情 label 语言（true=中文 / false=英文）；默认跟随设备语言，
     * label 在缓存写入时随设备语言固化
     * @return 按固定顺序（歌曲名→歌手→专辑→ISRC→平台 ID→歌词作者→对唱歌词→翻译→其他键）
     * 排列的详情行；无元数据/解析失败返回空列表
     */
    fun extract(
        ttml: String,
        useZhLabels: Boolean = Locale.getDefault().language == "zh",
    ): List<PluginCacheDetail> = runCatching {
        parse(ttml, useZhLabels)
    }.getOrElse { emptyList() }

    private fun parse(ttml: String, useZhLabels: Boolean): List<PluginCacheDetail> {
        if (ttml.isBlank()) return emptyList()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        // 与 TtmlParser 一致：按本地名匹配（amll:meta → meta），必须显式开启命名空间处理
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(ttml))

        // 组内去重保序：LinkedHashSet
        val groups = LinkedHashMap<String, LinkedHashSet<String>>()
        // 正文行实际使用的 ttm:agent（对唱判定依据：不同演唱者 ≥ 2 才有左右分侧效果）
        val bodyAgents = LinkedHashSet<String>()
        // 翻译标记：行内 x-translation span 或 head iTunesMetadata 块级 translation
        var hasTranslation = false

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "meta" -> {
                        val key = attrValue(parser, "key")?.trim()
                        val value = attrValue(parser, "value")?.trim()
                        if (key != null && value != null) {
                            groups.getOrPut(key) { LinkedHashSet() }.add(value)
                        }
                    }

                    // head iTunesMetadata 块级翻译（body 中无此本地名）
                    "translation" -> hasTranslation = true

                    // 正文行携带的演唱者（p 仅出现在 body）
                    "p" -> attrValue(parser, "agent")?.let { bodyAgents.add(it) }

                    // 行内翻译 span（head transliteration 的 span 无 role，不会误判）
                    "span" -> if (!hasTranslation &&
                        attrValue(parser, "role") == "x-translation"
                    ) {
                        hasTranslation = true
                    }
                }

                XmlPullParser.END_DOCUMENT -> break
            }
            // 对唱与翻译信息齐备即可停止扫描（meta 全部位于 head，已收集完成）
            if (hasTranslation && bodyAgents.size >= 2) break
        }

        if (groups.isEmpty() && bodyAgents.size < 2 && !hasTranslation) return emptyList()
        return buildDetails(groups, bodyAgents.size >= 2, hasTranslation, useZhLabels)
    }

    private fun buildDetails(
        groups: Map<String, LinkedHashSet<String>>,
        hasDuet: Boolean,
        hasTranslation: Boolean,
        useZhLabels: Boolean,
    ): List<PluginCacheDetail> {
        val details = mutableListOf<PluginCacheDetail>()

        fun label(zh: String, en: String): String = if (useZhLabels) zh else en

        fun flagValue(present: Boolean): String =
            if (useZhLabels) {
                if (present) "有" else "无"
            } else {
                if (present) "Yes" else "No"
            }

        fun newlineGroup(key: String): String? =
            groups[key]?.joinToString("\n")?.takeIf { it.isNotEmpty() }

        // 组间固定顺序：歌曲名 → 歌手 → 专辑 → ISRC → 平台 ID（ncm→apple→spotify→qq），
        // 同字段多值逐行显示
        newlineGroup("musicName")?.let {
            details.add(PluginCacheDetail(label("歌曲名", "Song Name"), it))
        }
        newlineGroup("artists")?.let {
            details.add(PluginCacheDetail(label("歌手", "Artist"), it))
        }
        newlineGroup("album")?.let {
            details.add(PluginCacheDetail(label("专辑", "Album"), it))
        }
        newlineGroup("isrc")?.let {
            details.add(PluginCacheDetail("ISRC", it))
        }
        newlineGroup("ncmMusicId")?.let {
            details.add(PluginCacheDetail(label("网易云音乐 ID", "NCM ID"), it))
        }
        newlineGroup("appleMusicId")?.let {
            details.add(PluginCacheDetail("Apple Music ID", it))
        }
        newlineGroup("spotifyId")?.let {
            details.add(PluginCacheDetail("Spotify ID", it))
        }
        newlineGroup("qqMusicId")?.let {
            details.add(PluginCacheDetail(label("QQ 音乐 ID", "QQ Music ID"), it))
        }

        // 歌词作者：单作者合并为 "用户名 (ID)"；多作者逐行显示用户名（login 与数字 ID
        // 是两组独立多值键、无法可靠配对，仅单作者时合并）；login 缺失时用数字 ID 兜底
        val authorLogins = groups["ttmlAuthorGithubLogin"].orEmpty().toList()
        val authorGithubIds = groups["ttmlAuthorGithub"].orEmpty().toList()
        val authorValue = when {
            authorLogins.size == 1 && authorGithubIds.size == 1 ->
                "${authorLogins[0]} (${authorGithubIds[0]})"

            authorLogins.isNotEmpty() -> authorLogins.joinToString("\n")
            authorGithubIds.isNotEmpty() -> authorGithubIds.joinToString("\n")
            else -> null
        }
        authorValue?.let {
            details.add(PluginCacheDetail(label("歌词作者", "Author"), it))
        }

        // 对唱歌词 / 翻译：有能力标记即输出（有/无二值），便于快速识别缓存歌词能力
        details.add(
            PluginCacheDetail(label("对唱歌词", "Duet Lyrics"), flagValue(hasDuet))
        )
        details.add(
            PluginCacheDetail(label("翻译", "Translation"), flagValue(hasTranslation))
        )

        // 其他未知 amll:meta 键：按首次出现顺序，label 即原始 key，同键换行合并
        groups.filterKeys { it !in KNOWN_KEYS }.forEach { (key, values) ->
            values.joinToString("\n").takeIf { it.isNotEmpty() }?.let {
                details.add(PluginCacheDetail(key, it))
            }
        }

        // 防御截断（对齐宿主 sanitize 预算）
        return details.map { entry ->
            PluginCacheDetail(entry.label.take(MAX_LABEL_LENGTH), entry.value.take(MAX_VALUE_LENGTH))
        }
    }

    /** 按本地名读取元素属性值（xml:id → "id"，与 TtmlParser 的属性读取约定一致） */
    private fun attrValue(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == localName) {
                return parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
