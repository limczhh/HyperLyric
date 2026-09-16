package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.Song
import java.util.Locale

/** Conservative script-based fallback for automatic skip-language behavior without Context. */
internal data class DetectedLanguage(
    val language: String,
    val languageTag: String,
    val confidence: Float,
    val secondConfidence: Float?,
    val hypothesisCount: Int,
)

internal object TranslationLanguageDetector {
    private const val MAX_SAMPLE_LENGTH = 2_000
    private const val MIN_LETTERS = 12
    private val whitespace = Regex("\\s+")
    private val SPANISH_WORDS = setOf(
        "el", "la", "los", "las", "de", "del", "que", "en", "un", "una", "por",
        "para", "con", "como", "te", "yo", "tu", "tú", "mi", "amor", "sin", "quiero"
    )

    fun detect(song: Song): DetectedLanguage? {
        val sample = buildSample(song)
        if (sample.count { it.isLetterOrDigit() } < MIN_LETTERS) return null
        return detectSample(sample)
    }

    private fun buildSample(song: Song): String = buildString {
        val seen = HashSet<String>()
        for (line in song.lyrics.orEmpty()) {
            val text = line.text
                ?.trim()
                ?.replace(whitespace, " ")
                ?.takeIf { it.isNotBlank() }
                ?: continue
            if (!seen.add(text)) continue
            if (isNotEmpty()) append('\n')
            val remaining = MAX_SAMPLE_LENGTH - length
            if (remaining <= 0) break
            append(text.take(remaining))
            if (length >= MAX_SAMPLE_LENGTH) break
        }
    }

    private fun detectSample(text: String): DetectedLanguage? {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return null

        val total = letters.length.toFloat()
        val han = letters.count(::isHan) / total
        val kana = letters.count(::isKana) / total
        val hangul = letters.count(::isHangul) / total
        val latin = letters.count(::isLatin) / total
        val lowerWords = text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}]+"))
            .filter(String::isNotBlank)
        val spanishWordCount = lowerWords.count { it in SPANISH_WORDS }
        val spanishMarkers = text.count { it in "ñÑ¿¡" }
        val spanishWordRatio = if (lowerWords.isEmpty()) {
            0f
        } else {
            spanishWordCount.toFloat() / lowerWords.size
        }
        val spanishScore = when {
            spanishMarkers > 0 -> 0.95f
            spanishWordCount >= 3 && spanishWordRatio >= 0.2f -> 0.85f
            spanishWordCount >= 2 && spanishWordRatio >= 0.35f -> 0.75f
            else -> 0f
        }

        val candidates = listOf(
            "zh" to han,
            "ja" to (kana * 1.4f + han * 0.2f).coerceAtMost(1f),
            "ko" to hangul,
            "es" to (spanishScore + latin * 0.1f).coerceAtMost(1f),
            "en" to (latin * 0.9f - spanishScore * 0.5f).coerceAtLeast(0f)
        ).sortedByDescending { it.second }

        val top = candidates.firstOrNull() ?: return null
        return DetectedLanguage(
            language = top.first,
            languageTag = top.first,
            confidence = top.second,
            secondConfidence = candidates.getOrNull(1)?.second,
            hypothesisCount = candidates.size
        )
    }

    private fun isHan(char: Char): Boolean =
        char in '\u4E00'..'\u9FFF' || char in '\u3400'..'\u4DBF'

    private fun isKana(char: Char): Boolean =
        char in '\u3040'..'\u30FF' || char in '\u31F0'..'\u31FF'

    private fun isHangul(char: Char): Boolean =
        char in '\uAC00'..'\uD7AF' || char in '\u1100'..'\u11FF'

    private fun isLatin(char: Char): Boolean =
        char in '\u0000'..'\u024F' || char in '\u1E00'..'\u1EFF'

}
