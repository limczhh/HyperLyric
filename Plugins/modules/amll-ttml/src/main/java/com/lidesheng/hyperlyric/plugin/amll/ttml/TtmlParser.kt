package com.lidesheng.hyperlyric.plugin.amll.ttml

import android.util.Xml
import com.lidesheng.hyperlyric.plugin.api.PluginLyricLine
import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginMetadata
import com.lidesheng.hyperlyric.plugin.api.PluginWord
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale

/**
 * AMLL TTML 解析器（自 main 分支 TtmlParser.kt 移植）
 *
 * 解析 W3C TTML + Apple Music SMPTE 风格 span 时间轴，输出 [PluginLyricLine] 列表。
 * 支持：
 * - `<p>` 行级 begin/end 与 `<span>` 逐字 begin/end
 * - span 之间的空白文本节点保留为单词分隔符（英文歌词），含换行的格式化空白忽略
 * - `ttm:agent` 对唱标记 → metadata["amll:agent"]（渲染层不区分，仅元数据保留）
 * - `ttm:role="x-bg"` 背景人声 → secondary/secondaryWords（优先级高于翻译/罗马音）；
 *   内部嵌套的无 role 逐字 span 递归解析为 secondaryWords（供副行逐字表演），
 *   嵌套的翻译/罗马音丢弃；内部无逐字 span 但外层自带时间轴时回退为整段词；
 *   词文本去除首尾括号（显示层不需要和声括号标注）；
 *   仅含 x-bg 的行（间奏和声）提升为主行文本，避免被宿主校验整行丢弃
 * - `ttm:role="x-translation"` 翻译 → 按 xml:lang 从候选中挑选一条写入 translation
 *   （优先级：完全匹配 > 简繁脚本等价 > 主语言前缀 > 无语言标记 > 第一个；行内无 x-bg 时）
 * - head 的 iTunesMetadata/translations 块级翻译（无逐字时间轴）：
 *   `<translation xml:lang>` 内 `<text for="key">` 经 `itunes:key` 关联正文行，
 *   行内无 x-translation 时回填为该行翻译候选（同样受 x-bg 优先级约束），
 *   词表留空由宿主 RelativeWordBuilder 生成整行词
 * - `ttm:role="x-roman"` 罗马音 → roma（行内无 x-bg 时）
 * - 同 begin 时间多行压缩：第一行主行、第二行副行（secondary）、第三行及以后丢弃
 * - `itunes:song-part` 段落标记忽略
 *
 * 与 main 分支的差异（插件契约适配）：
 * - 输出为 PluginLyricLine/PluginWord DTO（不可变），duration 显式计算；
 * - 宿主 REPLACE 校验要求行/词时间轴严格合法，解析后执行防御性规整
 *   （行 end 扩展覆盖 bg 词、词 clamp、排序、规模限制），见 [regularizeLines]；
 * - 间奏倒计时行（无 text/words）被宿主校验拒绝，v1 不插入（spec §7.2，
 *   宿主放行 CountdownLine metadata 行后可恢复）。
 */
internal class TtmlParser(private val logger: PluginLogger) {

