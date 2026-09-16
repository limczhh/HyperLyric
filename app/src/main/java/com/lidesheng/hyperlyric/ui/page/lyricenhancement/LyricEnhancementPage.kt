package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.AiTranslationLanguageSettings
import com.lidesheng.hyperlyric.common.LyricEnhancementConstants
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.ui.component.FloatInputDialog
import com.lidesheng.hyperlyric.ui.component.MultiSelectDialog
import com.lidesheng.hyperlyric.ui.component.MultiSelectDialogOption
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AI_KEY_ENABLED = RootConstants.KEY_HOOK_AI_TRANS_ENABLE
private const val AI_KEY_SKIP_LANGUAGES = RootConstants.KEY_HOOK_AI_TRANS_SKIP_LANGUAGES
private const val AI_KEY_SKIP_EXISTING = RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION
private const val AI_KEY_FORCE_OVERRIDE = RootConstants.KEY_HOOK_AI_TRANS_FORCE_OVERRIDE
private const val AI_KEY_TARGET_LANGUAGE = RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG
private const val AI_KEY_API_KEY = RootConstants.KEY_HOOK_AI_TRANS_API_KEY
private const val AI_KEY_MODEL = RootConstants.KEY_HOOK_AI_TRANS_MODEL
private const val AI_KEY_BASE_URL = RootConstants.KEY_HOOK_AI_TRANS_BASE_URL
private const val AI_KEY_PROMPT = RootConstants.KEY_HOOK_AI_TRANS_PROMPT
private const val AI_KEY_TEMPERATURE = RootConstants.KEY_HOOK_AI_TRANS_TEMPERATURE
private const val AI_KEY_TOP_P = RootConstants.KEY_HOOK_AI_TRANS_TOP_P
private const val AI_KEY_MAX_TOKENS = RootConstants.KEY_HOOK_AI_TRANS_MAX_TOKENS

private const val AMLL_KEY_ENABLED = RootConstants.KEY_HOOK_AMLL_TTML_ENABLE
private const val AMLL_KEY_PLATFORM_PROBE = RootConstants.KEY_HOOK_AMLL_TTML_PLATFORM_PROBE

