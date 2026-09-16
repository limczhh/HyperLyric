package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.Song

internal object TranslationEligibility {
    private const val MIN_TRANSLATABLE_LINES = 3

    fun skipReason(song: Song): String? {
        val lineCount = song.lyrics.orEmpty().count { !it.text.isNullOrBlank() }
        return when {
            lineCount == 0 -> "no_lyrics"
            lineCount < MIN_TRANSLATABLE_LINES -> "too_few_lines"
            else -> null
        }
    }
}
