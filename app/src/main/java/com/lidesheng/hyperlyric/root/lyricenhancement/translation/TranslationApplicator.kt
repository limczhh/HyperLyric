package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import java.util.Locale
import kotlin.math.abs

internal object TranslationApplicator {
    fun hasTranslation(line: RichLyricLine): Boolean =
        !line.translation.isNullOrBlank() ||
                line.translationWords.orEmpty().any { !it.text.isNullOrBlank() }

    fun apply(
        song: Song,
        items: List<TranslationItem>,
        forceOverride: Boolean,
        logger: LyricEnhancementLogger,
    ): Song? {
        val byIndex = items.associateBy { it.index }
        var appliedCount = 0
        val newLyrics = song.lyrics?.mapIndexed { index, line ->
            val translation = byIndex[index]?.trans?.trim()
            if (
                !translation.isNullOrBlank() &&
                (forceOverride || !hasTranslation(line)) &&
                !translation.equals(line.text?.trim(), ignoreCase = true)
            ) {
                appliedCount++
                line.copy(translation = translation, translationWords = null)
            } else {
                line
            }
        }
        logger.debug("应用翻译结果: song=${song.name}, lines=$appliedCount")
        return if (newLyrics != song.lyrics) song.copy(lyrics = newLyrics) else null
    }

    /**
     * 将 sourceSong 中的翻译合并到 targetSong，同时保留 targetSong 的歌词结构。
     *
     * AI 始终基于原始歌词生成按源行索引排列的结果，AMLL 可能合并同一时间点的行，
     * 因此这里不能直接把两个列表按下标覆盖，而是按歌词文本和时间做一对一匹配。
     */
    fun merge(
        sourceSong: Song,
        targetSong: Song,
        forceOverride: Boolean,
        logger: LyricEnhancementLogger,
    ): Song? {
        val sourceLyrics = sourceSong.lyrics.orEmpty()
        val targetLyrics = targetSong.lyrics ?: return null
        if (sourceLyrics.isEmpty() || targetLyrics.isEmpty()) return null

        val usedSourceIndices = mutableSetOf<Int>()
        var appliedCount = 0
        val newLyrics = targetLyrics.map { targetLine ->
            val sourceIndex = findSourceIndex(
                sourceLyrics = sourceLyrics,
                targetLine = targetLine,
                usedSourceIndices = usedSourceIndices,
            )
            val sourceLine = sourceIndex?.let { index ->
                usedSourceIndices += index
                sourceLyrics[index]
            }
            val translation = sourceLine?.translationText()
            if (
                !translation.isNullOrBlank() &&
                (forceOverride || !hasTranslation(targetLine)) &&
                !translation.equals(targetLine.text?.trim(), ignoreCase = true)
            ) {
                appliedCount++
                targetLine.copy(translation = translation, translationWords = null)
            } else {
                targetLine
            }
        }

        logger.debug("合并翻译结果: song=${targetSong.name}, lines=$appliedCount")
        return if (newLyrics != targetLyrics) targetSong.copy(lyrics = newLyrics) else null
    }

    private fun findSourceIndex(
        sourceLyrics: List<RichLyricLine>,
        targetLine: RichLyricLine,
        usedSourceIndices: Set<Int>,
    ): Int? {
        val targetText = normalizeText(targetLine.text)
        if (targetText.isBlank()) return null

        return sourceLyrics.indices
            .asSequence()
            .filter { it !in usedSourceIndices }
            .mapNotNull { index ->
                val sourceLine = sourceLyrics[index]
                if (sourceLine.translationText().isNullOrBlank()) return@mapNotNull null
                val sourceText = normalizeText(sourceLine.text)
                if (sourceText.isBlank()) return@mapNotNull null
                val textRank = when {
                    sourceText == targetText -> 0
                    sourceText.contains(targetText) || targetText.contains(sourceText) -> 1
                    else -> return@mapNotNull null
                }
                MatchCandidate(
                    index = index,
                    textRank = textRank,
                    beginDistance = abs(sourceLine.begin - targetLine.begin),
                    endDistance = abs(sourceLine.end - targetLine.end),
                )
            }
            .minWithOrNull(
                compareBy<MatchCandidate> { it.textRank }
                    .thenBy { it.beginDistance }
                    .thenBy { it.endDistance }
            )
            ?.index
    }

    private fun normalizeText(text: String?): String =
        text.orEmpty()
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }

    private fun RichLyricLine.translationText(): String? =
        translation?.trim()?.takeIf { it.isNotBlank() }
            ?: translationWords
                ?.asSequence()
                ?.mapNotNull { it.text?.takeIf(String::isNotBlank) }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }

    private data class MatchCandidate(
        val index: Int,
        val textRank: Int,
        val beginDistance: Long,
        val endDistance: Long,
    )
}
