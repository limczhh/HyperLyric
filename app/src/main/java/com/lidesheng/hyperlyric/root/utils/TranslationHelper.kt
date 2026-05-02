package com.lidesheng.hyperlyric.root.utils

import android.content.SharedPreferences
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine

object TranslationHelper {

    fun isTranslationDisabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(Constants.KEY_HOOK_DISABLE_TRANSLATION, Constants.DEFAULT_HOOK_DISABLE_TRANSLATION)
    }

    fun isTranslationOnly(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(Constants.KEY_HOOK_TRANSLATION_ONLY, Constants.DEFAULT_HOOK_TRANSLATION_ONLY)
    }

    fun applyTranslationOnly(line: IRichLyricLine): IRichLyricLine {
        val translation = line.translation
        if (translation.isNullOrBlank()) return line

        return io.github.proify.lyricon.lyric.model.RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            text = translation,
            words = line.translationWords ?: emptyList(),
            translation = line.text,
            translationWords = line.words,
            secondary = line.secondary,
            secondaryWords = line.secondaryWords,
            roma = line.roma,
            metadata = line.metadata
        )
    }
}
