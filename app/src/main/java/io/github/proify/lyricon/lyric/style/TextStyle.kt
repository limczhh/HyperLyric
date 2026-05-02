package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class TextStyle(
    var textSize: Float = Defaults.TEXT_SIZE,
    var margins: RectF = Defaults.MARGINS,
    var paddings: RectF = Defaults.PADDINGS,
    var repeatOutput: Boolean = Defaults.REPEAT_OUTPUT,

    var fadingEdgeLength: Int = Defaults.FADING_EDGE_LENGTH,

    var enableCustomTextColor: Boolean = Defaults.ENABLE_CUSTOM_TEXT_COLOR,
    var enableExtractCoverTextColor: Boolean = Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR,
    var enableExtractCoverTextGradient: Boolean = Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT,
    var lightModeRainbowColor: RainbowTextColor? = Defaults.LIGHT_MODE_RAINBOW_COLOR,
    var darkModeRainbowColor: RainbowTextColor? = Defaults.DARK_MODE_RAINBOW_COLOR,

    var typeFace: String? = Defaults.TYPE_FACE,
    var typeFaceBold: Boolean = Defaults.TYPE_FACE_BOLD,
    var typeFaceItalic: Boolean = Defaults.TYPE_FACE_ITALIC,
    var fontWeight: Int = Defaults.FONT_WEIGHT,

    var marqueeSpeed: Float = Defaults.MARQUEE_SPEED,
    var marqueeGhostSpacing: Float = Defaults.MARQUEE_GHOST_SPACING,
    var marqueeLoopDelay: Int = Defaults.MARQUEE_LOOP_DELAY,
    var marqueeRepeatCount: Int = Defaults.MARQUEE_REPEAT_COUNT,
    var marqueeStopAtEnd: Boolean = Defaults.MARQUEE_STOP_AT_END,
    var marqueeInitialDelay: Int = Defaults.MARQUEE_INITIAL_DELAY,
    var marqueeRepeatUnlimited: Boolean = Defaults.MARQUEE_REPEAT_UNLIMITED,
    var gradientProgressStyle: Boolean = Defaults.ENABLE_GRADIENT_PROGRESS_STYLE,

    var relativeProgress: Boolean = Defaults.RELATIVE_PROGRESS,
    var relativeProgressHighlight: Boolean = Defaults.RELATIVE_PROGRESS_HIGHLIGHT,
    var wordMotionEnabled: Boolean = Defaults.WORD_MOTION_ENABLED,
    var wordMotionCjkLiftFactor: Float = Defaults.WORD_MOTION_CJK_LIFT_FACTOR,
    var wordMotionCjkWaveFactor: Float = Defaults.WORD_MOTION_CJK_WAVE_FACTOR,
    var wordMotionLatinLiftFactor: Float = Defaults.WORD_MOTION_LATIN_LIFT_FACTOR,
    var wordMotionLatinWaveFactor: Float = Defaults.WORD_MOTION_LATIN_WAVE_FACTOR,
    var scaleInMultiLine: Float = Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE,

    var transitionConfig: String? = Defaults.TRANSITION_CONFIG,
    var placeholderFormat: String? = Defaults.PLACEHOLDER_FORMAT,

    var isDisableTranslation: Boolean = false,
    var isTranslationOnly: Boolean = false,

    var isAiTranslationEnable: Boolean = false,
    var aiTranslationConfigs: AiTranslationConfigs? = null,
    var isAiTranslationAutoIgnoreChinese: Boolean = false
) {

    companion object {
        const val TRANSITION_CONFIG_FAST: String = "fast"
        const val TRANSITION_CONFIG_SMOOTH: String = "smooth"
        const val TRANSITION_CONFIG_SLOW: String = "slow"
        const val TRANSITION_CONFIG_NONE = "none"

        const val KEY_AI_TRANSLATION_ENABLED = "lyric_style_text_ai_translation_enabled"
        const val KEY_AI_TRANSLATION_PROVIDER = "lyric_style_text_ai_translation_provider"
        const val KEY_AI_TRANSLATION_TARGET_LANGUAGE =
            "lyric_style_text_ai_translation_target_language"
        const val KEY_AI_TRANSLATION_API_KEY = "lyric_style_text_ai_translation_key"
        const val KEY_AI_TRANSLATION_MODEL = "lyric_style_text_ai_translation_model"
        const val KEY_AI_TRANSLATION_BASE_URL = "lyric_style_text_ai_translation_base_url"
        const val KEY_AI_TRANSLATION_PROMPT = "lyric_style_text_ai_translation_prompt"
        const val KEY_AI_TRANSLATION_TEMPERATURE = "lyric_style_text_ai_translation_temperature"
        const val KEY_AI_TRANSLATION_TOP_P = "lyric_style_text_ai_translation_top_p"
        const val KEY_AI_TRANSLATION_MAX_TOKENS = "lyric_style_text_ai_translation_max_tokens"
        const val KEY_AI_TRANSLATION_PRESENCE_PENALTY =
            "lyric_style_text_ai_translation_presence_penalty"
        const val KEY_AI_TRANSLATION_FREQUENCY_PENALTY =
            "lyric_style_text_ai_translation_frequency_penalty"

        const val KEY_TEXT_TRANSLATION_ONLY = "lyric_style_text_translation_only"
        const val KEY_TEXT_TRANSLATION_DISABLE = "lyric_style_text_translation_disable"
        const val KEY_WORD_MOTION_ENABLED = "lyric_style_text_word_motion_enabled"
        const val KEY_WORD_MOTION_CJK_LIFT_FACTOR = "lyric_style_text_word_motion_cjk_lift_factor"
        const val KEY_WORD_MOTION_CJK_WAVE_FACTOR = "lyric_style_text_word_motion_cjk_wave_factor"
        const val KEY_WORD_MOTION_LATIN_LIFT_FACTOR =
            "lyric_style_text_word_motion_latin_lift_factor"
        const val KEY_WORD_MOTION_LATIN_WAVE_FACTOR =
            "lyric_style_text_word_motion_latin_wave_factor"
        const val KEY_AI_TRANSLATION_IGNORE_CHINESE: String =
            "lyric_style_text_ai_translation_auto_ignore_chinese"
    }

    object PlaceholderFormat {
        const val NAME: String = "NameOnly"
        const val NAME_ARTIST: String = "NameAndArtist"
        const val NONE: String = "None"
    }

    object Defaults {
        const val TRANSLATION_ONLY: Boolean = false
        const val TRANSLATION_DISABLE: Boolean = false

        const val AI_TRANSLATION_ENABLED: Boolean = false
        val AI_TRANSLATION_PROVIDER = AiTranslationProvider.OPENAI.provider
        val AI_TRANSLATION_TARGET_LANGUAGE_DISPLAY_NAME: String
            get() {
                val locale = Locale.getDefault()
                val language = locale.getDisplayLanguage(locale)
                val script = locale.getDisplayScript(locale)

                return when {
                    !script.isNullOrBlank() -> script
                    else -> language
                }
            }

        val AI_TRANSLATION_HOST: String by lazy {
            val p = AiTranslationProvider.entries.find {
                it.provider == AI_TRANSLATION_PROVIDER
            }
            p?.url.orEmpty()
        }

        val AI_TRANSLATION_MODEL: String = AiTranslationProvider.OPENAI.model
        val AI_TRANSLATION_PROMPT: String = AiTranslationConfigs.USER_PROMPT
        const val AI_TRANSLATION_TEMPERATURE = AiTranslationConfigs.DEFAULT_TEMPERATURE
        const val AI_TRANSLATION_TOP_P = AiTranslationConfigs.DEFAULT_TOP_P
        const val AI_TRANSLATION_MAX_TOKENS = AiTranslationConfigs.DEFAULT_MAX_TOKENS
        const val AI_TRANSLATION_PRESENCE_PENALTY = AiTranslationConfigs.DEFAULT_PRESENCE_PENALTY
        const val AI_TRANSLATION_FREQUENCY_PENALTY = AiTranslationConfigs.DEFAULT_FREQUENCY_PENALTY
        const val AI_TRANSLATION_IGNORE_CHINESE = false

        const val PLACEHOLDER_FORMAT: String = PlaceholderFormat.NAME
        const val TRANSITION_CONFIG: String = TRANSITION_CONFIG_SMOOTH

        const val TEXT_SIZE_RATIO_IN_MULTI_LINE: Float = 0.86f
        const val RELATIVE_PROGRESS: Boolean = true
        const val RELATIVE_PROGRESS_HIGHLIGHT: Boolean = false
        const val WORD_MOTION_ENABLED: Boolean = true
        const val WORD_MOTION_CJK_LIFT_FACTOR: Float = 0.055f
        const val WORD_MOTION_CJK_WAVE_FACTOR: Float = 2.8f
        const val WORD_MOTION_LATIN_LIFT_FACTOR: Float = 0.065f
        const val WORD_MOTION_LATIN_WAVE_FACTOR: Float = 3.6f

        const val TEXT_SIZE: Float = 0f
        val MARGINS: RectF = RectF()
        val PADDINGS: RectF = RectF()
        const val REPEAT_OUTPUT: Boolean = false

        const val FADING_EDGE_LENGTH: Int = 14

        const val ENABLE_CUSTOM_TEXT_COLOR: Boolean = false
        const val ENABLE_EXTRACT_COVER_TEXT_COLOR: Boolean = false
        const val ENABLE_EXTRACT_COVER_TEXT_GRADIENT: Boolean = false

        val LIGHT_MODE_RAINBOW_COLOR: RainbowTextColor? = null
        val DARK_MODE_RAINBOW_COLOR: RainbowTextColor? = null

        val TYPE_FACE: String? = null
        const val TYPE_FACE_BOLD: Boolean = false
        const val TYPE_FACE_ITALIC: Boolean = false
        const val FONT_WEIGHT: Int = -1

        const val MARQUEE_SPEED: Float = 35f
        const val MARQUEE_GHOST_SPACING: Float = 50f
        const val MARQUEE_LOOP_DELAY: Int = 0

        const val MARQUEE_REPEAT_COUNT: Int = -1
        const val MARQUEE_STOP_AT_END: Boolean = false
        const val MARQUEE_INITIAL_DELAY: Int = 300
        const val MARQUEE_REPEAT_UNLIMITED: Boolean = true
        const val ENABLE_GRADIENT_PROGRESS_STYLE: Boolean = true
    }

    fun color(lightMode: Boolean): RainbowTextColor? =
        if (lightMode) lightModeRainbowColor else darkModeRainbowColor

    fun loadFromPreferences(preferences: SharedPreferences) {
        textSize = preferences.getFloat("lyric_style_text_size", Defaults.TEXT_SIZE)
        repeatOutput =
            preferences.getBoolean("lyric_style_text_repeat_output", Defaults.REPEAT_OUTPUT)
        margins = json.safeDecode<RectF>(preferences.getString("lyric_style_text_margins", null))
            ?: Defaults.MARGINS
        paddings = json.safeDecode<RectF>(preferences.getString("lyric_style_text_paddings", null))
            ?: Defaults.PADDINGS

        enableCustomTextColor = preferences.getBoolean(
            "lyric_style_text_enable_custom_color",
            Defaults.ENABLE_CUSTOM_TEXT_COLOR
        )
        enableExtractCoverTextColor = preferences.getBoolean(
            "lyric_style_text_extract_cover_color",
            Defaults.ENABLE_EXTRACT_COVER_TEXT_COLOR
        )
        enableExtractCoverTextGradient = preferences.getBoolean(
            "lyric_style_text_extract_cover_gradient",
            Defaults.ENABLE_EXTRACT_COVER_TEXT_GRADIENT
        )

        if (!enableExtractCoverTextColor) {
            enableExtractCoverTextGradient = false
        }
        lightModeRainbowColor = json.safeDecode<RainbowTextColor>(
            preferences.getString("lyric_style_text_rainbow_color_light_mode", null),
            Defaults.LIGHT_MODE_RAINBOW_COLOR
        )
        darkModeRainbowColor = json.safeDecode<RainbowTextColor>(
            preferences.getString("lyric_style_text_rainbow_color_dark_mode", null),
            Defaults.DARK_MODE_RAINBOW_COLOR
        )

        fadingEdgeLength =
            preferences.getInt("lyric_style_text_fading_edge_length", Defaults.FADING_EDGE_LENGTH)

        typeFace = preferences.getString("lyric_style_text_typeface", Defaults.TYPE_FACE)
        typeFaceBold =
            preferences.getBoolean("lyric_style_text_typeface_bold", Defaults.TYPE_FACE_BOLD)
        typeFaceItalic =
            preferences.getBoolean("lyric_style_text_typeface_italic", Defaults.TYPE_FACE_ITALIC)
        fontWeight = preferences.getInt("lyric_style_text_weight", Defaults.FONT_WEIGHT)

        marqueeSpeed =
            preferences.getFloat("lyric_style_text_marquee_speed", Defaults.MARQUEE_SPEED)
        marqueeGhostSpacing =
            preferences.getFloat("lyric_style_text_marquee_space", Defaults.MARQUEE_GHOST_SPACING)
        marqueeLoopDelay =
            preferences.getInt("lyric_style_text_marquee_loop_delay", Defaults.MARQUEE_LOOP_DELAY)
        marqueeInitialDelay = preferences.getInt(
            "lyric_style_text_marquee_initial_delay",
            Defaults.MARQUEE_INITIAL_DELAY
        )
        marqueeRepeatCount = preferences.getInt(
            "lyric_style_text_marquee_repeat_count",
            Defaults.MARQUEE_REPEAT_COUNT
        )
        marqueeStopAtEnd = preferences.getBoolean(
            "lyric_style_text_marquee_stop_at_end",
            Defaults.MARQUEE_STOP_AT_END
        )
        marqueeRepeatUnlimited = preferences.getBoolean(
            "lyric_style_text_marquee_repeat_unlimited",
            Defaults.MARQUEE_REPEAT_UNLIMITED
        )
        gradientProgressStyle = preferences.getBoolean(
            "lyric_style_text_gradient_progress_style",
            Defaults.ENABLE_GRADIENT_PROGRESS_STYLE
        )

        relativeProgress = preferences.getBoolean(
            "lyric_style_text_relative_progress",
            Defaults.RELATIVE_PROGRESS
        )
        relativeProgressHighlight = preferences.getBoolean(
            "lyric_style_text_relative_progress_highlight",
            Defaults.RELATIVE_PROGRESS_HIGHLIGHT
        )
        wordMotionEnabled = preferences.getBoolean(
            KEY_WORD_MOTION_ENABLED,
            Defaults.WORD_MOTION_ENABLED
        )
        wordMotionCjkLiftFactor = preferences.getFloat(
            KEY_WORD_MOTION_CJK_LIFT_FACTOR,
            Defaults.WORD_MOTION_CJK_LIFT_FACTOR
        )
        wordMotionCjkWaveFactor = preferences.getFloat(
            KEY_WORD_MOTION_CJK_WAVE_FACTOR,
            Defaults.WORD_MOTION_CJK_WAVE_FACTOR
        )
        wordMotionLatinLiftFactor = preferences.getFloat(
            KEY_WORD_MOTION_LATIN_LIFT_FACTOR,
            Defaults.WORD_MOTION_LATIN_LIFT_FACTOR
        )
        wordMotionLatinWaveFactor = preferences.getFloat(
            KEY_WORD_MOTION_LATIN_WAVE_FACTOR,
            Defaults.WORD_MOTION_LATIN_WAVE_FACTOR
        )
        scaleInMultiLine = preferences.getFloat(
            "lyric_style_text_size_ratio_in_multi_line_mode",
            Defaults.TEXT_SIZE_RATIO_IN_MULTI_LINE
        )

        transitionConfig = preferences.getString(
            "lyric_style_text_transition_config",
            Defaults.TRANSITION_CONFIG
        )
        placeholderFormat = preferences.getString(
            "lyric_style_text_placeholder_format",
            Defaults.PLACEHOLDER_FORMAT
        )

        isDisableTranslation = preferences.getBoolean(
            KEY_TEXT_TRANSLATION_DISABLE,
            Defaults.TRANSLATION_DISABLE
        )
        isTranslationOnly = preferences.getBoolean(
            KEY_TEXT_TRANSLATION_ONLY,
            Defaults.TRANSLATION_ONLY
        )

        isAiTranslationEnable =
            preferences.getBoolean(KEY_AI_TRANSLATION_ENABLED, Defaults.AI_TRANSLATION_ENABLED)
        aiTranslationConfigs = getAiTranslationConfigs(preferences)
        isAiTranslationAutoIgnoreChinese =
            preferences.getBoolean(
                KEY_AI_TRANSLATION_IGNORE_CHINESE,
                Defaults.AI_TRANSLATION_IGNORE_CHINESE
            )
    }

    fun writeToPreferences(editor: SharedPreferences.Editor) {
        editor.putFloat("lyric_style_text_size", textSize)
        editor.putBoolean("lyric_style_text_repeat_output", repeatOutput)

        editor.putString("lyric_style_text_margins", margins.toJson())
        editor.putString("lyric_style_text_paddings", paddings.toJson())

        editor.putBoolean("lyric_style_text_enable_custom_color", enableCustomTextColor)
        editor.putBoolean("lyric_style_text_extract_cover_color", enableExtractCoverTextColor)
        editor.putBoolean(
            "lyric_style_text_extract_cover_gradient",
            enableExtractCoverTextGradient
        )
        editor.putString(
            "lyric_style_text_rainbow_color_light_mode",
            lightModeRainbowColor.toJson()
        )
        editor.putString("lyric_style_text_rainbow_color_dark_mode", darkModeRainbowColor.toJson())

        editor.putInt("lyric_style_text_fading_edge_length", fadingEdgeLength)

        editor.putString("lyric_style_text_typeface", typeFace)
        editor.putBoolean("lyric_style_text_typeface_bold", typeFaceBold)
        editor.putBoolean("lyric_style_text_typeface_italic", typeFaceItalic)
        editor.putInt("lyric_style_text_weight", fontWeight)

        editor.putFloat("lyric_style_text_marquee_speed", marqueeSpeed)
        editor.putFloat("lyric_style_text_marquee_space", marqueeGhostSpacing)
        editor.putInt("lyric_style_text_marquee_loop_delay", marqueeLoopDelay)
        editor.putInt("lyric_style_text_marquee_initial_delay", marqueeInitialDelay)
        editor.putInt("lyric_style_text_marquee_repeat_count", marqueeRepeatCount)
        editor.putBoolean("lyric_style_text_marquee_stop_at_end", marqueeStopAtEnd)
        editor.putBoolean("lyric_style_text_marquee_repeat_unlimited", marqueeRepeatUnlimited)

        editor.putBoolean("lyric_style_text_gradient_progress_style", gradientProgressStyle)

        editor.putBoolean("lyric_style_text_relative_progress", relativeProgress)
        editor.putBoolean(
            "lyric_style_text_relative_progress_highlight",
            relativeProgressHighlight
        )
        editor.putBoolean(KEY_WORD_MOTION_ENABLED, wordMotionEnabled)
        editor.putFloat(KEY_WORD_MOTION_CJK_LIFT_FACTOR, wordMotionCjkLiftFactor)
        editor.putFloat(KEY_WORD_MOTION_CJK_WAVE_FACTOR, wordMotionCjkWaveFactor)
        editor.putFloat(KEY_WORD_MOTION_LATIN_LIFT_FACTOR, wordMotionLatinLiftFactor)
        editor.putFloat(KEY_WORD_MOTION_LATIN_WAVE_FACTOR, wordMotionLatinWaveFactor)
        editor.putFloat(
            "lyric_style_text_size_ratio_in_multi_line_mode",
            scaleInMultiLine
        )

        editor.putString(
            "lyric_style_text_transition_config",
            transitionConfig
        )
        editor.putString(
            "lyric_style_text_placeholder_format",
            placeholderFormat
        )

        editor.putBoolean(
            KEY_TEXT_TRANSLATION_DISABLE,
            isDisableTranslation
        )
        editor.putBoolean(KEY_TEXT_TRANSLATION_ONLY, isTranslationOnly)

        editor.putBoolean(
            KEY_AI_TRANSLATION_ENABLED,
            isAiTranslationEnable
        )
        aiTranslationConfigs?.let { writeAiTranslationConfigs(editor, it) }
        editor.putBoolean(
            KEY_AI_TRANSLATION_IGNORE_CHINESE,
            isAiTranslationAutoIgnoreChinese
        )
    }

    private fun getAiTranslationConfigs(preferences: SharedPreferences): AiTranslationConfigs {
        val providerName =
            preferences.getString(KEY_AI_TRANSLATION_PROVIDER, Defaults.AI_TRANSLATION_PROVIDER)
        val provider = AiTranslationProvider.entries.firstOrNull {
            it.name.equals(providerName, ignoreCase = true)
        }

        val model = preferences.getString(KEY_AI_TRANSLATION_MODEL, provider?.model)
        val baseUrl = preferences.getString(KEY_AI_TRANSLATION_BASE_URL, provider?.url)

        val customPrompt =
            preferences.getString(
                KEY_AI_TRANSLATION_PROMPT,
                Defaults.AI_TRANSLATION_PROMPT
            )

        val targetLanguage =
            preferences.getString(
                KEY_AI_TRANSLATION_TARGET_LANGUAGE,
                Defaults.AI_TRANSLATION_TARGET_LANGUAGE_DISPLAY_NAME
            )

        val apiKey = preferences.getString(KEY_AI_TRANSLATION_API_KEY, null)
        val temperature = preferences.getFloatCompat(
            KEY_AI_TRANSLATION_TEMPERATURE,
            Defaults.AI_TRANSLATION_TEMPERATURE
        )
        val topP = preferences.getFloatCompat(
            KEY_AI_TRANSLATION_TOP_P,
            Defaults.AI_TRANSLATION_TOP_P
        )
        val maxTokens = preferences.getIntCompat(
            KEY_AI_TRANSLATION_MAX_TOKENS,
            Defaults.AI_TRANSLATION_MAX_TOKENS
        )
        val presencePenalty = preferences.getFloatCompat(
            KEY_AI_TRANSLATION_PRESENCE_PENALTY,
            Defaults.AI_TRANSLATION_PRESENCE_PENALTY
        )
        val frequencyPenalty = preferences.getFloatCompat(
            KEY_AI_TRANSLATION_FREQUENCY_PENALTY,
            Defaults.AI_TRANSLATION_FREQUENCY_PENALTY
        )

        return AiTranslationConfigs(
            provider = provider?.name,
            targetLanguage = targetLanguage,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl,
            prompt = customPrompt ?: Defaults.AI_TRANSLATION_PROMPT,
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            presencePenalty = presencePenalty,
            frequencyPenalty = frequencyPenalty
        )
    }

    private fun writeAiTranslationConfigs(
        editor: SharedPreferences.Editor,
        configs: AiTranslationConfigs
    ) {
        editor.putString(KEY_AI_TRANSLATION_PROVIDER, configs.provider)
        editor.putString(KEY_AI_TRANSLATION_MODEL, configs.model)
        editor.putString(KEY_AI_TRANSLATION_BASE_URL, configs.baseUrl)
        editor.putString(KEY_AI_TRANSLATION_PROMPT, configs.prompt)
        editor.putString(KEY_AI_TRANSLATION_TARGET_LANGUAGE, configs.targetLanguage)
        editor.putString(KEY_AI_TRANSLATION_TEMPERATURE, configs.temperature.toString())
        editor.putString(KEY_AI_TRANSLATION_TOP_P, configs.topP.toString())
        editor.putString(KEY_AI_TRANSLATION_MAX_TOKENS, configs.maxTokens.toString())
        editor.putString(KEY_AI_TRANSLATION_PRESENCE_PENALTY, configs.presencePenalty.toString())
        editor.putString(KEY_AI_TRANSLATION_FREQUENCY_PENALTY, configs.frequencyPenalty.toString())
    }

    private fun SharedPreferences.getFloatCompat(key: String, defaultValue: Float): Float {
        return when (val value = all[key]) {
            is Float -> value
            is String -> value.toFloatOrNull() ?: defaultValue
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is Double -> value.toFloat()
            else -> defaultValue
        }
    }

    private fun SharedPreferences.getIntCompat(key: String, defaultValue: Int): Int {
        return when (val value = all[key]) {
            is Int -> value
            is String -> value.toIntOrNull() ?: defaultValue
            is Long -> value.toInt()
            is Float -> value.toInt()
            is Double -> value.toInt()
            else -> defaultValue
        }
    }
}
