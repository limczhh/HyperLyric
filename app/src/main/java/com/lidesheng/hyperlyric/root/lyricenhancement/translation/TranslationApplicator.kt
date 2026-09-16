package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger

internal object TranslationApplicator {
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
                (forceOverride || line.translation.isNullOrBlank()) &&
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
}
