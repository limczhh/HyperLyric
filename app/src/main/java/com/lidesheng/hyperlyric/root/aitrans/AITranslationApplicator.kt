package com.lidesheng.hyperlyric.root.aitrans

import android.util.Log
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.proify.lyricon.lyric.model.Song

/** Applies validated translation items back to lyric lines. */
internal object AITranslationApplicator {
    private const val TAG = "HyperLyricAITranslator"

    fun apply(song: Song, transItems: List<TranslationItem>): Song {
        var appliedCount = 0
        val translationsByIndex = transItems.associateBy { it.index }
        val newLyrics = song.lyrics?.mapIndexed { index, line ->
            val transText = translationsByIndex[index]?.trans?.trim()

            if (!transText.isNullOrBlank()
                && line.translation.isNullOrBlank()
                && transText.lowercase() != line.text?.trim()?.lowercase()
            ) {
                appliedCount++
                line.copy(translation = transText, translationWords = null)
            } else {
                line
            }
        }
        HookLogger.d("AITranslationApplicator", "AITranslation : Result: Applied $appliedCount lines to ${song.name}")
        return song.copy(lyrics = newLyrics)
    }
}