    companion object {
        /** TTML 命名空间下的行/文本元素本地名 */
        private const val TAG_PARAGRAPH = "p"
        private const val TAG_SPAN = "span"

        /** head iTunesMetadata 内的块级翻译元素本地名（transliterations 下的 transliteration 不含此名） */
        private const val TAG_ITUNES_TRANSLATION = "translation"
        private const val TAG_ITUNES_TEXT = "text"

        private const val ROLE_BG = "x-bg"
        private const val ROLE_TRANSLATION = "x-translation"
        private const val ROLE_ROMAN = "x-roman"

        const val METADATA_KEY_AGENT = "amll:agent"

        /** 间奏提示的最小行间隔（main 分支常量，v1 未使用，见类注释） */
        @Suppress("unused")
        private const val INTERLUDE_MIN_GAP_MS = 4_000L

        /** 间奏倒计时的显示延迟（main 分支常量，v1 未使用，见类注释） */
        @Suppress("unused")
        private const val INTERLUDE_COUNTDOWN_DELAY_MS = 1_000L

        /** 宿主校验的规模限制（对齐 PluginSongMapper） */
        private const val MAX_LINES = 20_000
        private const val MAX_WORDS_PER_LINE = 2_000
        private const val MAX_TOTAL_WORDS = 100_000

        /** 中文简体区域（zh-Hans 脚本等价） */
        private val SIMPLIFIED_CHINESE_REGIONS = setOf("cn", "sg")

        /** 中文繁体区域（zh-Hant 脚本等价） */
        private val TRADITIONAL_CHINESE_REGIONS = setOf("tw", "hk", "mo")

        /**
         * 解析 TTML 时间表达式为毫秒。
         *
         * 支持格式：
         * - `HH:MM:SS.mmm`（如 `00:01:23.456`）
         * - `MM:SS.mmm`（如 `01:23.456`）
         * - `SS.sss s`（如 `83.456s`、`83456ms`，无单位默认秒）
         *
         * @return 毫秒值；无法解析返回 -1
         */
        fun parseTtmlTime(expr: String): Long {
            val normalized = expr.trim().lowercase(Locale.US)
            if (normalized.isEmpty()) return -1L

            if (!normalized.contains(':')) {
                val (numericPart, unit) = when {
                    normalized.endsWith("ms") -> normalized.removeSuffix("ms") to "ms"
                    normalized.endsWith("s") -> normalized.removeSuffix("s") to "s"
                    normalized.endsWith("h") -> normalized.removeSuffix("h") to "h"
                    normalized.endsWith("m") -> normalized.removeSuffix("m") to "m"
                    else -> normalized to "s"
                }
                val value = numericPart.toDoubleOrNull() ?: return -1L
                val multiplier = when (unit) {
                    "ms" -> 0.001
                    "m" -> 60.0
                    "h" -> 3600.0
                    else -> 1.0
                }
                return (value * multiplier * 1000).toLong()
            }

            val parts = normalized.split(':')
            if (parts.size !in 2..3) return -1L
            var totalSeconds = 0.0
            for (part in parts) {
                val value = part.toDoubleOrNull() ?: return -1L
                totalSeconds = totalSeconds * 60 + value
            }
            return (totalSeconds * 1000).toLong()
        }
    }

    /** 候选翻译：同一行的多个翻译 span 按 xml:lang 归集（相邻同语言的拼接） */
    private class TranslationCandidate(val lang: String?) {
        val text = StringBuilder()
        val words = mutableListOf<PluginWord>()
    }

    /** 解析过程中的 `<p>` 中间载体 */
    private class ParsedParagraph {
        var begin = -1L
        var end = -1L
        var agent: String? = null

        /** 正文行的 itunes:key（head iTunesMetadata 翻译按此键关联） */
        var itunesKey: String? = null

        val mainWords = mutableListOf<PluginWord>()
        val mainExtraText = StringBuilder()
        val bgWords = mutableListOf<PluginWord>()
        val bgExtraText = StringBuilder()
        val translations = mutableListOf<TranslationCandidate>()
        var romaText: String? = null

        /**
         * span 之间的空白分隔符（AMLL 英文歌词的单词间空格是独立的空白文本节点），
         * 由下一个主歌词内容消费；含换行的空白视为 XML 格式化噪声，不会置位
         */
        var pendingSpace = false

        /** bg 内部的空白分隔符（如 "(Fast lane)" 的词间空格），机制同主行 pendingSpace */
        var bgPendingSpace = false

        /** 是否已有主歌词内容（分隔符仅在已有内容之后生效，行首空白忽略） */
        fun hasMainContent(): Boolean = mainWords.isNotEmpty() || mainExtraText.isNotEmpty()

        /** 是否已有背景人声内容（分隔符仅在已有内容之后生效） */
        fun hasBgContent(): Boolean = bgWords.isNotEmpty() || bgExtraText.isNotEmpty()

        /** 消费待拼接空格：已有内容且新文本非空白开头时补一个空格 */
        fun consumePendingSpace(text: String): String {
            if (!pendingSpace) return text
            pendingSpace = false
            if (!hasMainContent() || text.isEmpty() || text[0].isWhitespace()) return text
            return " $text"
        }

        /** 消费 bg 待拼接空格（机制同主行） */
        fun consumeBgPendingSpace(text: String): String {
            if (!bgPendingSpace) return text
            bgPendingSpace = false
            if (!hasBgContent() || text.isEmpty() || text[0].isWhitespace()) return text
            return " $text"
        }

