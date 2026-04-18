package com.lidesheng.hyperlyric.online.utils

import com.lidesheng.hyperlyric.online.model.LyricsData
import com.lidesheng.hyperlyric.online.model.LyricsLine
import com.lidesheng.hyperlyric.online.model.LyricsResult
import com.lidesheng.hyperlyric.online.model.LyricsWord
import java.util.regex.Pattern

private object LrcParserUtils {
    val LRC_TIME_TAG_PATTERN: Pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

    fun parseLrc(lrc: String): List<LyricsLine> {
        val timedLines = mutableListOf<Pair<Long, String>>()

        lrc.lines().forEach { line ->
            val trimmedLine = line.trim()
            val timeTagMatcher = LRC_TIME_TAG_PATTERN.matcher(trimmedLine)

            val timestamps = mutableListOf<Long>()
            var contentStart = 0

            while (timeTagMatcher.find()) {
                val min = timeTagMatcher.group(1)?.toLongOrNull() ?: 0L
                val sec = timeTagMatcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = (timeTagMatcher.group(3) ?: "0").padEnd(3, '0')
                val ms = msPart.toLongOrNull() ?: 0L
                timestamps.add(min * 60000 + sec * 1000 + ms)
                contentStart = timeTagMatcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val content = trimmedLine.substring(contentStart).trim()
                timestamps.forEach { time ->
                    timedLines.add(time to content)
                }
            }
        }

        timedLines.sortBy { it.first }

        val lines = mutableListOf<LyricsLine>()
        for (i in timedLines.indices) {
            val (startTime, text) = timedLines[i]

            if (text.isEmpty()) continue

            val endTime = if (i + 1 < timedLines.size) {
                timedLines[i + 1].first
            } else {
                startTime + 3000
            }

            val word = LyricsWord(start = startTime, end = endTime, text = text)
            lines.add(LyricsLine(start = startTime, end = endTime, words = listOf(word)))
        }
        return lines
    }

    fun lyricsMerge(
        originalLines: List<LyricsLine>,
        transLines: List<LyricsLine>?
    ): List<LyricsLine>? {
        if (transLines.isNullOrEmpty()) return null

        val sortedTransLines = transLines.sortedBy { it.start }
        val alignedList = ArrayList<LyricsLine>()

        var transIdx = 0
        val transCount = sortedTransLines.size

        for (i in originalLines.indices) {
            val orig = originalLines[i]

            val winStart = orig.start
            val winEnd = if (i < originalLines.size - 1) {
                originalLines[i + 1].start
            } else {
                Long.MAX_VALUE
            }

            var matchedText = ""

            while (transIdx < transCount) {
                val trans = sortedTransLines[transIdx]

                if (trans.start < winStart - 500) {
                    transIdx++
                    continue
                }

                if (trans.start >= winEnd) {
                    break
                }

                matchedText = trans.words.joinToString("") { it.text }
                transIdx++
                break
            }

            if (matchedText.isNotEmpty()) {
                val newWords = listOf(LyricsWord(orig.start, orig.end, matchedText))
                alignedList.add(LyricsLine(orig.start, orig.end, newWords))
            } else {
                val emptyWords = listOf(LyricsWord(orig.start, orig.end, ""))
                alignedList.add(LyricsLine(orig.start, orig.end, emptyWords))
            }
        }

        return alignedList
    }
}

object QrcParser {
    private val QRC_LINE_PATTERN: Pattern = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$")
    private val QRC_WORD_PATTERN: Pattern = Pattern.compile("\\((\\d+),(\\d+)\\)([^()]*)")
    private val QRC_XML_PATTERN = Pattern.compile("<Lyric_1 LyricType=\"1\" LyricContent=\"(.*?)\"/>", Pattern.DOTALL)
    private val TAG_PATTERN = Pattern.compile("^\\[(\\w+):([^]]*)]$")


    fun parse(qrcData: LyricsData): LyricsResult {
        val tags = mutableMapOf<String, String>()

        val originalLines = (when (qrcData.type) {
            "qrc" -> {
                if (!qrcData.original.isNullOrEmpty()) parseQrc(qrcData.original) else emptyList()
            }
            "lrc" -> {
                if (!qrcData.original.isNullOrEmpty()) LrcParserUtils.parseLrc(qrcData.original) else emptyList()
            }
            else -> {
                emptyList()
            }
        }).sortedBy { it.start }

        val translatedLinesRaw = qrcData.translated?.takeIf { it.isNotEmpty() }?.let { LrcParserUtils.parseLrc(it) }
        val romanizationLinesRaw = qrcData.romanization?.takeIf { it.isNotEmpty() }?.let { LrcParserUtils.parseLrc(it) }

        val translatedLinesAligned = LrcParserUtils.lyricsMerge(originalLines, translatedLinesRaw)
        val romanizationLinesAligned = LrcParserUtils.lyricsMerge(originalLines, romanizationLinesRaw)

        return LyricsResult(tags, originalLines, translatedLinesAligned, romanizationLinesAligned)
    }

