package com.lidesheng.hyperlyric.ui.page.hooksettings.xposedLyricSettings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import top.yukonga.miuix.kmp.basic.ScrollBehavior

@Composable
fun LyricAdvancedTab(
    lazyListState: LazyListState,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    lyricSource: String,
    lyricMode: Int,
    gradientStyle: Boolean,
    onGradientStyleChange: (Boolean) -> Unit,
    syllableRelative: Boolean,
    onSyllableRelativeChange: (Boolean) -> Unit,
    syllableHighlight: Boolean,
    onSyllableHighlightChange: (Boolean) -> Unit,
    wordMotionEnabled: Boolean,
    onWordMotionEnabledChange: (Boolean) -> Unit,
    wordMotionCjkLift: Float,
    onWordMotionCjkLiftClick: () -> Unit,
    wordMotionCjkWave: Float,
    onWordMotionCjkWaveClick: () -> Unit,
    wordMotionLatinLift: Float,
    onWordMotionLatinLiftClick: () -> Unit,
    wordMotionLatinWave: Float,
    onWordMotionLatinWaveClick: () -> Unit,
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
    LazyColumn(
        state = lazyListState,
        modifier = Modifier.pageScrollModifiers(
            enableScrollEndHaptic = true,
            showTopAppBar = true,
            topAppBarScrollBehavior = topAppBarScrollBehavior
        ),
        contentPadding = contentPadding,
    ) {
        advancedSections(
            lyricSource = lyricSource,
            lyricMode = lyricMode,
            gradientStyle = gradientStyle,
            onGradientStyleChange = onGradientStyleChange,
            syllableRelative = syllableRelative,
            onSyllableRelativeChange = onSyllableRelativeChange,
            syllableHighlight = syllableHighlight,
            onSyllableHighlightChange = onSyllableHighlightChange,
            wordMotionEnabled = wordMotionEnabled,
            onWordMotionEnabledChange = onWordMotionEnabledChange,
            wordMotionCjkLift = wordMotionCjkLift,
            onWordMotionCjkLiftClick = onWordMotionCjkLiftClick,
            wordMotionCjkWave = wordMotionCjkWave,
            onWordMotionCjkWaveClick = onWordMotionCjkWaveClick,
            wordMotionLatinLift = wordMotionLatinLift,
            onWordMotionLatinLiftClick = onWordMotionLatinLiftClick,
            wordMotionLatinWave = wordMotionLatinWave,
            onWordMotionLatinWaveClick = onWordMotionLatinWaveClick,
            disableTranslation = disableTranslation,
            onDisableTranslationChange = onDisableTranslationChange,
            translationOnly = translationOnly,
            onTranslationOnlyChange = onTranslationOnlyChange,
            swapTranslation = swapTranslation,
            onSwapTranslationChange = onSwapTranslationChange,
            nextLyricLine = nextLyricLine,
            onNextLyricLineChange = onNextLyricLineChange,
            aiTransEnabled = aiTransEnabled,
            onAiTransEnabledChange = onAiTransEnabledChange,
            autoIgnoreChinese = autoIgnoreChinese,
            onAutoIgnoreChineseChange = onAutoIgnoreChineseChange,
            skipExistingTranslation = skipExistingTranslation,
            onSkipExistingTranslationChange = onSkipExistingTranslationChange,
            targetLang = targetLang,
            onTargetLangClick = onTargetLangClick,
            apiKey = apiKey,
            onApiKeyClick = onApiKeyClick,
            model = model,
            onModelClick = onModelClick,
            baseUrl = baseUrl,
            onBaseUrlClick = onBaseUrlClick,
            prompt = prompt,
            onPromptClick = onPromptClick
        )
    }
}