        /** 收集无时间轴的主歌词文本 */
        fun appendMainExtra(text: String) {
            mainExtraText.append(consumePendingSpace(text))
        }

        /** 收集无时间轴的背景人声文本 */
        fun appendBgExtra(text: String) {
            bgExtraText.append(consumeBgPendingSpace(text))
        }

        /** 收集一条候选翻译（相邻同语言的拼接为一条） */
        fun addTranslation(lang: String?, text: String, word: PluginWord?) {
            if (text.isEmpty()) return
            val target = translations.lastOrNull()?.takeIf { it.lang == lang }
                ?: TranslationCandidate(lang).also { translations.add(it) }
            target.text.append(text)
            if (word != null) target.words.add(word)
        }
    }

    /**
     * 解析 TTML 字符串为歌词行列表。
     *
     * @param ttml TTML 原文
     * @param preferredLang 首选翻译语言（BCP 47 标签，如 zh-CN），用于从多语言候选中挑选翻译；
     * 默认取系统语言
     * @return 解析成功返回按时间顺序、通过宿主校验规整的行列表；
     * 解析失败/无段落/无有效行/规模超限返回 null（调用方走未命中回落）
     */
    fun parse(
        ttml: String,
        preferredLang: String? = Locale.getDefault().toLanguageTag()
    ): List<PluginLyricLine>? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(ttml))
            val doc = parseDocument(parser)
            if (doc.paragraphs.isEmpty()) {
                logger.debug("TTML 解析为空: 无歌词行")
                return null
            }
            val lines = buildLines(doc.paragraphs, preferredLang, doc.itunesTranslations)
            if (lines.isEmpty()) {
                logger.debug("TTML 解析为空: 无有效行")
                return null
            }
            logStats(lines)
            lines
        } catch (e: Exception) {
            logger.debug("TTML 解析异常: type=${e.javaClass.simpleName}")
            null
        }
    }

    // ==================== 文档遍历 ====================

    /** 文档级解析结果：正文行 + head iTunesMetadata 块级翻译（itunes:key → 各语言候选） */
    private class ParsedDocument(
        val paragraphs: MutableList<ParsedParagraph> = mutableListOf(),
        val itunesTranslations: MutableMap<String, MutableList<TranslationCandidate>> = mutableMapOf()
    )

    private fun parseDocument(parser: XmlPullParser): ParsedDocument {
        val doc = ParsedDocument()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_PARAGRAPH -> doc.paragraphs.add(parseParagraph(parser))
                    // head 内 iTunesMetadata/translations 块级翻译（body 中无此标签名）
                    TAG_ITUNES_TRANSLATION ->
                        parseItunesTranslationBlock(parser, doc.itunesTranslations)
                }
            }
            eventType = parser.next()
        }
        return doc
    }

    /**
     * 解析单个 `<translation xml:lang="...">` 块（调用时位于 translation 的
     * START_TAG，返回时位于其 END_TAG）：内部每个 `<text for="key">` 的文本
     * 按 for 键登记为对应正文行的翻译候选（无逐字时间轴，词表留空）。
     */
    private fun parseItunesTranslationBlock(
        parser: XmlPullParser,
        target: MutableMap<String, MutableList<TranslationCandidate>>
    ) {
        val lang = attrValue(parser, "lang")
        val blockDepth = parser.depth
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == TAG_ITUNES_TEXT) {
                        val forKey = attrValue(parser, "for")
                        val content = readTextUntilEnd(parser, TAG_ITUNES_TEXT).trim()
                        if (!forKey.isNullOrEmpty() && content.isNotEmpty()) {
                            target.getOrPut(forKey) { mutableListOf() }
                                .add(TranslationCandidate(lang).apply { text.append(content) })
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == TAG_ITUNES_TRANSLATION && parser.depth == blockDepth) {
                        return
                    }
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * 解析单个 `<p>` 元素（调用时 parser 位于 p 的 START_TAG，返回时位于 p 的 END_TAG）。
     */
    private fun parseParagraph(parser: XmlPullParser): ParsedParagraph {
        val paragraph = ParsedParagraph()
        paragraph.begin = parseTimeAttr(parser, "begin")
        paragraph.end = parseTimeAttr(parser, "end")
        paragraph.agent = attrValue(parser, "agent")
        paragraph.itunesKey = attrValue(parser, "key")

        val paragraphDepth = parser.depth
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == TAG_SPAN) {
                        parseSpanInto(parser, paragraph)
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text.orEmpty()
                    if (text.isNotBlank()) {
                        // 非纯空白文本：无 span 的 `<p>` 直接文本，消费待拼接空格后收集
                        paragraph.appendMainExtra(text.trim())
                    } else if (text.isNotEmpty() && !text.contains('\n') && !text.contains('\r')) {
                        // span 之间的同行空白（AMLL 英文歌词的单词分隔空格）：记为待拼接分隔符；
                        // 含换行的空白视为 XML 格式化噪声忽略（避免 CJK 逐字歌词被错误加空格）
                        paragraph.pendingSpace = true
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == TAG_PARAGRAPH && parser.depth == paragraphDepth) {
                        return paragraph
                    }
                }
            }
            eventType = parser.next()
        }
        return paragraph
    }

    /**
     * 解析单个 `<span>` 元素并按 ttm:role 分流（返回时位于 span 的 END_TAG）。
     */
    private fun parseSpanInto(parser: XmlPullParser, paragraph: ParsedParagraph) {
        val role = attrValue(parser, "role")
        val lang = attrValue(parser, "lang")
        val begin = parseTimeAttr(parser, "begin")
        val end = parseTimeAttr(parser, "end")

        when (role) {
            ROLE_BG -> parseBgSpan(parser, paragraph, begin, end)

            ROLE_TRANSLATION -> {
                val text = readTextUntilEnd(parser, TAG_SPAN)
                if (text.isNotEmpty()) {
                    paragraph.addTranslation(lang, text, buildWordOrNull(begin, end, text))
                }
            }

            ROLE_ROMAN -> {
                val text = readTextUntilEnd(parser, TAG_SPAN)
                if (paragraph.romaText == null) {
                    paragraph.romaText = text
                }
            }

            else -> {
                val text = readTextUntilEnd(parser, TAG_SPAN)
                if (begin >= 0) {
                    paragraph.mainWords.add(
                        buildWord(begin, end, paragraph.consumePendingSpace(text))
                    )
                } else if (text.isNotBlank()) {
                    paragraph.appendMainExtra(text.trim())
                } else if (text.isNotEmpty()) {
                    // 无时间轴的纯空白 span：同样视为单词分隔符
                    paragraph.pendingSpace = true
                }
            }
        }
    }

    /**
     * 解析 x-bg 背景人声 span（调用时位于 x-bg 的 START_TAG，返回时位于其 END_TAG）。
     *
     * 递归遍历内部节点：
     * - 无 role 且带 begin/end 的子 span → bgWords（真逐字时间轴，供副行逐字表演）
     * - 无 role 的无时间子 span / 直接文本 → bgExtraText（无逐字时间轴的兜底文本）
     * - 带其他 role 的子 span（嵌套翻译/罗马音等）→ 丢弃
     * - 同行空白文本节点 → 待拼接空格（如 "(Fast" 与 "lane)" 之间的分隔空格）
     *
     * 若内部没有任何逐字 span、仅有无时间文本，且外层 x-bg 自带 begin/end，
     * 则回退为一个覆盖外层时间轴的整段词（兼容无嵌套结构的扁平 TTML）
     */
    private fun parseBgSpan(
        parser: XmlPullParser,
        paragraph: ParsedParagraph,
        outerBegin: Long,
        outerEnd: Long
    ) {
        val startWordCount = paragraph.bgWords.size
        val startExtraLength = paragraph.bgExtraText.length
        val targetDepth = parser.depth
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == TAG_SPAN) {
                        parseBgChildSpan(parser, paragraph)
                    } else {
                        skipCurrentElement(parser)
                    }
                }

                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val text = parser.text.orEmpty()
                    if (text.isNotBlank()) {
                        paragraph.appendBgExtra(text.trim())
                    } else if (text.isNotEmpty() && !text.contains('\n') && !text.contains('\r')) {
                        paragraph.bgPendingSpace = true
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == TAG_SPAN && parser.depth == targetDepth) {
                        if (paragraph.bgWords.size == startWordCount &&
                            paragraph.bgExtraText.length > startExtraLength && outerBegin >= 0
                        ) {
                            // 扁平兜底：新增的无时间文本用外层整体时间轴并成一个词（同样去括号）
                            val text = stripBgParentheses(
                                paragraph.bgExtraText.substring(startExtraLength).trim()
                            )
                            paragraph.bgExtraText.setLength(startExtraLength)
                            if (text.isNotEmpty()) {
                                paragraph.bgWords.add(buildWord(outerBegin, outerEnd, text))
                            }
                        }
                        return
                    }
                }
            }
            eventType = parser.next()
        }
    }

    /**
     * 解析 x-bg 内部的单个子 span（调用时位于其 START_TAG，返回时位于其 END_TAG）。
     * 无 role 的子 span 按主行同款机制收集；带 role 的子 span（嵌套翻译/罗马音）丢弃。
     */
    private fun parseBgChildSpan(parser: XmlPullParser, paragraph: ParsedParagraph) {
        val role = attrValue(parser, "role")
        if (role != null) {
            // 嵌套翻译/罗马音等：按既定决策丢弃（连带其全部内容）
            readTextUntilEnd(parser, TAG_SPAN)
            return
        }
        val begin = parseTimeAttr(parser, "begin")
        val end = parseTimeAttr(parser, "end")
        val text = readTextUntilEnd(parser, TAG_SPAN)
        if (begin >= 0) {
            addBgWord(paragraph, begin, end, paragraph.consumeBgPendingSpace(text))
        } else if (text.isNotBlank()) {
            paragraph.appendBgExtra(text.trim())
        } else if (text.isNotEmpty()) {
            paragraph.bgPendingSpace = true
        }
    }

    /** 收集一个背景人声词：去除首尾括号（如 "(Fast"/"lane)" → "Fast"/"lane"），去后为空则丢弃 */
    private fun addBgWord(paragraph: ParsedParagraph, begin: Long, end: Long, text: String) {
        val stripped = stripBgParentheses(text)
        if (stripped.isNotEmpty()) {
            paragraph.bgWords.add(buildWord(begin, end, stripped))
        }
    }

    /** 去除背景人声文本的首尾括号（Apple Music 风格 bg 用括号标注和声，显示层不需要） */
    private fun stripBgParentheses(text: String): String = text.trimStart('(').trimEnd(')')

    /** 跳过当前元素的全部内容（调用时位于 START_TAG，返回时位于其匹配的 END_TAG） */
    private fun skipCurrentElement(parser: XmlPullParser) {
        val depth = parser.depth
        while (true) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> if (parser.depth == depth) return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    /**
     * 读取元素内全部文本，直到当前深度的目标标签 END_TAG。
     */
    private fun readTextUntilEnd(parser: XmlPullParser, targetTag: String): String {
        val sb = StringBuilder()
        val targetDepth = parser.depth
        while (true) {
            when (val eventType = parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> sb.append(parser.text.orEmpty())

                XmlPullParser.END_TAG -> {
                    if (parser.name == targetTag && parser.depth == targetDepth) {
                        return sb.toString()
                    }
                }

                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    // ==================== 行构造与合并 ====================

    private fun buildLines(
        paragraphs: List<ParsedParagraph>,
        preferredLang: String?,
        itunesTranslations: Map<String, List<TranslationCandidate>>
    ): List<PluginLyricLine> {
        val merged = mutableListOf<PluginLyricLine>()
        for (paragraph in paragraphs) {
            // 行内无 x-translation 时回填 head iTunesMetadata 块级翻译
            // （itunes:key 关联；候选无词表，宿主 RelativeWordBuilder 生成整行词）
            if (paragraph.translations.isEmpty()) {
                paragraph.itunesKey?.let { key ->
                    paragraph.translations.addAll(itunesTranslations[key].orEmpty())
                }
            }
            val line = buildLine(paragraph, preferredLang) ?: continue
            val lastIndex = merged.lastIndex
            val last = merged.getOrNull(lastIndex)
            if (last != null && last.begin == line.begin) {
                // 同 begin 多行压缩：第二行作为副行（先出现者的 x-bg 优先），第三行及以后丢弃
                val secondaryCandidate = line.text?.takeIf { it.isNotBlank() }
                    ?: line.secondary?.takeIf { it.isNotBlank() }
                if (last.secondary.isNullOrBlank() && secondaryCandidate != null) {
                    merged[lastIndex] = last.copy(
                        secondary = secondaryCandidate,
                        secondaryWords = line.words ?: line.secondaryWords
                    )
                }
                continue
            }
            merged.add(line)
        }
        return regularizeLines(merged)
    }

    private fun buildLine(paragraph: ParsedParagraph, preferredLang: String?): PluginLyricLine? {
        val bgText = buildString {
            paragraph.bgWords.forEach { append(it.text.orEmpty()) }
            append(paragraph.bgExtraText.toString().trim())
        }
        val hasBg = bgText.isNotBlank()
        val mainText = buildString {
            paragraph.mainWords.forEach { append(it.text.orEmpty()) }
            append(paragraph.mainExtraText.toString().trim())
        }
        if (mainText.isBlank() && !hasBg) return null

        val begin = paragraph.begin.coerceAtLeast(0L)
        val end = if (paragraph.end >= paragraph.begin && paragraph.end >= 0) paragraph.end else begin
        val metadata = paragraph.agent?.let { PluginMetadata(mapOf(METADATA_KEY_AGENT to it)) }

        if (mainText.isBlank()) {
            // 纯背景人声行（间奏和声等，主文本为空、仅有 x-bg）：背景人声是该行唯一内容，
            // 提升为主行文本与逐字时间轴；text 为 null 的行会被宿主校验的
            // 有效性规则（要求 text 非空）整行丢弃
            return PluginLyricLine(
                begin = begin,
                end = end,
                duration = (end - begin).coerceAtLeast(0L),
                isAlignedRight = false,
                metadata = metadata,
                text = bgText.trim(),
                words = paragraph.bgWords.takeIf { it.isNotEmpty() },
                secondary = null,
                secondaryWords = null,
                translation = null,
                translationWords = null,
                roma = null
            )
        }

        // 同一行既有 x-bg 又有翻译/罗马音时，优先填充 secondary（背景人声），跳过翻译与罗马音
        val pickedTranslation =
            if (hasBg) null else pickTranslation(paragraph.translations, preferredLang)

        return PluginLyricLine(
            begin = begin,
            end = end,
            duration = (end - begin).coerceAtLeast(0L),
            isAlignedRight = false,
            metadata = metadata,
            text = mainText.takeIf { it.isNotBlank() },
            words = paragraph.mainWords.takeIf { it.isNotEmpty() },
            secondary = bgText.trim().takeIf { it.isNotBlank() },
            secondaryWords = paragraph.bgWords.takeIf { it.isNotEmpty() },
            translation = pickedTranslation?.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            translationWords = pickedTranslation?.words?.takeIf { it.isNotEmpty() },
            roma = if (hasBg) null else paragraph.romaText?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    // ==================== 防御性时间轴规整（宿主校验适配） ====================

    /**
     * 对合并后的行列表执行宿主 REPLACE 校验所需的规整：
     * 1. 行 end 扩展：主行词与副行（背景人声）词的最后 end 常晚于行级 end
     *    （AMLL x-bg 和声回声时间轴），扩展行 end 使副行词合法；
     * 2. 词 clamp：词 begin/end 收进行范围、零时长词 end=begin+1、词序列升序
     *    （begin >= previousEnd），无法容纳的词丢弃；
     * 3. duration 显式 = end - begin；
     * 4. 行按 begin 稳定排序（宿主要求非递减）；
     * 5. 规模限制：行 ≤ 20000、单行词 ≤ 2000、总词 ≤ 100000，超限整体判未命中。
     *
     * 规整后仍非法的行（如零时长且无词可扩展）丢弃。
     */
    private fun regularizeLines(lines: List<PluginLyricLine>): List<PluginLyricLine> {
        if (lines.size > MAX_LINES) {
            logger.debug("TTML 行数超限: lines=${lines.size}")
            return emptyList()
        }
        val result = mutableListOf<PluginLyricLine>()
        var totalWords = 0
        for (line in lines) {
            val regularized = regularizeLine(line) ?: continue
            totalWords += regularized.words?.size ?: 0
            totalWords += regularized.secondaryWords?.size ?: 0
            totalWords += regularized.translationWords?.size ?: 0
            if (totalWords > MAX_TOTAL_WORDS) {
                logger.debug("TTML 词数超限: totalWords>$MAX_TOTAL_WORDS")
                return emptyList()
            }
            result.add(regularized)
        }
        return result.sortedBy { it.begin }
    }

    private fun regularizeLine(line: PluginLyricLine): PluginLyricLine? {
        val wordsCount = line.words?.size ?: 0
        val secondaryWordsCount = line.secondaryWords?.size ?: 0
        val translationWordsCount = line.translationWords?.size ?: 0
        if (wordsCount > MAX_WORDS_PER_LINE ||
            secondaryWordsCount > MAX_WORDS_PER_LINE ||
            translationWordsCount > MAX_WORDS_PER_LINE
        ) {
            logger.debug(
                "TTML 单行词数超限，丢弃该行: words=$wordsCount, secondary=$secondaryWordsCount, " +
                        "translation=$translationWordsCount"
            )
            return null
        }

        val begin = line.begin.coerceAtLeast(0L)
        // 行 end 扩展：主行词与副行词的最后 end 可晚于行级 end（AMLL x-bg 回声）
        val end = maxOf(
            line.end,
            line.words?.maxOfOrNull { it.end } ?: Long.MIN_VALUE,
            line.secondaryWords?.maxOfOrNull { it.end } ?: Long.MIN_VALUE
        )
        if (end <= begin) {
            // 零时长行且无词可扩展：无法修复，丢弃（main 分支 normalize 同样过滤）
            return null
        }

        return line.copy(
            begin = begin,
            end = end,
            duration = end - begin,
            words = clampWords(line.words, begin, end),
            secondaryWords = prependSecondaryLeadingFiller(
                clampWords(line.secondaryWords, begin, end),
                begin
            ),
            translationWords = clampWords(line.translationWords, begin, end)
        )
    }

    /**
     * 副行词表前置空白填充词。
     *
     * 宿主渲染的副行 alwaysShow 判定要求副行首词在行首 500ms 内开始，否则
     * 副行整体隐藏（GONE，无按播放位置恢复）。AMLL x-bg 和声常在行中插入
     * （首词延迟数百毫秒至 1.5s），会被该规则误杀为整行不显示。
     * 前置一个覆盖 [行首, 首词开始) 的空白词使首词与行首对齐：空白无字形、
     * 渐变不可见，bg 词保持原时间轴逐字表演，副行文本随行首出现。
     * 首词已与行首对齐（无延迟）时不插入。
     */
    private fun prependSecondaryLeadingFiller(
        words: List<PluginWord>?,
        lineBegin: Long
    ): List<PluginWord>? {
        if (words == null) return null
        val firstBegin = words.first().begin
        if (firstBegin <= lineBegin) return words
        return listOf(buildWord(lineBegin, firstBegin, " ")) + words
    }

    /**
     * 词列表 clamp：保证每个词 begin >= line.begin、end <= line.end、end > begin、
     * duration == end - begin、begin >= previousEnd（宿主 hasValidWords 全部约束）。
     * 无法在剩余区间容纳的词丢弃；全部被丢弃时返回 null。
     */
    private fun clampWords(
        words: List<PluginWord>?,
        lineBegin: Long,
        lineEnd: Long
    ): List<PluginWord>? {
        if (words == null) return null
        val result = mutableListOf<PluginWord>()
        var previousEnd = lineBegin
        for (word in words) {
            val begin = maxOf(word.begin, previousEnd, lineBegin)
            if (begin > lineEnd - 1) {
                // 无剩余空间容纳该词（previousEnd 已逼近行尾）：丢弃
                continue
            }
            val end = maxOf(word.end, begin + 1).coerceAtMost(lineEnd)
            result.add(
                word.copy(begin = begin, end = end, duration = end - begin)
            )
            previousEnd = end
        }
        return result.takeIf { it.isNotEmpty() }
    }

    // ==================== 翻译语言挑选 ====================

    /**
     * 从候选翻译中挑选首选语言的一条。
     *
     * 优先级：完全匹配 > 简繁脚本等价（zh-CN↔zh-Hans、zh-TW↔zh-Hant 等）
     * > 主语言前缀匹配 > 无语言标记的候选 > 第一个候选（保底有内容）
     */
    private fun pickTranslation(
        candidates: List<TranslationCandidate>,
        preferredLang: String?
    ): TranslationCandidate? {
        if (candidates.isEmpty()) return null
        if (preferredLang != null) {
            val preferred = preferredLang.lowercase(Locale.US)
            // 1. 完全匹配
            candidates.firstOrNull { it.lang?.lowercase(Locale.US) == preferred }
                ?.let { return it }
            // 2. 简繁脚本等价
            val preferredScript = chineseScriptOf(preferred)
            if (preferredScript != null) {
                candidates.firstOrNull {
                    chineseScriptOf(it.lang?.lowercase(Locale.US)) == preferredScript
                }?.let { return it }
            }
            // 3. 主语言前缀匹配（如 zh-CN 匹配 zh / zh-Hant；en-US 匹配 en-GB）
            val mainLanguage = preferred.substringBefore('-')
            candidates.firstOrNull { candidate ->
                candidate.lang?.lowercase(Locale.US)?.let { lang ->
                    lang == mainLanguage || lang.startsWith("$mainLanguage-")
                } == true
            }?.let { return it }
        }
        // 4. 无语言标记的候选（TTML 未标注 xml:lang 时的默认译文）
        candidates.firstOrNull { it.lang == null }?.let { return it }
        // 5. 回退第一个候选
        return candidates.first()
    }

    /**
     * 中文简繁脚本归类：zh-Hans/zh-CN/zh-SG* → "hans"，zh-Hant/zh-TW/zh-HK/zh-MO* → "hant"；
     * 非中文或无法判断返回 null
     */
    private fun chineseScriptOf(lang: String?): String? {
        if (lang == null) return null
        val parts = lang.lowercase(Locale.US).split('-')
        if (parts.firstOrNull() != "zh") return null
        return when {
            "hans" in parts || parts.any { it in SIMPLIFIED_CHINESE_REGIONS } -> "hans"
            "hant" in parts || parts.any { it in TRADITIONAL_CHINESE_REGIONS } -> "hant"
            else -> null
        }
    }

    // ==================== 属性与时间解析 ====================

    /** 按本地名读取元素属性值（ttm:agent → "agent"，itunes:song-part → "song-part"） */
    private fun attrValue(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == localName) {
                return parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun parseTimeAttr(parser: XmlPullParser, localName: String): Long =
        attrValue(parser, localName)?.let { parseTtmlTime(it) } ?: -1L

    private fun buildWord(begin: Long, end: Long, text: String): PluginWord {
        val safeEnd = if (end >= begin) end else begin
        // 必须显式传递全部参数：宿主 R8 开启 allowoptimization 后会移除
        // Kotlin 默认参数合成的 DefaultConstructorMarker 构造函数，
        // 省略参数会触发 NoSuchMethodError
        return PluginWord(
            begin = begin,
            end = safeEnd,
            duration = safeEnd - begin,
            text = text,
            metadata = null
        )
    }

    /** 带时间轴才生成词，否则返回 null（翻译 span 通常无时间轴） */
    private fun buildWordOrNull(begin: Long, end: Long, text: String): PluginWord? =
        if (begin >= 0) buildWord(begin, end, text) else null

    // ==================== 统计日志 ====================

    private fun logStats(lines: List<PluginLyricLine>) {
        val wordTimingCount = lines.count { !it.words.isNullOrEmpty() }
        val bgCount = lines.count { !it.secondary.isNullOrBlank() }
        val bgWordTimingCount = lines.count { !it.secondaryWords.isNullOrEmpty() }
        val agentCount = lines.count { it.metadata?.values?.containsKey(METADATA_KEY_AGENT) == true }
        val translationCount = lines.count { !it.translation.isNullOrBlank() }
        logger.info(
            "TTML 解析完成: lines=${lines.size}, wordTiming=$wordTimingCount, " +
                    "bg=$bgCount, bgWordTiming=$bgWordTimingCount, " +
                    "agent=$agentCount, translation=$translationCount"
        )
    }
}
