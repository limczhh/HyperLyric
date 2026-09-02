package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import org.json.JSONObject
import java.util.regex.Pattern

object LyricInfoParser {

    private val LRC_TIME_RE = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]")
    private val ELRC_WORD_TIME_RE = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>")

    fun parse(json: String): Song? = parsePayload(json)?.song

    fun parsePayload(json: String): LyricInfoPayload? {
        return try {
            val obj = JSONObject(json)
            val lyricRaw = obj.optionalText("lyric")
            val rawLyric = obj.optionalText("rawLyric")
            val translationRaw = obj.optionalText("translation")
            val primaryRaw = lyricRaw ?: return null

            val title = obj.optionalText("songName")
            val artist = obj.optionalText("artist")
            val album = obj.optionalText("album")
            val songId = obj.optionalText("songId")

            val parsedPrimary = if (rawLyric != null) {
                parseLyricLines(rawLyric, enhanced = true)
            } else {
                parseLyricLines(primaryRaw, enhanced = false)
            } ?: return null
            val resultLines = attachTranslation(parsedPrimary, translationRaw)

            LyricInfoPayload(
                song = Song(
                    id = songId,
                    name = title,
                    artist = artist,
                    album = album,
                    lyrics = resultLines
                ),
                songId = songId,
                title = title,
                artist = artist,
                album = album
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLyricLines(
        lyricRaw: String,
        enhanced: Boolean
    ): List<ParsedLine>? {
        if (lyricRaw.isBlank()) return null

        val allLines = lyricRaw.lines().filter { it.isNotBlank() }

        val parsedLines = allLines.mapNotNull { line ->
            parseLine(line, enhanced)?.let { parsedLine ->
                ParsedLine(extractTimeMs(line), parsedLine)
            }
        }
        return parsedLines
            .takeIf { it.isNotEmpty() }
            ?.also(::completeLineTiming)
    }

    /** Match the independent translation lane to original lines by line timestamp. */
    private fun attachTranslation(
        originalLines: List<ParsedLine>,
        translationRaw: String?
    ): List<RichLyricLine> {
        if (translationRaw.isNullOrBlank()) {
            return originalLines.map { it.line }
        }

        val translationLines = parseLyricLines(
            translationRaw,
            enhanced = ELRC_WORD_TIME_RE.matcher(translationRaw).find()
        ) ?: return originalLines.map { it.line }
        val translationByTime = translationLines
            .groupBy { it.timeMs }
            .mapValues { (_, lines) -> lines.toMutableList() }

        return originalLines.map { original ->
            val candidates = translationByTime[original.timeMs]
            val translation = candidates?.takeIf { it.isNotEmpty() }?.removeAt(0)?.line
            if (translation == null) {
                original.line
            } else {
                original.line.copy(
                    translation = translation.text,
                    translationWords = translation.words
                )
            }
        }
    }

    private fun completeLineTiming(lines: List<ParsedLine>) {
        for (idx in lines.indices) {
            val cur = lines[idx].line
            val nextBegin = lines.getOrNull(idx + 1)?.line?.begin
            if (cur.end <= cur.begin) {
                cur.end = nextBegin ?: (cur.begin + 5000)
                cur.duration = cur.end - cur.begin
            }
            cur.words?.lastOrNull()?.let { lastWord ->
                if (lastWord.end < cur.end) {
                    lastWord.end = cur.end
                    lastWord.duration = lastWord.end - lastWord.begin
                }
            }
        }
    }

    /**
     * 解析单行为 RichLyricLine（ELRC 或 LRC）。
     */
    private fun parseLine(raw: String, enhanced: Boolean): RichLyricLine? =
        if (enhanced) parseElrcLine(raw) else parseLrcLine(raw)

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
        val lineTimestamp = toTimeMs(lm.group(1)!!, lm.group(2)!!, lm.group(3)!!)

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

        if (words.isEmpty()) {
            // Some providers mark ordinary LRC lines as ELRC without adding
            // any word-level timestamp. Keep the line instead of dropping it.
            return if (!wordRe.matcher(wordPart).find()) parseLrcLine(trimmed) else null
        }

        // A single word beginning exactly at the line timestamp carries no
        // intra-line timing. Treat it as a normal line to avoid word animation.
        if (words.size == 1 && words.first().begin == lineTimestamp) {
            return RichLyricLine(begin = lineTimestamp, text = words.first().text)
        }

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

    private fun toTimeMs(minutes: String, seconds: String, fraction: String): Long =
        minutes.toLong() * 60000 + seconds.toLong() * 1000 +
                (if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong())

    fun diagnose(json: String): LyricInfoDiagnosis? {
        return try {
            val obj = JSONObject(json)
            val rawLyric = obj.optString("rawLyric", "")
            val lyric = obj.optString("lyric", "")
            val previewSource = rawLyric.takeIf { it.isNotBlank() } ?: lyric
            LyricInfoDiagnosis(
                songName = obj.optString("songName", ""),
                artist = obj.optString("artist", ""),
                songId = obj.optString("songId", ""),
                rawLyricLength = rawLyric.length,
                lyricLength = lyric.length,
                translationLength = obj.optString("translation", "").length,
                lyricPreview = previewSource.lines().filter { it.isNotBlank() }.take(10)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun JSONObject.optionalText(key: String): String? =
        optString(key, "").trim().takeIf { it.isNotEmpty() }
}

private data class ParsedLine(val timeMs: Long, val line: RichLyricLine)

data class LyricInfoPayload(
    val song: Song,
    val songId: String?,
    val title: String?,
    val artist: String?,
    val album: String?
)

data class LyricInfoDiagnosis(
    val songName: String,
    val artist: String,
    val songId: String,
    val rawLyricLength: Int,
    val lyricLength: Int,
    val translationLength: Int,
    val lyricPreview: List<String>
)
