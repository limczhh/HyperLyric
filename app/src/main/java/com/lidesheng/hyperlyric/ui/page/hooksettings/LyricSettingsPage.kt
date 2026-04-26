package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun LyricSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    
    var textSize by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_TEXT_SIZE, RootConstants.DEFAULT_HOOK_TEXT_SIZE)) }
    var fontWeight by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_FONT_WEIGHT, RootConstants.DEFAULT_HOOK_FONT_WEIGHT)) }
    var fontBold by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_FONT_BOLD, RootConstants.DEFAULT_HOOK_FONT_BOLD)) }
    var fontItalic by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_FONT_ITALIC, RootConstants.DEFAULT_HOOK_FONT_ITALIC)) }
    var fadingEdge by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, RootConstants.DEFAULT_HOOK_FADING_EDGE_LENGTH)) }
    var gradientStyle by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, RootConstants.DEFAULT_HOOK_GRADIENT_PROGRESS)) }
    var marqueeMode by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_MODE)) }
    var marqueeSpeed by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_SPEED)) }
    var marqueeDelay by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_DELAY)) }
    var marqueeLoop by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_LOOP_DELAY)) }
    var marqueeInfinite by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_INFINITE)) }
    var marqueeStopEnd by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_STOP_END, RootConstants.DEFAULT_HOOK_MARQUEE_STOP_END)) }
    var syllableRelative by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE)) }
    var syllableHighlight by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT)) }
    var textSizeRatio by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO)) }

    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showFontWeightDialog by remember { mutableStateOf(false) }
    var showFadingEdgeDialog by remember { mutableStateOf(false) }
    var showMarqueeSpeedDialog by remember { mutableStateOf(false) }
    var showMarqueeDelayDialog by remember { mutableStateOf(false) }
    var showMarqueeLoopDialog by remember { mutableStateOf(false) }
    var showTextSizeRatioDialog by remember { mutableStateOf(false) }

    fun saveConfig(key: String, value: Any) {
        prefs.edit {
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is String -> putString(key, value)
            }
        }
        ConfigSync.syncPreference(UIConstants.PREF_NAME, key, value)
        val refreshKeys = setOf(
            RootConstants.KEY_HOOK_TEXT_SIZE,
            RootConstants.KEY_HOOK_FONT_WEIGHT,
            RootConstants.KEY_HOOK_FONT_BOLD,
            RootConstants.KEY_HOOK_FONT_ITALIC,
            RootConstants.KEY_HOOK_FADING_EDGE_LENGTH,
            RootConstants.KEY_HOOK_TEXT_SIZE_RATIO,
            RootConstants.KEY_HOOK_GRADIENT_PROGRESS,
            RootConstants.KEY_HOOK_MARQUEE_MODE,
            RootConstants.KEY_HOOK_MARQUEE_SPEED,
            RootConstants.KEY_HOOK_MARQUEE_DELAY,
            RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY,
            RootConstants.KEY_HOOK_MARQUEE_INFINITE,
            RootConstants.KEY_HOOK_MARQUEE_STOP_END,
            RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
            RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT
        )
        if (key in refreshKeys) {
            context.sendBroadcast(Intent("com.lidesheng.hyperlyric.UPDATE_LYRIC_ANIM"))
        }
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(backgroundColor = MiuixTheme.colorScheme.surface, tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f)))

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = stringResource(id = R.string.title_lyrics),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back)) }
                },
                modifier = Modifier.hazeEffect(hazeState) { style = hazeStyle; blurRadius = 25.dp; noiseFactor = 0f }
            )
        }
    ) { padding ->
        NumberInputDialog(show = showTextSizeDialog, title = stringResource(id = R.string.title_size), label = stringResource(id = R.string.label_size_range), initialValue = textSize, min = 8, max = 16, onDismiss = { showTextSizeDialog = false }, onConfirm = { value -> textSize = value; saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE, value) })
        NumberInputDialog(show = showFontWeightDialog, title = stringResource(id = R.string.title_font_weight), label = stringResource(id = R.string.label_font_weight_range), initialValue = fontWeight, min = 100, max = 900, onDismiss = { showFontWeightDialog = false }, onConfirm = { value -> fontWeight = value; saveConfig(RootConstants.KEY_HOOK_FONT_WEIGHT, value) })
        NumberInputDialog(show = showFadingEdgeDialog, title = stringResource(id = R.string.title_fading_edge), label = stringResource(id = R.string.label_fading_edge_range), initialValue = fadingEdge, min = 0, max = 100, onDismiss = { showFadingEdgeDialog = false }, onConfirm = { value -> fadingEdge = value; saveConfig(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, value) })
        NumberInputDialog(show = showMarqueeSpeedDialog, title = stringResource(id = R.string.title_marquee_speed), label = stringResource(id = R.string.label_marquee_speed_range), initialValue = marqueeSpeed, min = 10, max = 500, onDismiss = { showMarqueeSpeedDialog = false }, onConfirm = { value -> marqueeSpeed = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_SPEED, value) })
        NumberInputDialog(show = showMarqueeDelayDialog, title = stringResource(id = R.string.title_marquee_delay), label = stringResource(id = R.string.label_marquee_delay_range), initialValue = marqueeDelay, min = 0, max = 5000, onDismiss = { showMarqueeDelayDialog = false }, onConfirm = { value -> marqueeDelay = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_DELAY, value) })
        NumberInputDialog(show = showMarqueeLoopDialog, title = stringResource(id = R.string.title_marquee_loop), label = stringResource(id = R.string.label_marquee_loop_range), initialValue = marqueeLoop, min = 0, max = 5000, onDismiss = { showMarqueeLoopDialog = false }, onConfirm = { value -> marqueeLoop = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, value) })
        NumberInputDialog(show = showTextSizeRatioDialog, title = stringResource(id = R.string.title_text_size_ratio), label = stringResource(id = R.string.label_text_size_ratio_range), initialValue = (textSizeRatio * 100).toInt(), min = 10, max = 100, onDismiss = { showTextSizeRatioDialog = false }, onConfirm = { value -> textSizeRatio = value.toFloat() / 100f; saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, textSizeRatio) })

        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollEndHaptic().hazeSource(state = hazeState).overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding())
        ) {
            item {
                Column {
                    SmallTitle(text = stringResource(id = R.string.title_text), insideMargin = PaddingValues(10.dp, 4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ArrowPreference(title = stringResource(id = R.string.title_size), endActions = { Text("$textSize", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showTextSizeDialog = true }, bottomAction = { Slider(value = textSize.toFloat(), onValueChange = { textSize = it.toInt(); saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE, textSize) }, steps = 8, hapticEffect = SliderDefaults.SliderHapticEffect.Step, valueRange = 8f..16f) })
                            ArrowPreference(title = stringResource(id = R.string.title_font_weight), endActions = { Text(fontWeight.toString(), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showFontWeightDialog = true }, bottomAction = { Slider(value = fontWeight.toFloat(), onValueChange = { fontWeight = it.toInt(); saveConfig(RootConstants.KEY_HOOK_FONT_WEIGHT, fontWeight) }, hapticEffect = SliderDefaults.SliderHapticEffect.Step, keyPoints = listOf(100f, 200f, 300f, 400f, 500f, 600f, 700f, 800f, 900f), valueRange = 100f..900f) })
                            SwitchPreference(title = stringResource(id = R.string.title_bold), checked = fontBold, onCheckedChange = { fontBold = it; saveConfig(RootConstants.KEY_HOOK_FONT_BOLD, it) })
                            SwitchPreference(title = stringResource(id = R.string.title_italic), checked = fontItalic, onCheckedChange = { fontItalic = it; saveConfig(RootConstants.KEY_HOOK_FONT_ITALIC, it) })
                            ArrowPreference(title = stringResource(id = R.string.title_text_size_ratio), endActions = { Text(stringResource(id = R.string.format_percent, (textSizeRatio * 100).toInt()), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showTextSizeRatioDialog = true }, bottomAction = { Slider(value = textSizeRatio, onValueChange = { textSizeRatio = it; saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, textSizeRatio) }, valueRange = 0.1f..1f) })
                            ArrowPreference(title = stringResource(id = R.string.title_fading_edge), endActions = { Text("$fadingEdge", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showFadingEdgeDialog = true }, bottomAction = { Slider(value = fadingEdge.toFloat(), onValueChange = { fadingEdge = it.toInt(); saveConfig(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, fadingEdge) }, valueRange = 0f..100f) })
                            SwitchPreference(title = stringResource(id = R.string.title_gradient_progress), checked = gradientStyle, onCheckedChange = { gradientStyle = it; saveConfig(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, it) })
                        }
                    }
                }
            }
            item {
                Column {
                    SmallTitle(text = stringResource(id = R.string.title_lyric_marquee), insideMargin = PaddingValues(10.dp, 4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwitchPreference(title = stringResource(id = R.string.title_lyric_marquee), checked = marqueeMode, onCheckedChange = { marqueeMode = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_MODE, it) })
                            AnimatedVisibility(visible = marqueeMode) {
                                Column {
                                    ArrowPreference(title = stringResource(id = R.string.title_marquee_speed), endActions = { Text("$marqueeSpeed", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showMarqueeSpeedDialog = true }, bottomAction = { Slider(value = marqueeSpeed.toFloat(), onValueChange = { marqueeSpeed = it.toInt(); saveConfig(RootConstants.KEY_HOOK_MARQUEE_SPEED, marqueeSpeed) }, valueRange = 10f..500f) })
                                    ArrowPreference(title = stringResource(id = R.string.title_marquee_delay), endActions = { Text(stringResource(id = R.string.format_ms, marqueeDelay), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showMarqueeDelayDialog = true }, bottomAction = { Slider(value = marqueeDelay.toFloat(), onValueChange = { marqueeDelay = it.toInt(); saveConfig(RootConstants.KEY_HOOK_MARQUEE_DELAY, marqueeDelay) }, valueRange = 0f..5000f) })
                                    SwitchPreference(title = stringResource(id = R.string.title_infinite_loop), checked = marqueeInfinite, onCheckedChange = { marqueeInfinite = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_INFINITE, it) })
                                    ArrowPreference(title = stringResource(id = R.string.title_marquee_loop), endActions = { Text(stringResource(id = R.string.format_ms, marqueeLoop), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showMarqueeLoopDialog = true }, bottomAction = { Slider(value = marqueeLoop.toFloat(), onValueChange = { marqueeLoop = it.toInt(); saveConfig(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, marqueeLoop) }, valueRange = 0f..5000f) })
                                    SwitchPreference(title = stringResource(id = R.string.title_stop_at_end), checked = marqueeStopEnd, onCheckedChange = { marqueeStopEnd = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_STOP_END, it) })
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    SmallTitle(text = stringResource(id = R.string.lyric_mode_verbatim), insideMargin = PaddingValues(10.dp, 4.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            SwitchPreference(title = stringResource(id = R.string.title_syllable_relative), checked = syllableRelative, onCheckedChange = { syllableRelative = it; saveConfig(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, it) })
                            SwitchPreference(title = stringResource(id = R.string.title_syllable_highlight), checked = syllableHighlight, onCheckedChange = { syllableHighlight = it; saveConfig(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NumberInputDialog(show: Boolean, title: String, label: String, initialValue: Int, min: Int, max: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    if (!show) return
    var inputValue by remember { mutableStateOf(initialValue.toString()) }

    WindowDialog(title = title, show = true, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(value = inputValue, onValueChange = { newValue -> if (newValue.all { it.isDigit() }) inputValue = newValue }, label = label, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), maxLines = 1)
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = stringResource(id = R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = stringResource(id = R.string.confirm), onClick = { inputValue.toIntOrNull()?.let { onConfirm(it.coerceIn(min, max)); onDismiss() } }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}
