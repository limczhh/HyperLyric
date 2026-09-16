package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.AiTranslationLanguageSettings
import com.lidesheng.hyperlyric.common.RootConstants

internal data class AiTranslationConfig(
    val provider: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val targetLanguage: String,
    val prompt: String,
    val skipLanguages: Set<String>,
    val skipExisting: Boolean,
    val forceOverride: Boolean,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
    val enabled: Boolean,
) {
    val isUsable: Boolean
        get() = enabled &&
                provider.isNotBlank() &&
                apiKey.isNotBlank() &&
                baseUrl.isNotBlank() &&
                model.isNotBlank() &&
                targetLanguage.isNotBlank()

    companion object {
        val PREFERENCE_KEYS = setOf(
            RootConstants.KEY_HOOK_AI_TRANS_ENABLE,
            RootConstants.KEY_HOOK_AI_TRANS_SKIP_LANGUAGES,
            RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION,
            RootConstants.KEY_HOOK_AI_TRANS_FORCE_OVERRIDE,
            RootConstants.KEY_HOOK_AI_TRANS_PROVIDER,
            RootConstants.KEY_HOOK_AI_TRANS_API_KEY,
            RootConstants.KEY_HOOK_AI_TRANS_MODEL,
            RootConstants.KEY_HOOK_AI_TRANS_BASE_URL,
            RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG,
            RootConstants.KEY_HOOK_AI_TRANS_PROMPT,
            RootConstants.KEY_HOOK_AI_TRANS_TEMPERATURE,
            RootConstants.KEY_HOOK_AI_TRANS_TOP_P,
            RootConstants.KEY_HOOK_AI_TRANS_MAX_TOKENS,
        )

        fun from(preferences: SharedPreferences): AiTranslationConfig =
            AiTranslationConfig(
                provider = preferences.getString(
                    RootConstants.KEY_HOOK_AI_TRANS_PROVIDER,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_PROVIDER
                ).orEmpty(),
                apiKey = preferences.getString(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, "")
                    .orEmpty(),
                baseUrl = preferences.getString(
                    RootConstants.KEY_HOOK_AI_TRANS_BASE_URL,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_BASE_URL
                ).orEmpty(),
                model = preferences.getString(
                    RootConstants.KEY_HOOK_AI_TRANS_MODEL,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_MODEL
                ).orEmpty(),
                targetLanguage = preferences.getString(
                    RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG
                ).orEmpty(),
                prompt = preferences.getString(
                    RootConstants.KEY_HOOK_AI_TRANS_PROMPT,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT
                ).orEmpty(),
                skipLanguages = AiTranslationLanguageSettings.getSkipLanguages(preferences),
                skipExisting = preferences.getBoolean(
                    RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION
                ),
                forceOverride = preferences.getBoolean(
                    RootConstants.KEY_HOOK_AI_TRANS_FORCE_OVERRIDE,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_FORCE_OVERRIDE
                ),
                temperature = preferences.getFloat(
                    RootConstants.KEY_HOOK_AI_TRANS_TEMPERATURE,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_TEMPERATURE
                )
                    .coerceIn(0f, 2f),
                topP = preferences.getFloat(
                    RootConstants.KEY_HOOK_AI_TRANS_TOP_P,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_TOP_P
                )
                    .coerceIn(0f, 1f),
                maxTokens = preferences.getLong(
                    RootConstants.KEY_HOOK_AI_TRANS_MAX_TOKENS,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_MAX_TOKENS
                )
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt(),
                enabled = preferences.getBoolean(
                    RootConstants.KEY_HOOK_AI_TRANS_ENABLE,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE
                )
            )

    }
}