@Composable
fun LyricEnhancementPage() {
    val navigator = LocalNavigator.current

    XposedLyricSettingPage(title = stringResource(R.string.title_lyric_enhancement)) {
        item(key = "openai_translation") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.title_openai_translation),
                    onClick = {
                        navigator.navigate(
                            Route.LyricEnhancementSettings(
                                LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID
                            )
                        )
                    }
                )
            }
        }
        item(key = "amll_ttml") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.title_amll_ttml),
                    summary = stringResource(R.string.summary_amll_ttml),
                    onClick = {
                        navigator.navigate(
                            Route.LyricEnhancementSettings(
                                LyricEnhancementConstants.AMLL_TTML_FEATURE_ID
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun LyricEnhancementSettingsPage(featureId: String) {
    when (featureId) {
        LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID -> OpenAiTranslationSettingsPage()
        LyricEnhancementConstants.AMLL_TTML_FEATURE_ID -> AmllTtmlSettingsPage()
        else -> LyricEnhancementUnavailablePage()
    }
}

@Composable
private fun OpenAiTranslationSettingsPage() {
    val prefs = rememberLyricEnhancementPrefs()
    val saveConfig = rememberLyricEnhancementConfigSaver(prefs)
    val navigator = LocalNavigator.current
    val defaultPrompt = RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT
    val defaultTargetLanguage = stringResource(R.string.default_ai_translation_language)
    val defaultBaseUrl = stringResource(R.string.default_ai_translation_base_url)
    val defaultModel = stringResource(R.string.default_ai_translation_model)

    var enabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AI_KEY_ENABLED, false))
    }
    var skipLanguages by remember(prefs) {
        mutableStateOf(
            AiTranslationLanguageSettings.getSkipLanguages(prefs)
        )
    }
    var skipExisting by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AI_KEY_SKIP_EXISTING, false))
    }
    var forceOverride by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AI_KEY_FORCE_OVERRIDE, false))
    }
    var targetLanguage by remember(prefs, defaultTargetLanguage) {
        mutableStateOf(
            prefs.getString(AI_KEY_TARGET_LANGUAGE, defaultTargetLanguage)
                ?: defaultTargetLanguage
        )
    }
    var apiKey by remember(prefs) {
        mutableStateOf(prefs.getString(AI_KEY_API_KEY, "").orEmpty())
    }
    var model by remember(prefs, defaultModel) {
        mutableStateOf(prefs.getString(AI_KEY_MODEL, defaultModel) ?: defaultModel)
    }
    var baseUrl by remember(prefs, defaultBaseUrl) {
        mutableStateOf(prefs.getString(AI_KEY_BASE_URL, defaultBaseUrl) ?: defaultBaseUrl)
    }
    var prompt by remember(prefs, defaultPrompt) {
        mutableStateOf(prefs.getString(AI_KEY_PROMPT, defaultPrompt) ?: defaultPrompt)
    }
    var temperature by remember(prefs) {
        mutableFloatStateOf(prefs.getFloat(AI_KEY_TEMPERATURE, 1f))
    }
    var topP by remember(prefs) {
        mutableFloatStateOf(prefs.getFloat(AI_KEY_TOP_P, 1f))
    }
    var maxTokens by remember(prefs) {
        mutableIntStateOf(
            prefs.getLong(AI_KEY_MAX_TOKENS, 0L)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        )
    }

    var showSkipLanguagesDialog by remember { mutableStateOf(false) }
    var showTargetLanguageDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }
    var showTemperatureDialog by remember { mutableStateOf(false) }
    var showTopPDialog by remember { mutableStateOf(false) }
    var showMaxTokensDialog by remember { mutableStateOf(false) }

    val languageOptions = listOf(
        AiTranslationLanguageSettings.LANGUAGE_CHINESE to
                stringResource(R.string.option_ai_translation_language_chinese),
        AiTranslationLanguageSettings.LANGUAGE_ENGLISH to
                stringResource(R.string.option_ai_translation_language_english),
        AiTranslationLanguageSettings.LANGUAGE_JAPANESE to
                stringResource(R.string.option_ai_translation_language_japanese),
        AiTranslationLanguageSettings.LANGUAGE_KOREAN to
                stringResource(R.string.option_ai_translation_language_korean),
        AiTranslationLanguageSettings.LANGUAGE_SPANISH to
                stringResource(R.string.option_ai_translation_language_spanish)
    )
    val skipLanguagesSummary = languageOptions
        .filter { it.first in skipLanguages }
        .joinToString(", ") { it.second }
        .ifBlank { stringResource(R.string.summary_ai_translation_skip_languages_none) }

    MultiSelectDialog(
        show = showSkipLanguagesDialog,
        title = stringResource(R.string.title_ai_translation_skip_languages),
        summary = stringResource(R.string.summary_ai_translation_skip_languages),
        options = languageOptions.map { (key, title) ->
            MultiSelectDialogOption(key, title)
        },
        selectedKeys = skipLanguages,
        onDismiss = { showSkipLanguagesDialog = false },
        onConfirm = {
            skipLanguages = it
            saveConfig(AI_KEY_SKIP_LANGUAGES, it)
        }
    )
    TextInputDialog(
        show = showTargetLanguageDialog,
        title = stringResource(R.string.title_ai_translation_target_language),
        initialValue = targetLanguage,
        onDismiss = { showTargetLanguageDialog = false },
        onConfirm = {
            targetLanguage = it
            saveConfig(AI_KEY_TARGET_LANGUAGE, it)
        }
    )
    TextInputDialog(
        show = showApiKeyDialog,
        title = stringResource(R.string.title_ai_translation_api_key),
        initialValue = apiKey,
        onDismiss = { showApiKeyDialog = false },
        onConfirm = {
            apiKey = it
            saveConfig(AI_KEY_API_KEY, it)
        }
    )
    TextInputDialog(
        show = showModelDialog,
        title = stringResource(R.string.title_ai_translation_model),
        initialValue = model,
        onDismiss = { showModelDialog = false },
        onConfirm = {
            model = it
            saveConfig(AI_KEY_MODEL, it)
        }
    )
    TextInputDialog(
        show = showBaseUrlDialog,
        title = stringResource(R.string.title_ai_translation_base_url),
        initialValue = baseUrl,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
        ),
        onDismiss = { showBaseUrlDialog = false },
        onConfirm = {
            baseUrl = it
            saveConfig(AI_KEY_BASE_URL, it)
        }
    )
    TextInputDialog(
        show = showPromptDialog,
        title = stringResource(R.string.title_ai_translation_prompt),
        initialValue = prompt,
        onDismiss = { showPromptDialog = false },
        onConfirm = {
            prompt = it
            saveConfig(AI_KEY_PROMPT, it)
        }
    )
    FloatInputDialog(
        show = showTemperatureDialog,
        title = stringResource(R.string.title_ai_translation_temperature),
        label = stringResource(R.string.label_ai_translation_temperature),
        summary = stringResource(R.string.summary_ai_translation_temperature),
        initialValue = temperature,
        min = 0f,
        max = 2f,
        onDismiss = { showTemperatureDialog = false },
        onConfirm = {
            temperature = it
            saveConfig(AI_KEY_TEMPERATURE, it)
        }
    )
    FloatInputDialog(
        show = showTopPDialog,
        title = stringResource(R.string.title_ai_translation_top_p),
        label = stringResource(R.string.label_ai_translation_top_p),
        summary = stringResource(R.string.summary_ai_translation_top_p),
        initialValue = topP,
        min = 0f,
        max = 1f,
        onDismiss = { showTopPDialog = false },
        onConfirm = {
            topP = it
            saveConfig(AI_KEY_TOP_P, it)
        }
    )
    NumberInputDialog(
        show = showMaxTokensDialog,
        title = stringResource(R.string.title_ai_translation_max_tokens),
        label = stringResource(R.string.label_ai_translation_max_tokens),
        summary = stringResource(R.string.summary_ai_translation_max_tokens),
        initialValue = maxTokens,
        min = 0,
        max = Int.MAX_VALUE,
        onDismiss = { showMaxTokensDialog = false },
        onConfirm = {
            maxTokens = it
            saveConfig(AI_KEY_MAX_TOKENS, it.toLong())
        }
    )

    XposedLyricSettingPage(title = stringResource(R.string.title_openai_translation)) {
        item(key = "openai_enable") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.title_enable),
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        saveConfig(AI_KEY_ENABLED, it)
                    }
                )
            }
        }
        item(key = "openai_basic_title") {
            SmallTitle(text = stringResource(R.string.title_lyric_enhancement_basic_settings))
        }
        item(key = "openai_basic_settings") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_skip_languages),
                        summary = skipLanguagesSummary,
                        enabled = enabled,
                        holdDownState = showSkipLanguagesDialog,
                        onClick = { showSkipLanguagesDialog = true }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.title_ai_translation_skip_existing),
                        enabled = enabled,
                        checked = skipExisting,
                        onCheckedChange = {
                            skipExisting = it
                            if (it) {
                                forceOverride = false
                                saveConfig(AI_KEY_FORCE_OVERRIDE, false)
                            }
                            saveConfig(AI_KEY_SKIP_EXISTING, it)
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.title_ai_translation_force_override),
                        enabled = enabled,
                        checked = forceOverride,
                        onCheckedChange = {
                            forceOverride = it
                            if (it) {
                                skipExisting = false
                                saveConfig(AI_KEY_SKIP_EXISTING, false)
                            }
                            saveConfig(AI_KEY_FORCE_OVERRIDE, it)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_target_language),
                        endActions = {
                            Text(
                                text = targetLanguage,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        },
                        enabled = enabled,
                        onClick = { showTargetLanguageDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_api_key),
                        endActions = {
                            Text(
                                text = apiKey.takeIf { it.isNotEmpty() }?.let { "***************" }
                                    ?: stringResource(R.string.summary_ai_translation_not_configured),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        },
                        enabled = enabled,
                        onClick = { showApiKeyDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_model),
                        endActions = {
                            Text(
                                text = model,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        },
                        enabled = enabled,
                        onClick = { showModelDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_base_url),
                        summary = baseUrl,
                        enabled = enabled,
                        onClick = { showBaseUrlDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_prompt),
                        summary = previewSettingValue(prompt, 2),
                        enabled = enabled,
                        holdDownState = showPromptDialog,
                        onClick = { showPromptDialog = true }
                    )
                }
            }
        }
        item(key = "openai_advanced_title") {
            SmallTitle(text = stringResource(R.string.title_lyric_enhancement_advanced_settings))
        }
        item(key = "openai_advanced_settings") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_temperature),
                        endActions = {
                            Text(
                                text = temperature.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        },
                        enabled = enabled,
                        holdDownState = showTemperatureDialog,
                        onClick = { showTemperatureDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_top_p),
                        endActions = {
                            Text(
                                text = topP.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        },
                        enabled = enabled,
                        holdDownState = showTopPDialog,
                        onClick = { showTopPDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_max_tokens),
                        endActions = {
                            Text(
                                text = maxTokens.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = if (enabled) {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                } else {
                                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                }
                            )
                        },
                        enabled = enabled,
                        holdDownState = showMaxTokensDialog,
                        onClick = { showMaxTokensDialog = true }
                    )
                }
            }
        }
        item(key = "openai_cache_title") {
            SmallTitle(text = stringResource(R.string.title_lyric_enhancement_cache))
        }
        item(key = "openai_cache") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.title_lyric_enhancement_cache_manage),
                    summary = stringResource(R.string.summary_lyric_enhancement_cache_manage),
                    onClick = { navigator.navigate(Route.AiTranslationCache) }
                )
            }
        }
    }
}

