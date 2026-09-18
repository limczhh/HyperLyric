package com.lidesheng.hyperlyric.root.lyricenhancement.amll

import android.util.Xml
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheDetail
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale

/**
 * 从 TTML 提取缓存详情页需要的可读元数据。
 *
 * 详情只保存到缓存索引，不会把 TTML 正文跨进程传给 App。解析失败时返回空列表，不能
 * 影响歌词本身的增强流程。
 */
internal object TtmlMetadataExtractor {
    private const val MAX_LABEL_LENGTH = 32
    private const val MAX_VALUE_LENGTH = 120

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

    fun extract(
        ttml: String,
        useZhLabels: Boolean = Locale.getDefault().language == "zh",
    ): List<LyricEnhancementCacheDetail> = runCatching {
        parse(ttml, useZhLabels)
    }.getOrElse { emptyList() }

    private fun parse(
        ttml: String,
        useZhLabels: Boolean,
    ): List<LyricEnhancementCacheDetail> {
        if (ttml.isBlank()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(ttml))

        val groups = LinkedHashMap<String, LinkedHashSet<String>>()
        val bodyAgents = LinkedHashSet<String>()
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

                    "translation" -> hasTranslation = true
                    "p" -> attrValue(parser, "agent")?.let(bodyAgents::add)
                    "span" -> if (
                        !hasTranslation && attrValue(parser, "role") == "x-translation"
                    ) {
                        hasTranslation = true
                    }
                }

                XmlPullParser.END_DOCUMENT -> break
            }
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
    ): List<LyricEnhancementCacheDetail> {
        val details = mutableListOf<LyricEnhancementCacheDetail>()

        fun label(zh: String, en: String): String = if (useZhLabels) zh else en
        fun flagValue(present: Boolean): String = if (useZhLabels) {
            if (present) "有" else "无"
        } else {
            if (present) "Yes" else "No"
        }
        fun newlineGroup(key: String): String? =
            groups[key]?.joinToString("\n")?.takeIf(String::isNotEmpty)

        newlineGroup("musicName")?.let {
            details.add(LyricEnhancementCacheDetail(label("歌曲名", "Song Name"), it))
        }
        newlineGroup("artists")?.let {
            details.add(LyricEnhancementCacheDetail(label("歌手", "Artist"), it))
        }
        newlineGroup("album")?.let {
            details.add(LyricEnhancementCacheDetail(label("专辑", "Album"), it))
        }
        newlineGroup("isrc")?.let { details.add(LyricEnhancementCacheDetail("ISRC", it)) }
        newlineGroup("ncmMusicId")?.let {
            details.add(LyricEnhancementCacheDetail(label("网易云音乐 ID", "NCM ID"), it))
        }
        newlineGroup("appleMusicId")?.let {
            details.add(LyricEnhancementCacheDetail("Apple Music ID", it))
        }
        newlineGroup("spotifyId")?.let {
            details.add(LyricEnhancementCacheDetail("Spotify ID", it))
        }
        newlineGroup("qqMusicId")?.let {
            details.add(LyricEnhancementCacheDetail(label("QQ 音乐 ID", "QQ Music ID"), it))
        }

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
            details.add(LyricEnhancementCacheDetail(label("歌词作者", "Author"), it))
        }

        details.add(
            LyricEnhancementCacheDetail(label("对唱歌词", "Duet Lyrics"), flagValue(hasDuet))
        )
        details.add(
            LyricEnhancementCacheDetail(label("翻译", "Translation"), flagValue(hasTranslation))
        )

        groups.filterKeys { it !in KNOWN_KEYS }.forEach { (key, values) ->
            values.joinToString("\n").takeIf(String::isNotEmpty)?.let {
                details.add(LyricEnhancementCacheDetail(key, it))
            }
        }

        return details.map { detail ->
            LyricEnhancementCacheDetail(
                label = detail.label.take(MAX_LABEL_LENGTH),
                value = detail.value.take(MAX_VALUE_LENGTH)
            )
        }
    }

    private fun attrValue(parser: XmlPullParser, localName: String): String? {
        for (index in 0 until parser.attributeCount) {
            if (parser.getAttributeName(index) == localName) {
                return parser.getAttributeValue(index)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
