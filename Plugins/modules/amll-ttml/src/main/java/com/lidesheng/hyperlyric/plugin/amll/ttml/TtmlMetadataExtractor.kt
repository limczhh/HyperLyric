package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginCacheDetail
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale

/**
 * 从 TTML 头部 <head><metadata> 提取 amll:meta 与 ttm:agent，映射为可读 label 的
 * 键值对，供缓存管理详情页展示（映射参考 amll.dev TTMLMetadata 结构，spec 表 1）。
 *
 * - 仅读取 head 区域元数据（遇到 </head> 或 <body> 停止），不解析正文行；
 * - 组内保持出现顺序、多值去重保序合并；
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
     * @return 按固定顺序（歌曲名→歌手→专辑→ISRC→平台 ID→歌词作者→演唱者→其他键）排列的
     * 详情行；无元数据/解析失败返回空列表
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
        val agents = LinkedHashSet<String>()

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

                    "agent" -> attrValue(parser, "id")?.let { agents.add(it) }

                    // head 结束，进入正文：正文行只有 ttm:agent 属性，不再有 meta/agent 元素
                    "body" -> break
                }

                XmlPullParser.END_TAG -> if (parser.name == "head") break
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        if (groups.isEmpty() && agents.isEmpty()) return emptyList()
        return buildDetails(groups, agents, useZhLabels)
    }

    private fun buildDetails(
        groups: Map<String, LinkedHashSet<String>>,
        agents: Set<String>,
        useZhLabels: Boolean,
    ): List<PluginCacheDetail> {
        val details = mutableListOf<PluginCacheDetail>()

        fun label(zh: String, en: String): String = if (useZhLabels) zh else en

        fun slashGroup(key: String): String? =
            groups[key]?.joinToString(" / ")?.takeIf { it.isNotEmpty() }

        fun newlineGroup(key: String): String? =
            groups[key]?.joinToString("\n")?.takeIf { it.isNotEmpty() }

        // 组间固定顺序：歌曲名 → 歌手 → 专辑 → ISRC → 平台 ID（ncm→apple→spotify→qq）
        slashGroup("musicName")?.let {
            details.add(PluginCacheDetail(label("歌曲名", "Song Name"), it))
        }
        slashGroup("artists")?.let {
            details.add(PluginCacheDetail(label("歌手", "Artist"), it))
        }
        slashGroup("album")?.let {
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

        // 歌词作者：ttmlAuthorGithubLogin(用户名) + ttmlAuthorGithub(数字 ID) 合并为
        // "用户名 (ID)"；仅出现一项时按存在项展示，两项皆缺不出该行
        val authorLogin = groups["ttmlAuthorGithubLogin"]?.firstOrNull()
        val authorGithubId = groups["ttmlAuthorGithub"]?.firstOrNull()
        when {
            authorLogin != null && authorGithubId != null ->
                details.add(
                    PluginCacheDetail(label("歌词作者", "Author"), "$authorLogin ($authorGithubId)")
                )

            authorLogin != null ->
                details.add(PluginCacheDetail(label("歌词作者", "Author"), authorLogin))

            authorGithubId != null ->
                details.add(PluginCacheDetail(label("歌词作者", "Author"), authorGithubId))
        }

        // 演唱者：收集全部 agent id
        if (agents.isNotEmpty()) {
            details.add(
                PluginCacheDetail(label("演唱者（Agent）", "Agent"), agents.joinToString(" / "))
            )
        }

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