@Composable
private fun AmllTtmlSettingsPage() {
    val prefs = rememberLyricEnhancementPrefs()
    val saveConfig = rememberLyricEnhancementConfigSaver(prefs)
    val navigator = LocalNavigator.current
    var enabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AMLL_KEY_ENABLED, false))
    }
    var platformProbe by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AMLL_KEY_PLATFORM_PROBE, true))
    }

    XposedLyricSettingPage(title = stringResource(R.string.title_amll_ttml)) {
        item(key = "amll_enable") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.title_enable),
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        saveConfig(AMLL_KEY_ENABLED, it)
                    }
                )
            }
        }
        item(key = "amll_settings") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    title = stringResource(R.string.title_amll_platform_probe),
                    summary = stringResource(R.string.summary_amll_platform_probe),
                    enabled = enabled,
                    checked = platformProbe,
                    onCheckedChange = {
                        platformProbe = it
                        saveConfig(AMLL_KEY_PLATFORM_PROBE, it)
                    }
                )
            }
        }
        item(key = "amll_cache_title") {
            SmallTitle(text = stringResource(R.string.title_lyric_enhancement_cache))
        }
        item(key = "amll_cache") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = stringResource(R.string.title_lyric_enhancement_cache_manage),
                    summary = stringResource(R.string.summary_lyric_enhancement_cache_manage),
                    onClick = { navigator.navigate(Route.AmllTtmlCache) }
                )
            }
        }
    }
}

@Composable
private fun LyricEnhancementUnavailablePage() {
    XposedLyricSettingPage(title = stringResource(R.string.title_lyric_enhancement)) {
        item(key = "lyric_enhancement_unavailable") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.summary_lyric_enhancement_unavailable),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private fun previewSettingValue(value: String, lineCount: Int): String {
    val lines = value.lines()
    return if (lines.size > lineCount + 1) {
        lines.take(lineCount).joinToString("\n") + "..."
    } else {
        value
    }
}
