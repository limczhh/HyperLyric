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
import com.lidesheng.hyperlyric.ui.component.FloatInputDialog
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs

@Composable
fun VerbatimLyricPage() {
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)

    var gradientStyle by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS)) }
    var syllableRelative by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE)) }
    var syllableHighlight by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT)) }
    var wordMotionEnabled by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED)) }
    var wordMotionCjkLift by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT)) }
    var wordMotionCjkWave by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE)) }
    var wordMotionLatinLift by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT)) }
    var wordMotionLatinWave by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE)) }

    var showWordMotionCjkLiftDialog by remember { mutableStateOf(false) }
    var showWordMotionCjkWaveDialog by remember { mutableStateOf(false) }
    var showWordMotionLatinLiftDialog by remember { mutableStateOf(false) }
    var showWordMotionLatinWaveDialog by remember { mutableStateOf(false) }

    FloatInputDialog(
        show = showWordMotionCjkLiftDialog,
        title = stringResource(id = R.string.title_word_motion_cjk_lift),
        label = stringResource(id = R.string.label_word_motion_lift_range),
        initialValue = wordMotionCjkLift,
        min = 0f,
        max = 0.2f,
        onDismiss = { showWordMotionCjkLiftDialog = false },
        onConfirm = { value ->
            wordMotionCjkLift = value
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, value)
        }
    )
    FloatInputDialog(
        show = showWordMotionCjkWaveDialog,
        title = stringResource(id = R.string.title_word_motion_cjk_wave),
        label = stringResource(id = R.string.label_word_motion_wave_range),
        initialValue = wordMotionCjkWave,
        min = 0f,
        max = 10f,
        onDismiss = { showWordMotionCjkWaveDialog = false },
        onConfirm = { value ->
            wordMotionCjkWave = value
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, value)
        }
    )
    FloatInputDialog(
        show = showWordMotionLatinLiftDialog,
        title = stringResource(id = R.string.title_word_motion_latin_lift),
        label = stringResource(id = R.string.label_word_motion_lift_range),
        initialValue = wordMotionLatinLift,
        min = 0f,
        max = 0.2f,
        onDismiss = { showWordMotionLatinLiftDialog = false },
        onConfirm = { value ->
            wordMotionLatinLift = value
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, value)
        }
    )
    FloatInputDialog(
        show = showWordMotionLatinWaveDialog,
        title = stringResource(id = R.string.title_word_motion_latin_wave),
        label = stringResource(id = R.string.label_word_motion_wave_range),
        initialValue = wordMotionLatinWave,
        min = 0f,
        max = 10f,
        onDismiss = { showWordMotionLatinWaveDialog = false },
        onConfirm = { value ->
            wordMotionLatinWave = value
            saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, value)
        }
    )

    XposedLyricSettingPage(title = stringResource(id = R.string.title_verbatim_lyric)) {
        verbatimLyricSections(
            gradientStyle = gradientStyle,
            onGradientStyleChange = {
                gradientStyle = it
                saveConfig(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, it)
            },
            syllableRelative = syllableRelative,
            onSyllableRelativeChange = {
                syllableRelative = it
                saveConfig(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, it)
            },
            syllableHighlight = syllableHighlight,
            onSyllableHighlightChange = {
                syllableHighlight = it
                saveConfig(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, it)
            },
            wordMotionEnabled = wordMotionEnabled,
            onWordMotionEnabledChange = {
                wordMotionEnabled = it
                saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, it)
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
