package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import java.util.Locale

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
        fun from(config: PluginConfig): AiTranslationConfig = AiTranslationConfig(
            provider = config.getString("provider", DEFAULT_PROVIDER).orEmpty(),
            apiKey = config.getString("api_key", "").orEmpty(),
            baseUrl = config.getString("base_url", DEFAULT_BASE_URL).orEmpty(),
            model = config.getString("model", DEFAULT_MODEL).orEmpty(),
            targetLanguage = config.getString("target_language", DEFAULT_TARGET).orEmpty(),
            prompt = config.getString("prompt", DEFAULT_PROMPT).orEmpty(),
            skipLanguages = config.getStringSet("skip_languages")
                .mapNotNull(::normalizeLanguageCode)
                .toSet(),
            skipExisting = config.getBoolean("skip_existing"),
            forceOverride = config.getBoolean("force_override"),
            temperature = config.getFloat("temperature", DEFAULT_TEMPERATURE)
                .coerceIn(0f, 2f),
            topP = config.getFloat("top_p", DEFAULT_TOP_P)
                .coerceIn(0f, 1f),
            maxTokens = config.getLong("max_tokens", 0L)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt(),
            enabled = config.getBoolean("enabled")
        )

        private fun normalizeLanguageCode(value: String): String? =
            Locale.forLanguageTag(value.trim().replace('_', '-'))
                .language
                .lowercase(Locale.ROOT)
                .takeIf { it.isNotBlank() }

        private const val DEFAULT_PROVIDER = "OPENAI"
        private const val DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1/"
        private const val DEFAULT_MODEL = "mimo-v2.5"
        private const val DEFAULT_TARGET = "中文"
        private const val DEFAULT_TEMPERATURE = 1f
        private const val DEFAULT_TOP_P = 1f
        private const val DEFAULT_PROMPT = "你是一个歌词翻译专家，遵循‘信雅达’原则进行创作。"
    }
}
