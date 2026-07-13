package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.translation

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs

@Composable
fun LyricTranslationPage() {
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)

    val lyricMode by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)) }
    val lyricSource by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_LYRIC_SOURCE, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE) ?: "lyricon") }
    var disableTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION)) }
    var translationOnly by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_TRANSLATION_ONLY, RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY)) }
    var swapTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SWAP_TRANSLATION, RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION)) }
    var nextLyricLine by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_NEXT_LYRIC_LINE, RootConstants.DEFAULT_HOOK_NEXT_LYRIC_LINE)) }
    var aiTransEnabled by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE)) }
    var autoIgnoreChinese by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE, RootConstants.DEFAULT_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE)) }
    var skipExistingTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION, RootConstants.DEFAULT_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION)) }
    var apiKey by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_MODEL, RootConstants.DEFAULT_HOOK_AI_TRANS_MODEL) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_MODEL) }
    var baseUrl by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, RootConstants.DEFAULT_HOOK_AI_TRANS_BASE_URL) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_BASE_URL) }
    var targetLang by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG) }
    var prompt by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT) }

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showTargetLangDialog by remember { mutableStateOf(false) }
    var showPromptDialog by remember { mutableStateOf(false) }

    TextInputDialog(
        show = showApiKeyDialog,
        title = stringResource(id = R.string.label_ai_trans_api_key),
        initialValue = apiKey,
        onDismiss = { showApiKeyDialog = false },
        onConfirm = {
            apiKey = it
            saveConfig(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, it)
        }
    )
    TextInputDialog(
        show = showModelDialog,
        title = stringResource(id = R.string.label_ai_trans_model),
        initialValue = model,
        onDismiss = { showModelDialog = false },
        onConfirm = {
            model = it
            saveConfig(RootConstants.KEY_HOOK_AI_TRANS_MODEL, it)
        }
    )
    TextInputDialog(
        show = showBaseUrlDialog,
        title = stringResource(id = R.string.label_ai_trans_base_url),
        initialValue = baseUrl,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        onDismiss = { showBaseUrlDialog = false },
        onConfirm = {
            baseUrl = it
            saveConfig(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, it)
        }
    )
    TextInputDialog(
        show = showTargetLangDialog,
        title = stringResource(id = R.string.label_ai_trans_target_lang),
        initialValue = targetLang,
        onDismiss = { showTargetLangDialog = false },
        onConfirm = {
            targetLang = it
            saveConfig(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, it)
        }
    )
    TextInputDialog(
        show = showPromptDialog,
        title = stringResource(R.string.title_custom_prompt),
        initialValue = prompt,
        onDismiss = { showPromptDialog = false },
        onConfirm = {
            prompt = it
            saveConfig(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, it)
        }
    )

    XposedLyricSettingPage(title = stringResource(id = R.string.title_translation)) {
        translationSections(
            lyricSource = lyricSource,
            lyricMode = lyricMode,
            disableTranslation = disableTranslation,
            onDisableTranslationChange = {
                disableTranslation = it
                saveConfig(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, it)
            },
            translationOnly = translationOnly,
            onTranslationOnlyChange = {
                translationOnly = it
                saveConfig(RootConstants.KEY_HOOK_TRANSLATION_ONLY, it)
                if (it && swapTranslation) {
                    swapTranslation = false
                    saveConfig(RootConstants.KEY_HOOK_SWAP_TRANSLATION, false)
                }
            },
            swapTranslation = swapTranslation,
            onSwapTranslationChange = {
                swapTranslation = it
                saveConfig(RootConstants.KEY_HOOK_SWAP_TRANSLATION, it)
                if (it && translationOnly) {
                    translationOnly = false
                    saveConfig(RootConstants.KEY_HOOK_TRANSLATION_ONLY, false)
                }
            },
            nextLyricLine = nextLyricLine,
            onNextLyricLineChange = {
                nextLyricLine = it
                saveConfig(RootConstants.KEY_HOOK_NEXT_LYRIC_LINE, it)
            },
            aiTransEnabled = aiTransEnabled,
            onAiTransEnabledChange = {
                aiTransEnabled = it
                saveConfig(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, it)
            },
            autoIgnoreChinese = autoIgnoreChinese,
            onAutoIgnoreChineseChange = {
                autoIgnoreChinese = it
                saveConfig(RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE, it)
            },
            skipExistingTranslation = skipExistingTranslation,
            onSkipExistingTranslationChange = {
                skipExistingTranslation = it
                saveConfig(RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION, it)
            },
            targetLang = targetLang,
            onTargetLangClick = { showTargetLangDialog = true },
            apiKey = apiKey,
            onApiKeyClick = { showApiKeyDialog = true },
            model = model,
            onModelClick = { showModelDialog = true },
            baseUrl = baseUrl,
            onBaseUrlClick = { showBaseUrlDialog = true },
            prompt = prompt,
            onPromptClick = { showPromptDialog = true }
        )
    }
}
