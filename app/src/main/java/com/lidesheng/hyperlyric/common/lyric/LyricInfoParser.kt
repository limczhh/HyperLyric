package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import org.json.JSONObject
import java.util.regex.Pattern

object LyricInfoParser {

    private val LRC_TIME_RE = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")

    fun parse(json: String, songName: String, artist: String): Song? {
        return try {
            val obj = JSONObject(json)
            val lyricRaw = obj.optString("lyric", "").trim()
            val format = obj.optString("format", "").trim()
            val translationFormat = obj.optString("translation", "").trim()
            if (lyricRaw.isBlank()) return null

            val hasTranslation = translationFormat.isNotBlank()
            val allLines = lyricRaw.lines().filter { it.isNotBlank() }

            // 提取每行的时间戳和文本
            val parsedLines = allLines.map { line ->
                val timeMs = extractTimeMs(line)
                ParsedLine(timeMs, line)
            }

            // 按时间戳分组，相同时间戳的第二行为翻译
            val resultLines = mutableListOf<RichLyricLine>()
            var i = 0
            while (i < parsedLines.size) {
                val current = parsedLines[i]
                val next = parsedLines.getOrNull(i + 1)

                // 判断下一行是否是翻译（时间戳相同且有翻译格式标识）
                val isNextTranslation = hasTranslation
                        && next != null
                        && next.timeMs == current.timeMs
                        && current.timeMs >= 0

                val mainLine = parseLine(current.raw, format)
                if (mainLine != null) {
                    if (isNextTranslation && next != null) {
                        val transText = extractText(next.raw, translationFormat)
                        if (!transText.isNullOrBlank()) {
                            resultLines.add(mainLine.copy(translation = transText))
                        } else {
                            resultLines.add(mainLine)
                        }
                        i += 2 // 跳过翻译行
                    } else {
                        resultLines.add(mainLine)
                        i += 1
                    }
                } else {
                    i += 1
                }
            }

            // 补全 end/duration，修正最后一个词的时间
            for (idx in resultLines.indices) {
                val cur = resultLines[idx]
                val nextBegin = resultLines.getOrNull(idx + 1)?.begin
                if (cur.end <= cur.begin) {
                    cur.end = nextBegin ?: (cur.begin + 5000)
                    cur.duration = cur.end - cur.begin
                }
                // 修正最后一个词的 end 为行级 end
                cur.words?.lastOrNull()?.let { lastWord ->
                    if (lastWord.end < cur.end) {
                        lastWord.end = cur.end
                        lastWord.duration = lastWord.end - lastWord.begin
                    }
                }
            }

            if (resultLines.isEmpty()) return null
            Song(name = songName, artist = artist, lyrics = resultLines)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析单行为 RichLyricLine（ELRC 或 LRC）。
     */
    private fun parseLine(raw: String, format: String): RichLyricLine? {
        return when (format) {
            "elrc" -> parseElrcLine(raw)
            else -> parseLrcLine(raw)
        }
    }

    /**
     * 提取行文本（去掉时间戳和词级标签）。
     */
    private fun extractText(raw: String, format: String): String? {
        return when (format) {
            "elrc" -> {
                val lineRe = Pattern.compile("\\[\\d{2}:\\d{2}\\.\\d{2,3}]\\s*(.*)")
                val lm = lineRe.matcher(raw.trim())
                if (!lm.matches()) return null
                val wordPart = lm.group(1) ?: ""
                val wordRe = Pattern.compile("<\\d{2}:\\d{2}\\.\\d{2,3}>")
                wordRe.matcher(wordPart).replaceAll("").trim().takeIf { it.isNotBlank() }
            }
            else -> parseLrcLine(raw)?.text
        }
    }

    /**
     * 提取行首时间戳（毫秒），用于翻译匹配。
     * 取 [mm:ss.ms] 行首标签，不取词级标签。
     */
    private fun extractTimeMs(raw: String): Long {
        val m = LRC_TIME_RE.matcher(raw)
        if (!m.find()) return -1
        return m.group(1)!!.toLong() * 60000 +
                m.group(2)!!.toLong() * 1000 +
                (if (m.group(3)!!.length == 2) m.group(3)!!.toLong() * 10 else m.group(3)!!.toLong())
    }

    /**
     * 解析 ELRC 单行：[mm:ss.ms] <mm:ss.ms>word <mm:ss.ms>word...
     */
    private fun parseElrcLine(raw: String): RichLyricLine? {
        val lineRe = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]\\s*(.*)")
        val wordRe = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val lm = lineRe.matcher(trimmed)
        if (!lm.matches()) return null

        val wordPart = lm.group(4) ?: ""

        val wm = wordRe.matcher(wordPart)
        val words = mutableListOf<LyricWord>()
        while (wm.find()) {
            val wordBegin = wm.group(1)!!.toLong() * 60000 +
                    wm.group(2)!!.toLong() * 1000 +
                    (if (wm.group(3)!!.length == 2) wm.group(3)!!.toLong() * 10 else wm.group(3)!!.toLong())
            val wordText = wm.group(4) ?: ""
            if (wordText.isBlank()) continue
            words.add(LyricWord(begin = wordBegin, end = wordBegin + 500, duration = 500, text = wordText))
        }

        if (words.isEmpty()) return null

        // 修正每个词的 end 为下一个词的 begin
        for (i in 0 until words.size - 1) {
            words[i].end = words[i + 1].begin
            words[i].duration = words[i].end - words[i].begin
        }
        words.last().end = words.last().begin + 500
        words.last().duration = 500

        // 行级时间以第一个词的 begin 为准（行首时间戳可能与词级时间不一致）
        val lineBegin = words.first().begin
        val lineEnd = words.last().end
        val lineText = words.joinToString("") { it.text.orEmpty() }
        return RichLyricLine(begin = lineBegin, end = lineEnd, duration = lineEnd - lineBegin, text = lineText, words = words)
    }

    /**
     * 解析 LRC 单行：[mm:ss.xx]文本
     */
    private fun parseLrcLine(raw: String): RichLyricLine? {
        val re = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        val m = re.matcher(raw.trim())
        if (!m.matches()) return null
        val ms = m.group(1)!!.toLong() * 60000 + m.group(2)!!.toLong() * 1000 +
                (if (m.group(3)!!.length == 2) m.group(3)!!.toLong() * 10 else m.group(3)!!.toLong())
        val text = m.group(4)!!.trim()
        if (text.isBlank()) return null
        return RichLyricLine(begin = ms, text = text)
    }

    fun diagnose(json: String): LyricInfoDiagnosis? {
        return try {
            val obj = JSONObject(json)
            LyricInfoDiagnosis(
                songName = obj.optString("songName", ""),
                artist = obj.optString("artist", ""),
                songId = obj.optString("songId", ""),
                format = obj.optString("format", ""),
                translationFormat = obj.optString("translation", ""),
                lyricLength = obj.optString("lyric", "").length,
                lyricPreview = obj.optString("lyric", "").lines().filter { it.isNotBlank() }.drop(3).take(10)
            )
        } catch (_: Exception) {
            null
        }
    }
}

private data class ParsedLine(val timeMs: Long, val raw: String)

data class LyricInfoDiagnosis(
    val songName: String,
    val artist: String,
    val songId: String,
    val format: String,
    val translationFormat: String,
    val lyricLength: Int,
    val lyricPreview: List<String>
)
