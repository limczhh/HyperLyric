package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.AiTranslationLanguageSettings
import com.lidesheng.hyperlyric.common.LyricEnhancementConstants
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.getFloatCompat
import com.lidesheng.hyperlyric.ui.component.FloatInputDialog
import com.lidesheng.hyperlyric.ui.component.MultiSelectDialog
import com.lidesheng.hyperlyric.ui.component.MultiSelectDialogOption
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.utils.rememberExternalLinkOpener
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AI_KEY_ENABLED = RootConstants.KEY_HOOK_AI_TRANS_ENABLE
private const val AI_KEY_PROVIDER = RootConstants.KEY_HOOK_AI_TRANS_PROVIDER
private const val AI_KEY_SKIP_LANGUAGES = RootConstants.KEY_HOOK_AI_TRANS_SKIP_LANGUAGES
private const val AI_KEY_SKIP_LANGUAGES_ENABLED =
    RootConstants.KEY_HOOK_AI_TRANS_SKIP_LANGUAGES_ENABLED
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
private const val AMLL_KEY_API_BASE_URL = RootConstants.KEY_HOOK_AMLL_TTML_API_BASE_URL
private const val AMLL_KEY_DUET_PERFORMANCE = RootConstants.KEY_HOOK_AMLL_TTML_DUET_PERFORMANCE

private const val AMLL_READY_PLAYLIST_URL = "https://music.163.com/m/playlist?id=13303023713"
private const val AMLL_TTML_DB_URL = "https://github.com/amll-dev/amll-ttml-db"

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
    var provider by remember(prefs) {
        mutableStateOf(
            prefs.getString(
                AI_KEY_PROVIDER,
                RootConstants.DEFAULT_HOOK_AI_TRANS_PROVIDER
            ) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_PROVIDER
        )
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
        mutableFloatStateOf(getFloatCompat(prefs, AI_KEY_TEMPERATURE, 1f))
    }
    var topP by remember(prefs) {
        mutableFloatStateOf(getFloatCompat(prefs, AI_KEY_TOP_P, 1f))
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
            saveConfig(AI_KEY_SKIP_LANGUAGES_ENABLED, it.isNotEmpty())
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
    AiTranslationModelDialog(
        show = showModelDialog,
        provider = provider,
        currentModel = model,
        baseUrl = baseUrl,
        apiKey = apiKey,
        onDismiss = { showModelDialog = false },
        onConfirm = {
            model = it
            saveConfig(AI_KEY_MODEL, it)
            showModelDialog = false
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
        item(key = "ai_enable") {
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
        item(key = "ai_targets_and_filters") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
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
                }
            }
        }
        item(key = "ai_settings_title") {
            SmallTitle(text = stringResource(R.string.title_ai_settings))
        }
        item(key = "ai_api_settings") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    val providerOptions = listOf("OpenAI", "Anthropic")
                    val providerValues = listOf(
                        RootConstants.AI_TRANS_PROVIDER_OPENAI,
                        RootConstants.AI_TRANS_PROVIDER_ANTHROPIC
                    )
                    val selectedProviderIndex = if (provider.equals(RootConstants.AI_TRANS_PROVIDER_ANTHROPIC, ignoreCase = true)) 1 else 0
                    OverlayDropdownPreference(
                        title = stringResource(R.string.title_ai_translation_provider),
                        items = providerOptions,
                        selectedIndex = selectedProviderIndex,
                        onSelectedIndexChange = { index ->
                            val newProvider = providerValues.getOrElse(index) { RootConstants.AI_TRANS_PROVIDER_OPENAI }
                            provider = newProvider
                            saveConfig(AI_KEY_PROVIDER, newProvider)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_base_url),
                        summary = baseUrl,
                        onClick = { showBaseUrlDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_api_key),
                        endActions = {
                            Text(
                                text = apiKey.takeIf { it.isNotEmpty() }?.let { "***************" }
                                    ?: stringResource(R.string.summary_ai_translation_not_configured),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = { showApiKeyDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_model),
                        endActions = {
                            Text(
                                text = model,
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = { showModelDialog = true }
                    )
                }
            }
        }
        item(key = "ai_prompt_and_params") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_prompt),
                        summary = previewSettingValue(prompt, 2),
                        holdDownState = showPromptDialog,
                        onClick = { showPromptDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_temperature),
                        endActions = {
                            Text(
                                text = temperature.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        holdDownState = showTemperatureDialog,
                        onClick = { showTemperatureDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_top_p),
                        endActions = {
                            Text(
                                text = topP.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        holdDownState = showTopPDialog,
                        onClick = { showTopPDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_ai_translation_max_tokens),
                        endActions = {
                            Text(
                                text = maxTokens.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        holdDownState = showMaxTokensDialog,
                        onClick = { showMaxTokensDialog = true }
                    )
                }
            }
        }
        item(key = "ai_cache_title") {
            SmallTitle(text = stringResource(R.string.title_lyric_enhancement_cache))
        }
        item(key = "ai_cache") {
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
    val snackbarHostState = remember { SnackbarHostState() }
    val onOpenLink = rememberExternalLinkOpener(snackbarHostState)
    var enabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AMLL_KEY_ENABLED, false))
    }
    var platformProbe by remember(prefs) {
        mutableStateOf(prefs.getBoolean(AMLL_KEY_PLATFORM_PROBE, true))
    }
    var apiBaseUrl by remember(prefs) {
        mutableStateOf(
            prefs.getString(
                AMLL_KEY_API_BASE_URL,
                RootConstants.DEFAULT_HOOK_AMLL_TTML_API_BASE_URL
            ) ?: RootConstants.DEFAULT_HOOK_AMLL_TTML_API_BASE_URL
        )
    }
    var duetPerformance by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                AMLL_KEY_DUET_PERFORMANCE,
                RootConstants.DEFAULT_HOOK_AMLL_TTML_DUET_PERFORMANCE
            )
        )
    }
    var showApiBaseUrlDialog by remember { mutableStateOf(false) }

    XposedLyricSettingPage(title = stringResource(R.string.title_amll_ttml), snackbarHostState = snackbarHostState) {
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
                ArrowPreference(
                    title = stringResource(R.string.title_amll_api_base_url),
                    summary = apiBaseUrl,
                    onClick = { showApiBaseUrlDialog = true }
                )
                SwitchPreference(
                    title = stringResource(R.string.title_amll_duet_performance),
                    summary = stringResource(R.string.summary_amll_duet_performance),
                    enabled = enabled,
                    checked = duetPerformance,
                    onCheckedChange = {
                        duetPerformance = it
                        saveConfig(AMLL_KEY_DUET_PERFORMANCE, it)
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.title_amll_ready_playlist),
                    onClick = {
                        onOpenLink(AMLL_READY_PLAYLIST_URL)
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.title_amll_ttml_database),
                    onClick = {
                        onOpenLink(AMLL_TTML_DB_URL)
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
                    summary = stringResource(R.string.summary_amll_ttml_cache),
                    onClick = { navigator.navigate(Route.AmllTtmlCache) }
                )
            }
        }
    }

    TextInputDialog(
        show = showApiBaseUrlDialog,
        title = stringResource(R.string.title_amll_api_base_url),
        summary = stringResource(R.string.summary_amll_api_base_url),
        initialValue = apiBaseUrl,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        onDismiss = { showApiBaseUrlDialog = false },
        onConfirm = {
            apiBaseUrl = it.trim()
            saveConfig(AMLL_KEY_API_BASE_URL, apiBaseUrl)
        }
    )
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
