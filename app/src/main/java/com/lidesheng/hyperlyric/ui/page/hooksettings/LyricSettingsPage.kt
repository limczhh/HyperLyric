package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import io.github.proify.lyricon.app.bridge.LyriconBridge
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.HorizontalDivider
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
    var disableTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION)) }
    var translationOnly by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_TRANSLATION_ONLY, RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY)) }
    var swapTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SWAP_TRANSLATION, RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION)) }
    var extractCoverColor by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR, RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_COLOR)) }
    var extractCoverGradient by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT, RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_GRADIENT)) }
    var customFontPath by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_CUSTOM_FONT_PATH, null) ?: "") }

    var aiTransEnabled by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE)) }
    var autoIgnoreChinese by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE, RootConstants.DEFAULT_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE)) }
    var apiKey by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_MODEL, "") ?: "") }
    var baseUrl by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, "") ?: "") }
    var targetLang by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, "") ?: "") }
    var prompt by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, "") ?: "") }

    var wordMotionEnabled by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED)) }
    var wordMotionCjkLift by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT)) }
    var wordMotionCjkWave by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE)) }
    var wordMotionLatinLift by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT)) }
    var wordMotionLatinWave by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE)) }

    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showFontWeightDialog by remember { mutableStateOf(false) }
    var showFadingEdgeDialog by remember { mutableStateOf(false) }
    var showMarqueeSpeedDialog by remember { mutableStateOf(false) }
    var showMarqueeDelayDialog by remember { mutableStateOf(false) }
    var showMarqueeLoopDialog by remember { mutableStateOf(false) }
    var showTextSizeRatioDialog by remember { mutableStateOf(false) }
    var showFontPathDialog by remember { mutableStateOf(false) }

    var showPromptDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showTargetLangDialog by remember { mutableStateOf(false) }

    var showWordMotionCjkLiftDialog by remember { mutableStateOf(false) }
    var showWordMotionCjkWaveDialog by remember { mutableStateOf(false) }
    var showWordMotionLatinLiftDialog by remember { mutableStateOf(false) }
    var showWordMotionLatinWaveDialog by remember { mutableStateOf(false) }

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
            RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
            RootConstants.KEY_HOOK_DISABLE_TRANSLATION,
            RootConstants.KEY_HOOK_TRANSLATION_ONLY,
            RootConstants.KEY_HOOK_SWAP_TRANSLATION,
            RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR,
            RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT,
            RootConstants.KEY_HOOK_CUSTOM_FONT_PATH,
            RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE
        )
        if (key in refreshKeys) {
            LyriconBridge.with(context).key("com.lidesheng.hyperlyric.UPDATE_LYRIC_ANIM").to("com.android.systemui").send()
        }
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(backgroundColor = MiuixTheme.colorScheme.surface, tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f)))

    val tabs = listOf(stringResource(R.string.tab_basic), stringResource(R.string.tab_advanced))
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 25.dp
                    noiseFactor = 0f
                }
            ) {
                TopAppBar(
                    color = Color.Transparent,
                    title = stringResource(id = R.string.title_lyrics),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back)) }
                    }
                )
                TabRow(
                    tabs = tabs,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        TextInputDialog(show = showApiKeyDialog, title = stringResource(id = R.string.label_ai_trans_api_key), initialValue = apiKey, onDismiss = { showApiKeyDialog = false }, onConfirm = { apiKey = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, it) })
        TextInputDialog(show = showModelDialog, title = stringResource(id = R.string.label_ai_trans_model), initialValue = model, onDismiss = { showModelDialog = false }, onConfirm = { model = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_MODEL, it) })
        TextInputDialog(show = showBaseUrlDialog, title = stringResource(id = R.string.label_ai_trans_base_url), initialValue = baseUrl, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), onDismiss = { showBaseUrlDialog = false }, onConfirm = { baseUrl = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, it) })
        TextInputDialog(show = showTargetLangDialog, title = stringResource(id = R.string.label_ai_trans_target_lang), initialValue = targetLang, onDismiss = { showTargetLangDialog = false }, onConfirm = { targetLang = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, it) })
        TextInputDialog(show = showPromptDialog, title = stringResource(R.string.title_custom_prompt), initialValue = prompt, onDismiss = { showPromptDialog = false }, onConfirm = { prompt = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, it) })

        FloatInputDialog(show = showWordMotionCjkLiftDialog, title = stringResource(id = R.string.title_word_motion_cjk_lift), label = stringResource(id = R.string.label_word_motion_lift_range), initialValue = wordMotionCjkLift, min = 0f, max = 0.2f, onDismiss = { showWordMotionCjkLiftDialog = false }, onConfirm = { value -> wordMotionCjkLift = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, value) })
        FloatInputDialog(show = showWordMotionCjkWaveDialog, title = stringResource(id = R.string.title_word_motion_cjk_wave), label = stringResource(id = R.string.label_word_motion_wave_range), initialValue = wordMotionCjkWave, min = 0f, max = 8f, onDismiss = { showWordMotionCjkWaveDialog = false }, onConfirm = { value -> wordMotionCjkWave = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, value) })
        FloatInputDialog(show = showWordMotionLatinLiftDialog, title = stringResource(id = R.string.title_word_motion_latin_lift), label = stringResource(id = R.string.label_word_motion_lift_range), initialValue = wordMotionLatinLift, min = 0f, max = 0.2f, onDismiss = { showWordMotionLatinLiftDialog = false }, onConfirm = { value -> wordMotionLatinLift = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, value) })
        FloatInputDialog(show = showWordMotionLatinWaveDialog, title = stringResource(id = R.string.title_word_motion_latin_wave), label = stringResource(id = R.string.label_word_motion_wave_range), initialValue = wordMotionLatinWave, min = 0f, max = 8f, onDismiss = { showWordMotionLatinWaveDialog = false }, onConfirm = { value -> wordMotionLatinWave = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, value) })

        NumberInputDialog(show = showTextSizeDialog, title = stringResource(id = R.string.title_size), label = stringResource(id = R.string.label_size_range), initialValue = textSize, min = 8, max = 16, onDismiss = { showTextSizeDialog = false }, onConfirm = { value -> textSize = value; saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE, value) })
        NumberInputDialog(show = showFontWeightDialog, title = stringResource(id = R.string.title_font_weight), label = stringResource(id = R.string.label_font_weight_range), initialValue = fontWeight, min = 100, max = 900, onDismiss = { showFontWeightDialog = false }, onConfirm = { value -> fontWeight = value; saveConfig(RootConstants.KEY_HOOK_FONT_WEIGHT, value) })
        NumberInputDialog(show = showFadingEdgeDialog, title = stringResource(id = R.string.title_fading_edge), label = stringResource(id = R.string.label_fading_edge_range), initialValue = fadingEdge, min = 0, max = 100, onDismiss = { showFadingEdgeDialog = false }, onConfirm = { value -> fadingEdge = value; saveConfig(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, value) })
        NumberInputDialog(show = showMarqueeSpeedDialog, title = stringResource(id = R.string.title_marquee_speed), label = stringResource(id = R.string.label_marquee_speed_range), initialValue = marqueeSpeed, min = 10, max = 500, onDismiss = { showMarqueeSpeedDialog = false }, onConfirm = { value -> marqueeSpeed = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_SPEED, value) })
        NumberInputDialog(show = showMarqueeDelayDialog, title = stringResource(id = R.string.title_marquee_delay), label = stringResource(id = R.string.label_marquee_delay_range), initialValue = marqueeDelay, min = 0, max = 5000, onDismiss = { showMarqueeDelayDialog = false }, onConfirm = { value -> marqueeDelay = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_DELAY, value) })
        NumberInputDialog(show = showMarqueeLoopDialog, title = stringResource(id = R.string.title_marquee_loop), label = stringResource(id = R.string.label_marquee_loop_range), initialValue = marqueeLoop, min = 0, max = 5000, onDismiss = { showMarqueeLoopDialog = false }, onConfirm = { value -> marqueeLoop = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_LOOP_DELAY, value) })
        NumberInputDialog(show = showTextSizeRatioDialog, title = stringResource(id = R.string.title_text_size_ratio), label = stringResource(id = R.string.label_text_size_ratio_range), initialValue = (textSizeRatio * 100).toInt(), min = 10, max = 100, onDismiss = { showTextSizeRatioDialog = false }, onConfirm = { value -> textSizeRatio = value.toFloat() / 100f; saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, textSizeRatio) })

        TextInputDialog(
            show = showFontPathDialog,
            title = stringResource(id = R.string.title_custom_font),
            label = stringResource(id = R.string.label_custom_font_path),
            initialValue = customFontPath,
            onDismiss = { showFontPathDialog = false },
            onConfirm = { path ->
                customFontPath = path
                saveConfig(RootConstants.KEY_HOOK_CUSTOM_FONT_PATH, path)
            }
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = true) { page ->
            when (page) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().scrollEndHaptic().hazeSource(state = hazeState).overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                        item {
                            Column {
                                SmallTitle(text = stringResource(id = R.string.title_text), insideMargin = PaddingValues(10.dp, 4.dp))
                                Card {
                                    Column {
                                        ArrowPreference(
                                            title = stringResource(id = R.string.title_size),
                                            endActions = {
                                                Text(
                                                    "$textSize",
                                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = { showTextSizeDialog = true }
                                        )
                                        ArrowPreference(
                                            title = stringResource(id = R.string.title_text_size_ratio),
                                            endActions = { Text(stringResource(id = R.string.format_percent, (textSizeRatio * 100).toInt()), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                            onClick = { showTextSizeRatioDialog = true }
                                        )
                                        ArrowPreference(
                                            title = stringResource(id = R.string.title_fading_edge),
                                            endActions = {
                                                Text(
                                                    "$fadingEdge",
                                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                ) },
                                            onClick = { showFadingEdgeDialog = true },
                                            bottomAction = {
                                                Slider(
                                                    value = fadingEdge.toFloat(),
                                                    onValueChange = { fadingEdge = it.toInt(); saveConfig(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, fadingEdge) },
                                                    valueRange = 0f..100f
                                                )
                                            }
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        SwitchPreference(title = stringResource(id = R.string.title_extract_cover_color), checked = extractCoverColor, onCheckedChange = { extractCoverColor = it; saveConfig(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR, it) })
                                        AnimatedVisibility(visible = extractCoverColor) {
                                            SwitchPreference(title = stringResource(id = R.string.title_extract_cover_gradient), checked = extractCoverGradient, onCheckedChange = { extractCoverGradient = it; saveConfig(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT, it) })
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        ArrowPreference(
                                            title = stringResource(id = R.string.title_custom_font),
                                            endActions = {
                                                Text(
                                                    customFontPath.ifEmpty { stringResource(id = R.string.summary_default_font) },
                                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = { showFontPathDialog = true }
                                        )
                                        ArrowPreference(
                                            title = stringResource(id = R.string.title_font_weight),
                                            endActions = {
                                                Text(
                                                    fontWeight.toString(),
                                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = { showFontWeightDialog = true }
                                        )
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_italic),
                                            checked = fontItalic,
                                            onCheckedChange = { fontItalic = it; saveConfig(RootConstants.KEY_HOOK_FONT_ITALIC, it) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Column {
                                SmallTitle(text = stringResource(id = R.string.title_lyric_marquee), insideMargin = PaddingValues(10.dp, 4.dp))
                                Card {
                                    Column {
                                        SwitchPreference(title = stringResource(id = R.string.title_lyric_marquee), checked = marqueeMode, onCheckedChange = { marqueeMode = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_MODE, it) })
                                        AnimatedVisibility(visible = marqueeMode) {
                                            Column {
                                                ArrowPreference(title = stringResource(id = R.string.title_marquee_speed), endActions = { Text("$marqueeSpeed", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showMarqueeSpeedDialog = true }, bottomAction = { Slider(value = marqueeSpeed.toFloat(), onValueChange = { marqueeSpeed = it.toInt(); saveConfig(RootConstants.KEY_HOOK_MARQUEE_SPEED, marqueeSpeed) }, valueRange = 10f..500f) })
                                                ArrowPreference(title = stringResource(id = R.string.title_marquee_delay), endActions = { Text(stringResource(id = R.string.format_ms, marqueeDelay), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showMarqueeDelayDialog = true } )
                                                SwitchPreference(title = stringResource(id = R.string.title_infinite_loop), checked = marqueeInfinite, onCheckedChange = { marqueeInfinite = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_INFINITE, it) })
                                                ArrowPreference(title = stringResource(id = R.string.title_marquee_loop), endActions = { Text(stringResource(id = R.string.format_ms, marqueeLoop), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = { showMarqueeLoopDialog = true } )
                                                SwitchPreference(title = stringResource(id = R.string.title_stop_at_end), checked = marqueeStopEnd, onCheckedChange = { marqueeStopEnd = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_STOP_END, it) })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().scrollEndHaptic().hazeSource(state = hazeState).overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding() + 16.dp)
                    ) {
                        item {
                            Column {
                                SmallTitle(text = stringResource(id = R.string.lyric_mode_verbatim), insideMargin = PaddingValues(10.dp, 4.dp))
                                Card {
                                    Column {
                                        SwitchPreference(title = stringResource(id = R.string.title_gradient_progress), checked = gradientStyle, onCheckedChange = { gradientStyle = it; saveConfig(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, it) })
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_syllable_relative),
                                            summary = stringResource(id = R.string.summary_syllable_relative),
                                            checked = syllableRelative,
                                            onCheckedChange = { syllableRelative = it; saveConfig(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, it) }
                                        )
                                        SwitchPreference(title = stringResource(id = R.string.title_syllable_highlight), checked = syllableHighlight, onCheckedChange = { syllableHighlight = it; saveConfig(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, it) })
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_word_motion),
                                            checked = wordMotionEnabled,
                                            onCheckedChange = { wordMotionEnabled = it; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, it) }
                                        )
                                        AnimatedVisibility(visible = wordMotionEnabled) {
                                            Column {
                                                ArrowPreference(
                                                    title = stringResource(id = R.string.title_word_motion_cjk_lift),
                                                    onClick = { showWordMotionCjkLiftDialog = true },
                                                    endActions = {
                                                        Text(
                                                            String.format("%.2f", wordMotionCjkLift),
                                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    }
                                                )
                                                ArrowPreference(
                                                    title = stringResource(id = R.string.title_word_motion_cjk_wave),
                                                    endActions = {
                                                        Text(
                                                            String.format("%.2f", wordMotionCjkWave),
                                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    },
                                                    onClick = { showWordMotionCjkWaveDialog = true }
                                                )
                                                ArrowPreference(
                                                    title = stringResource(id = R.string.title_word_motion_latin_lift),
                                                    endActions = {
                                                        Text(
                                                            String.format("%.2f", wordMotionLatinLift),
                                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    },
                                                    onClick = { showWordMotionLatinLiftDialog = true }
                                                )
                                                ArrowPreference(
                                                    title = stringResource(id = R.string.title_word_motion_latin_wave),
                                                    endActions = {
                                                        Text(
                                                            String.format("%.2f", wordMotionLatinWave),
                                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                        )
                                                    },
                                                    onClick = { showWordMotionLatinWaveDialog = true }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Column {
                                SmallTitle(text = stringResource(id = R.string.title_translation), insideMargin = PaddingValues(10.dp, 4.dp))
                                Card {
                                    Column {
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_disable_translation),
                                            checked = disableTranslation,
                                            onCheckedChange = { disableTranslation = it; saveConfig(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, it) }
                                        )
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_translation_only),
                                            checked = translationOnly,
                                            onCheckedChange = {
                                                translationOnly = it
                                                saveConfig(RootConstants.KEY_HOOK_TRANSLATION_ONLY, it)
                                                if (it && swapTranslation) {
                                                    swapTranslation = false
                                                    saveConfig(RootConstants.KEY_HOOK_SWAP_TRANSLATION, false)
                                                }
                                            }
                                        )
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_swap_translation),
                                            checked = swapTranslation,
                                            onCheckedChange = {
                                                swapTranslation = it
                                                saveConfig(RootConstants.KEY_HOOK_SWAP_TRANSLATION, it)
                                                if (it && translationOnly) {
                                                    translationOnly = false
                                                    saveConfig(RootConstants.KEY_HOOK_TRANSLATION_ONLY, false)
                                                }
                                            }
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Column {
                                        SwitchPreference(
                                            title = stringResource(id = R.string.title_ai_translation),
                                            checked = aiTransEnabled,
                                            onCheckedChange = { aiTransEnabled = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, it) }
                                        )
                                        AnimatedVisibility(visible = aiTransEnabled) {
                                            Column {
                                                SwitchPreference(
                                                    title = stringResource(id = R.string.title_ai_trans_auto_ignore_chinese),
                                                    checked = autoIgnoreChinese,
                                                    onCheckedChange = { autoIgnoreChinese = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE, it) }
                                                )
                                                Column {
                                                    ArrowPreference(
                                                        title = stringResource(id = R.string.label_ai_trans_target_lang),
                                                        endActions = {
                                                            Text(
                                                                targetLang,
                                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = { showTargetLangDialog = true }
                                                    )
                                                    ArrowPreference(
                                                        title = stringResource(id = R.string.label_ai_trans_api_key),
                                                        endActions = {
                                                            Text(
                                                                if (apiKey.isNotEmpty()) "***************" else "",
                                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = { showApiKeyDialog = true }
                                                    )
                                                    ArrowPreference(
                                                        title = stringResource(id = R.string.label_ai_trans_model),
                                                        endActions = {
                                                            Text(
                                                                model,
                                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = { showModelDialog = true }
                                                    )
                                                    ArrowPreference(
                                                        title = stringResource(id = R.string.label_ai_trans_base_url),
                                                        summary = baseUrl,
                                                        onClick = { showBaseUrlDialog = true }
                                                    )
                                                    ArrowPreference(
                                                        title = stringResource(R.string.title_custom_prompt),
                                                        summary = if (prompt.lines().size > 3) prompt.lines().take(2).joinToString("\n") + "..." else prompt,
                                                        onClick = { showPromptDialog = true }
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

@Composable
fun TextInputDialog(show: Boolean, title: String, initialValue: String, label: String = title, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    if (!show) return
    var inputValue by remember { mutableStateOf(initialValue) }

    WindowDialog(title = title, show = true, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(value = inputValue, onValueChange = { inputValue = it }, label = label, keyboardOptions = keyboardOptions, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), maxLines = 15)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(text = stringResource(id = R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = stringResource(id = R.string.confirm), onClick = { onConfirm(inputValue); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

@Composable
fun FloatInputDialog(show: Boolean, title: String, label: String, initialValue: Float, min: Float, max: Float, onDismiss: () -> Unit, onConfirm: (Float) -> Unit) {
    if (!show) return
    var inputValue by remember { mutableStateOf(initialValue.toString()) }

    WindowDialog(title = title, show = true, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { newValue -> if (newValue.all { it.isDigit() || it == '.' }) inputValue = newValue },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = stringResource(id = R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(id = R.string.confirm),
                    onClick = {
                        inputValue.toFloatOrNull()?.let {
                            onConfirm(it.coerceIn(min, max))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
