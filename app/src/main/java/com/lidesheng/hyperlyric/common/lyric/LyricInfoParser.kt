package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import org.json.JSONObject

/**
 * lyricInfo JSON 解析工具。
 *
 * JSON 格式：
 * {
 *   "songName": "歌曲名",
 *   "artist": "歌手",
 *   "songId": "歌曲ID",
 *   "lyric": "[mm:ss.xx]歌词 / 翻译",
 *   "lyricWord": "[beginMs,durMs](offset,dur)字...",
 *   "translation": "[mm:ss.xx]翻译"
 * }
 */
object LyricInfoParser {

    /**
     * 解析 lyricInfo JSON 为 Song 对象。
     * 优先使用 lyricWord（逐字），回退到 lyric（逐行）。
     * translation 合并到每行的 translation 字段。
     */
    fun parse(json: String, songName: String, artist: String): Song? {
        return try {
            val obj = JSONObject(json)
            val lyricWordRaw = obj.optString("lyricWord", "").trim()
            val lyricRaw = obj.optString("lyric", "").trim()
            val translationRaw = obj.optString("translation", "").trim()

            val lines = when {
                lyricWordRaw.isNotBlank() -> parseYrc(lyricWordRaw)
                lyricRaw.isNotBlank() -> parseLrc(lyricRaw)
                else -> return null
            }
            if (lines.isEmpty()) return null

            val withTranslation = if (translationRaw.isNotBlank()) {
                val transLines = parseLrc(translationRaw)
                lines.mapIndexed { i, line ->
                    val trans = transLines.getOrNull(i)?.text
                    if (!trans.isNullOrBlank()) line.copy(translation = trans) else line
                }
            } else lines

            Song(name = songName, artist = artist, lyrics = withTranslation)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 提取字段信息用于诊断日志。
     */
    fun diagnose(json: String): LyricInfoDiagnosis? {
        return try {
            val obj = JSONObject(json)
            LyricInfoDiagnosis(
                songName = obj.optString("songName", ""),
                artist = obj.optString("artist", ""),
                songId = obj.optString("songId", ""),
                lyricLength = obj.optString("lyric", "").length,
                lyricWordLength = obj.optString("lyricWord", "").length,
                translationLength = obj.optString("translation", "").length,
                lyricPreview = obj.optString("lyric", "").lines().filter { it.isNotBlank() }.drop(3).take(3),
                lyricWordPreview = obj.optString("lyricWord", "").lines().filter { it.isNotBlank() }.drop(3).take(3),
                translationPreview = obj.optString("translation", "").lines().filter { it.isNotBlank() }.drop(3).take(3)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 解析 LRC 格式：[mm:ss.xx]文本
     */
    private fun parseLrc(lrc: String): List<RichLyricLine> {
        val re = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
        val lines = lrc.lines().mapNotNull { l ->
            val m = re.matchEntire(l.trim()) ?: return@mapNotNull null
            val ms = m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 +
                    (if (m.groupValues[3].length == 2) m.groupValues[3].toLong() * 10 else m.groupValues[3].toLong())
            val text = m.groupValues[4].trim()
            if (text.isBlank()) null else RichLyricLine(begin = ms, text = text)
        }.sortedBy { it.begin }
        for (i in lines.indices) {
            val next = if (i + 1 < lines.size) lines[i + 1].begin else lines[i].begin + 5000
            lines[i].end = next; lines[i].duration = next - lines[i].begin
        }
        return lines
    }

    /**
     * 解析 YRC 逐字格式：[beginMs,durMs](offset,dur)文本...
     */
    private fun parseYrc(yrc: String): List<RichLyricLine> {
        val lineRe = Regex("""\[(\d+),(\d+)](.*)""")
        val wordRe = Regex("""\((\d+),(\d+)(?:,\d+)?\)""")
        return yrc.lines().mapNotNull { l ->
            val lm = lineRe.matchEntire(l.trim()) ?: return@mapNotNull null
            val lineBegin = lm.groupValues[1].toLong()
            val lineDur = lm.groupValues[2].toLong()
            val wordPart = lm.groupValues[3]
            val wordMatches = wordRe.findAll(wordPart).toList()
            val words = wordMatches.mapIndexed { i, wm ->
                // YRC 格式：word 的第一个数字是绝对时间戳，不是相对偏移
                val wordBegin = wm.groupValues[1].toLong()
                val wDur = wm.groupValues[2].toLong()
                val textStart = wm.range.last + 1
                val textEnd = if (i + 1 < wordMatches.size) wordMatches[i + 1].range.first else wordPart.length
                LyricWord(begin = wordBegin, end = wordBegin + wDur, duration = wDur, text = wordPart.substring(textStart, textEnd))
            }.filter { !it.text.isNullOrBlank() }
            val text = words.joinToString("") { it.text.orEmpty() }
            if (text.isBlank()) null
            else RichLyricLine(begin = lineBegin, end = lineBegin + lineDur, duration = lineDur, text = text, words = words.ifEmpty { null })
        }.sortedBy { it.begin }
    }
}

data class LyricInfoDiagnosis(
    val songName: String,
    val artist: String,
    val songId: String,
    val lyricLength: Int,
    val lyricWordLength: Int,
    val translationLength: Int,
    val lyricPreview: List<String>,
    val lyricWordPreview: List<String>,
    val translationPreview: List<String>
)
