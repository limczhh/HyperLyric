package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginSong

/** Decides whether a lyric snapshot contains enough rows to justify translation. */
internal object TranslationEligibility {
    private const val MIN_TRANSLATABLE_LINES = 3

    /** Returns a stable skip reason, or null when the snapshot is eligible for translation. */
    fun skipReason(song: PluginSong): String? {
        val lineCount = song.lyrics.orEmpty().count { !it.text.isNullOrBlank() }
        return when {
            lineCount == 0 -> "no_lyrics"
            lineCount < MIN_TRANSLATABLE_LINES -> "too_few_lines"
            else -> null
        }
    }
}
