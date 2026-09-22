package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.verbatim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.SyllablePreferencePolicy
import com.lidesheng.hyperlyric.common.WordMotionPreferencePolicy
import com.lidesheng.hyperlyric.ui.component.FloatInputDialog
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs

@Composable
fun VerbatimLyricPage() {
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)
    val syllableSettings = remember(prefs) { SyllablePreferencePolicy.read(prefs) }
    val wordMotionSettings = remember(prefs) { WordMotionPreferencePolicy.read(prefs) }

    var gradientStyle by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
                RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS
            )
        )
    }
    var lineDisplay by remember {
        mutableStateOf(syllableSettings.lineDisplay)
    }
    var syllableRelative by remember {
        mutableStateOf(syllableSettings.relativeProgress)
    }
    var syllableHighlight by remember {
        mutableStateOf(syllableSettings.relativeHighlight)
    }
    var wordMotionEnabled by remember {
        mutableStateOf(wordMotionSettings.enabled)
    }
    var wordMotionLatinByCharacter by remember {
        mutableStateOf(wordMotionSettings.latinByCharacter)
    }
    var wordMotionCjkLift by remember {
        mutableFloatStateOf(wordMotionSettings.cjkLiftFactor)
    }
    var wordMotionCjkWave by remember {
        mutableFloatStateOf(wordMotionSettings.cjkWaveFactor)
    }
    var wordMotionLatinLift by remember {
        mutableFloatStateOf(wordMotionSettings.latinLiftFactor)
    }
    var wordMotionLatinWave by remember {
        mutableFloatStateOf(wordMotionSettings.latinWaveFactor)
    }

    var showWordMotionCjkLiftDialog by remember { mutableStateOf(false) }
    var showWordMotionCjkWaveDialog by remember { mutableStateOf(false) }
    var showWordMotionLatinLiftDialog by remember { mutableStateOf(false) }
    var showWordMotionLatinWaveDialog by remember { mutableStateOf(false) }

    fun updateSyllableSettings(
        relativeProgress: Boolean = syllableRelative,
        relativeHighlight: Boolean = syllableHighlight,
        lineDisplayValue: Boolean = lineDisplay
    ) {
        val state = SyllablePreferencePolicy.normalize(
            relativeProgress = relativeProgress,
            relativeHighlight = relativeHighlight,
            lineDisplay = lineDisplayValue
        )
        lineDisplay = state.lineDisplay
        syllableRelative = state.relativeProgress
        syllableHighlight = state.relativeHighlight
        saveConfig(RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY, state.lineDisplay)
        saveConfig(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, state.relativeProgress)
        saveConfig(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, state.relativeHighlight)
    }

    FloatInputDialog(
        show = showWordMotionCjkLiftDialog,
        title = stringResource(id = R.string.title_word_motion_cjk_lift),
        label = stringResource(id = R.string.label_word_motion_lift_range),
        initialValue = wordMotionCjkLift,
        min = RootConstants.MIN_HOOK_WORD_MOTION_LIFT,
        max = RootConstants.MAX_HOOK_WORD_MOTION_LIFT,
        onDismiss = { showWordMotionCjkLiftDialog = false },
        onConfirm = { value ->
            val normalized = WordMotionPreferencePolicy.normalizeLiftFactor(value)
            wordMotionCjkLift = normalized
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, normalized)
        }
    )
    FloatInputDialog(
        show = showWordMotionCjkWaveDialog,
        title = stringResource(id = R.string.title_word_motion_cjk_wave),
        label = stringResource(id = R.string.label_word_motion_wave_range),
        initialValue = wordMotionCjkWave,
        min = RootConstants.MIN_HOOK_WORD_MOTION_WAVE,
        max = RootConstants.MAX_HOOK_WORD_MOTION_WAVE,
        onDismiss = { showWordMotionCjkWaveDialog = false },
        onConfirm = { value ->
            val normalized = WordMotionPreferencePolicy.normalizeWaveFactor(value)
            wordMotionCjkWave = normalized
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, normalized)
        }
    )
    FloatInputDialog(
        show = showWordMotionLatinLiftDialog,
        title = stringResource(id = R.string.title_word_motion_latin_lift),
        label = stringResource(id = R.string.label_word_motion_lift_range),
        initialValue = wordMotionLatinLift,
        min = RootConstants.MIN_HOOK_WORD_MOTION_LIFT,
        max = RootConstants.MAX_HOOK_WORD_MOTION_LIFT,
        onDismiss = { showWordMotionLatinLiftDialog = false },
        onConfirm = { value ->
            val normalized = WordMotionPreferencePolicy.normalizeLiftFactor(value)
            wordMotionLatinLift = normalized
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, normalized)
        }
    )
    FloatInputDialog(
        show = showWordMotionLatinWaveDialog,
        title = stringResource(id = R.string.title_word_motion_latin_wave),
        label = stringResource(id = R.string.label_word_motion_wave_range),
        initialValue = wordMotionLatinWave,
        min = RootConstants.MIN_HOOK_WORD_MOTION_WAVE,
        max = RootConstants.MAX_HOOK_WORD_MOTION_WAVE,
        onDismiss = { showWordMotionLatinWaveDialog = false },
        onConfirm = { value ->
            val normalized = WordMotionPreferencePolicy.normalizeWaveFactor(value)
            wordMotionLatinWave = normalized
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, normalized)
        }
    )

    XposedLyricSettingPage(title = stringResource(id = R.string.title_verbatim_lyric)) {
        verbatimLyricSections(
            gradientStyle = gradientStyle,
            onGradientStyleChange = {
                gradientStyle = it
                saveConfig(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, it)
            },
            lineDisplay = lineDisplay,
            onLineDisplayChange = {
                updateSyllableSettings(lineDisplayValue = it)
            },
            syllableRelative = syllableRelative,
            onSyllableRelativeChange = {
                updateSyllableSettings(relativeProgress = it, lineDisplayValue = false)
            },
            syllableHighlight = syllableHighlight,
            onSyllableHighlightChange = {
                updateSyllableSettings(relativeHighlight = it)
            },
            wordMotionEnabled = wordMotionEnabled,
            onWordMotionEnabledChange = {
                wordMotionEnabled = it
                saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, it)
            },
            wordMotionLatinByCharacter = wordMotionLatinByCharacter,
            onWordMotionLatinByCharacterChange = {
                wordMotionLatinByCharacter = it
                saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_BY_CHARACTER, it)
            },
            wordMotionCjkLift = wordMotionCjkLift,
            onWordMotionCjkLiftClick = { showWordMotionCjkLiftDialog = true },
            wordMotionCjkWave = wordMotionCjkWave,
            onWordMotionCjkWaveClick = { showWordMotionCjkWaveDialog = true },
            wordMotionLatinLift = wordMotionLatinLift,
            onWordMotionLatinLiftClick = { showWordMotionLatinLiftDialog = true },
            wordMotionLatinWave = wordMotionLatinWave,
            onWordMotionLatinWaveClick = { showWordMotionLatinWaveDialog = true }
        )
    }
}
