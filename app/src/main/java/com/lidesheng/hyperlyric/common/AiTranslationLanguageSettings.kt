package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences
import java.util.Locale

object AiTranslationLanguageSettings {
    const val LANGUAGE_CHINESE = "zh"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_JAPANESE = "ja"
    const val LANGUAGE_KOREAN = "ko"
    const val LANGUAGE_SPANISH = "es"

    fun getSkipLanguages(prefs: SharedPreferences): Set<String> {
        val storedLanguages = runCatching {
            prefs.getStringSet(
                RootConstants.KEY_HOOK_AI_TRANS_SKIP_LANGUAGES,
                RootConstants.DEFAULT_HOOK_AI_TRANS_SKIP_LANGUAGES
            )
        }.getOrNull().orEmpty()

        return storedLanguages.mapNotNullTo(linkedSetOf(), ::normalizeLanguageCode)
    }

    private fun normalizeLanguageCode(languageCode: String): String? {
        val normalized = Locale.forLanguageTag(languageCode.trim().replace('_', '-'))
            .language
            .lowercase(Locale.ROOT)
        return normalized.takeIf { it.isNotBlank() }
    }
}