    /**
     * 解析 QRC 格式
     */
    private fun parseQrc(qrc: String): List<LyricsLine> {
        val origList = ArrayList<LyricsLine>()
        var content = qrc
        val xmlMatcher = QRC_XML_PATTERN.matcher(qrc)
        if (xmlMatcher.find()) {
            content = xmlMatcher.group(1) ?: ""
        }

        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            val tagMatcher = TAG_PATTERN.matcher(line)
            if (tagMatcher.matches()) {
                continue
            }

            val lineMatcher = QRC_LINE_PATTERN.matcher(line)
            if (lineMatcher.matches()) {
                val lineStart = lineMatcher.group(1)!!.toLong()
                val lineDuration = lineMatcher.group(2)!!.toLong()
                val lineEnd = lineStart + lineDuration
                val lineContent = lineMatcher.group(3) ?: ""

                val words = ArrayList<LyricsWord>()
                val wordMatcher = QRC_WORD_PATTERN.matcher(lineContent)

                var lastWordEnd = lineStart

                var currentPos = 0
                while (wordMatcher.find(currentPos)) {
                    val wordText = wordMatcher.group(3) ?: ""
                    val wordStart = wordMatcher.group(1)!!.toLong()
                    val wordDuration = wordMatcher.group(2)!!.toLong()
                    val wordEnd = wordStart + wordDuration

                    val preWordText = lineContent.substring(currentPos, wordMatcher.start())
                    if (preWordText.isNotBlank()) {
                        words.add(LyricsWord(lastWordEnd, wordStart, preWordText.trim()))
                    }

                    words.add(LyricsWord(start = wordStart, end = wordEnd, text = wordText))
                    lastWordEnd = wordEnd
                    currentPos = wordMatcher.end()
                }

                val remainingText = lineContent.substring(currentPos)
                if (remainingText.isNotBlank()) {
                    words.add(LyricsWord(lastWordEnd, lineEnd, remainingText.trim()))
                }

                if (words.isEmpty() && lineContent.isNotBlank()) {
                    words.add(LyricsWord(lineStart, lineEnd, lineContent.trim()))
                } else if (words.isEmpty() && lineContent.isBlank()) {
                    words.add(LyricsWord(lineStart, lineEnd, ""))
                }

                origList.add(LyricsLine(lineStart, lineEnd, words))
            }
        }
        return origList
    }
}

object YrcParser {
    private val YRC_LINE_PATTERN: Pattern = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$")
    private val YRC_WORD_PATTERN: Pattern = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^()]*)")

    fun parse(yrc: String?, lrc: String?, tlyric: String?, romalrc: String?): LyricsResult? {
        if (yrc.isNullOrEmpty() && lrc.isNullOrEmpty()) return null

        val originalLines = (if (!yrc.isNullOrEmpty()) {
            parseYrc(yrc)
        } else {
            LrcParserUtils.parseLrc(lrc!!)
        }).sortedBy { it.start }

        val translatedLinesRaw = tlyric?.takeIf { it.isNotEmpty() }?.let { LrcParserUtils.parseLrc(it) }
        val romanizationLinesRaw = romalrc?.takeIf { it.isNotEmpty() }?.let { LrcParserUtils.parseLrc(it) }

        val translatedLinesAligned = LrcParserUtils.lyricsMerge(originalLines, translatedLinesRaw)
        val romanizationLinesAligned = LrcParserUtils.lyricsMerge(originalLines, romanizationLinesRaw)

        return LyricsResult(
            tags = emptyMap(),
            original = originalLines,
            translated = translatedLinesAligned,
            romanization = romanizationLinesAligned
        )
    }


    private fun parseYrc(yrc: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        yrc.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            val lineMatcher = YRC_LINE_PATTERN.matcher(trimmedLine)
            if (lineMatcher.find()) {
                val lineStart = lineMatcher.group(1)?.toLongOrNull() ?: 0L
                val lineDuration = lineMatcher.group(2)?.toLongOrNull() ?: 0L
                val content = lineMatcher.group(3) ?: ""
                val lineEnd = lineStart + lineDuration

                val words = mutableListOf<LyricsWord>()
                val wordMatcher = YRC_WORD_PATTERN.matcher(content)
                while (wordMatcher.find()) {
                    val wordStart = wordMatcher.group(1)?.toLongOrNull() ?: 0L
                    val wordDuration = wordMatcher.group(2)?.toLongOrNull() ?: 0L
                    val wordText = wordMatcher.group(3) ?: ""
                    words.add(LyricsWord(start = wordStart, end = wordStart + wordDuration, text = wordText))
                }

                if (words.isEmpty() && content.isNotEmpty()) {
                    words.add(LyricsWord(start = lineStart, end = lineEnd, text = content))
                }

                if (words.isNotEmpty()) {
                    words.sortBy { it.start }
                    lines.add(LyricsLine(start = lineStart, end = lineEnd, words = words))
                }
            }
        }
        return lines
    }
}
