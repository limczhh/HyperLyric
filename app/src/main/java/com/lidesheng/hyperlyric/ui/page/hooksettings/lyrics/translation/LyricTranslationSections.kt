package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.translationSections(
    lyricSource: String,
    lyricMode: Int,
    disableTranslation: Boolean,
    onDisableTranslationChange: (Boolean) -> Unit,
    translationOnly: Boolean,
    onTranslationOnlyChange: (Boolean) -> Unit,
    swapTranslation: Boolean,
    onSwapTranslationChange: (Boolean) -> Unit,
    nextLyricLine: Boolean,
    onNextLyricLineChange: (Boolean) -> Unit,
    aiTransEnabled: Boolean,
    onAiTransEnabledChange: (Boolean) -> Unit,
    autoIgnoreChinese: Boolean,
    onAutoIgnoreChineseChange: (Boolean) -> Unit,
    skipExistingTranslation: Boolean,
    onSkipExistingTranslationChange: (Boolean) -> Unit,
    targetLang: String,
    onTargetLangClick: () -> Unit,
    apiKey: String,
    onApiKeyClick: () -> Unit,
    model: String,
    onModelClick: () -> Unit,
    baseUrl: String,
    onBaseUrlClick: () -> Unit,
    prompt: String,
    onPromptClick: () -> Unit
) {
    item(key = "translation") {
        val supportsNextLyricLine = (lyricSource == "lyricon" || lyricSource == "lyricinfo") && lyricMode == 0
        val translationControlsEnabled = !supportsNextLyricLine || !nextLyricLine
        val translationActionColor = if (translationControlsEnabled) {
            MiuixTheme.colorScheme.onSurfaceVariantActions
        } else {
            MiuixTheme.colorScheme.disabledOnSecondaryVariant
        }

        Column {
            SmallTitle(text = stringResource(id = R.string.title_translation))
            if (supportsNextLyricLine) {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(id = R.string.title_next_lyric_line),
                        summary = stringResource(id = R.string.summary_next_lyric_line),
                        checked = nextLyricLine,
                        onCheckedChange = onNextLyricLineChange
                    )
                }
            }
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        title = stringResource(id = R.string.title_disable_translation),
                        checked = disableTranslation,
                        onCheckedChange = onDisableTranslationChange,
                        enabled = translationControlsEnabled
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_translation_only),
                        checked = translationOnly,
                        onCheckedChange = onTranslationOnlyChange,
                        enabled = translationControlsEnabled
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_swap_translation),
                        checked = swapTranslation,
                        onCheckedChange = onSwapTranslationChange,
                        enabled = translationControlsEnabled
                    )
                }
            }
            if (lyricSource == "lyricon" || lyricSource == "lyricinfo") {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                    Column {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_ai_translation),
                            checked = aiTransEnabled,
                            onCheckedChange = onAiTransEnabledChange,
                            enabled = translationControlsEnabled
                        )
                        AnimatedVisibility(visible = aiTransEnabled) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(id = R.string.title_ai_trans_auto_ignore_chinese),
                                    checked = autoIgnoreChinese,
                                    onCheckedChange = onAutoIgnoreChineseChange,
                                    enabled = translationControlsEnabled
                                )
                                SwitchPreference(
                                    title = stringResource(id = R.string.title_ai_trans_skip_existing),
                                    checked = skipExistingTranslation,
                                    onCheckedChange = onSkipExistingTranslationChange,
                                    enabled = translationControlsEnabled
                                )
                                Column {
                                    ArrowPreference(
                                        title = stringResource(id = R.string.label_ai_trans_target_lang),
                                        endActions = {
                                            Text(
                                                targetLang,
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = translationActionColor
                                            )
                                        },
                                        onClick = onTargetLangClick,
                                        enabled = translationControlsEnabled
                                    )
                                    ArrowPreference(
                                        title = stringResource(id = R.string.label_ai_trans_api_key),
                                        endActions = {
                                            Text(
                                                if (apiKey.isNotEmpty()) "***************" else stringResource(id = R.string.summary_not_configured),
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = translationActionColor
                                            )
                                        },
                                        onClick = onApiKeyClick,
                                        enabled = translationControlsEnabled
                                    )
                                    ArrowPreference(
                                        title = stringResource(id = R.string.label_ai_trans_model),
                                        endActions = {
                                            Text(
                                                model,
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = translationActionColor
                                            )
                                        },
                                        onClick = onModelClick,
                                        enabled = translationControlsEnabled
                                    )
                                    ArrowPreference(
                                        title = stringResource(id = R.string.label_ai_trans_base_url),
                                        summary = baseUrl,
                                        onClick = onBaseUrlClick,
                                        enabled = translationControlsEnabled
                                    )
                                    ArrowPreference(
                                        title = stringResource(R.string.title_custom_prompt),
                                        summary = if (prompt.lines().size > 3) {
                                            prompt.lines().take(2).joinToString("\n") + "..."
                                        } else {
                                            prompt
                                        },
                                        onClick = onPromptClick,
                                        enabled = translationControlsEnabled
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
