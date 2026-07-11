package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import com.lidesheng.hyperlyric.ui.component.FloatInputDialog
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lidesheng.hyperlyric.ui.page.hooksettings.xposedLyricSettings.LyricBasicTab
import com.lidesheng.hyperlyric.ui.page.hooksettings.xposedLyricSettings.LyricAdvancedTab

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
    var marqueeMetadataSpeed by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_SPEED)) }
    var marqueeMetadataMode by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_MODE)) }
    var marqueeMetadataDelay by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_DELAY)) }
    var marqueeMetadataLoopDelay by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY)) }
    var marqueeMetadataInfinite by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE, RootConstants.DEFAULT_HOOK_MARQUEE_METADATA_INFINITE)) }
    var syllableRelative by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE)) }
    var syllableHighlight by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT)) }
    var textSizeRatio by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO)) }
    var disableTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, RootConstants.DEFAULT_HOOK_DISABLE_TRANSLATION)) }
    var translationOnly by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_TRANSLATION_ONLY, RootConstants.DEFAULT_HOOK_TRANSLATION_ONLY)) }
    var swapTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_SWAP_TRANSLATION, RootConstants.DEFAULT_HOOK_SWAP_TRANSLATION)) }
    var nextLyricLine by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_NEXT_LYRIC_LINE, RootConstants.DEFAULT_HOOK_NEXT_LYRIC_LINE)) }
    var extractCoverColor by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR, RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_COLOR)) }
    var extractCoverGradient by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT, RootConstants.DEFAULT_HOOK_EXTRACT_COVER_TEXT_GRADIENT)) }
    var customFontPath by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_CUSTOM_FONT_PATH, null) ?: "") }
    var centerLyric by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_CENTER_LYRIC, RootConstants.DEFAULT_HOOK_CENTER_LYRIC)) }
    val lyricMode by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)) }
    val lyricSource by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_LYRIC_SOURCE, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE) ?: "lyricon") }

    var aiTransEnabled by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE)) }
    var autoIgnoreChinese by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE, RootConstants.DEFAULT_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE)) }
    var skipExistingTranslation by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION, RootConstants.DEFAULT_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION)) }
    var apiKey by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_MODEL, RootConstants.DEFAULT_HOOK_AI_TRANS_MODEL) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_MODEL) }
    var baseUrl by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, RootConstants.DEFAULT_HOOK_AI_TRANS_BASE_URL) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_BASE_URL) }
    var targetLang by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG) }
    var prompt by remember { mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT) }

    var wordMotionEnabled by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED)) }
    var wordMotionCjkLift by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT)) }
    var wordMotionCjkWave by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE)) }
    var wordMotionLatinLift by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT)) }
    var wordMotionLatinWave by remember { mutableFloatStateOf(prefs.getFloat(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE)) }

    var showTextSizeDialog by remember { mutableStateOf(false) }
    var showFontWeightDialog by remember { mutableStateOf(false) }
    var showFadingEdgeDialog by remember { mutableStateOf(false) }
    var showMarqueeSpeedDialog by remember { mutableStateOf(false) }
    var showMarqueeMetadataSpeedDialog by remember { mutableStateOf(false) }
    var showMarqueeMetadataDelayDialog by remember { mutableStateOf(false) }
    var showMarqueeMetadataLoopDialog by remember { mutableStateOf(false) }
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

    val saveConfig = remember {
        { key: String, value: Any ->
            prefs.edit {
                when (value) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
            when (value) {
                is Int -> PrefsBridge.putInt(key, value)
                is Boolean -> PrefsBridge.putBoolean(key, value)
                is Float -> PrefsBridge.putFloat(key, value)
                is String -> PrefsBridge.putString(key, value)
            }
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    val tabs = listOf(stringResource(R.string.tab_basic), stringResource(R.string.tab_advanced))
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val basicLazyListState = rememberLazyListState()
    val advancedLazyListState = rememberLazyListState()

    TextInputDialog(show = showApiKeyDialog, title = stringResource(id = R.string.label_ai_trans_api_key), initialValue = apiKey, onDismiss = { showApiKeyDialog = false }, onConfirm = { apiKey = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, it) })
    TextInputDialog(show = showModelDialog, title = stringResource(id = R.string.label_ai_trans_model), initialValue = model, onDismiss = { showModelDialog = false }, onConfirm = { model = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_MODEL, it) })
    TextInputDialog(show = showBaseUrlDialog, title = stringResource(id = R.string.label_ai_trans_base_url), initialValue = baseUrl, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), onDismiss = { showBaseUrlDialog = false }, onConfirm = { baseUrl = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, it) })
    TextInputDialog(show = showTargetLangDialog, title = stringResource(id = R.string.label_ai_trans_target_lang), initialValue = targetLang, onDismiss = { showTargetLangDialog = false }, onConfirm = { targetLang = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, it) })
    TextInputDialog(show = showPromptDialog, title = stringResource(R.string.title_custom_prompt), initialValue = prompt, onDismiss = { showPromptDialog = false }, onConfirm = { prompt = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, it) })

    FloatInputDialog(show = showWordMotionCjkLiftDialog, title = stringResource(id = R.string.title_word_motion_cjk_lift), label = stringResource(id = R.string.label_word_motion_lift_range), initialValue = wordMotionCjkLift, min = 0f, max = 0.2f, onDismiss = { showWordMotionCjkLiftDialog = false }, onConfirm = { value -> wordMotionCjkLift = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT, value) })
    FloatInputDialog(show = showWordMotionCjkWaveDialog, title = stringResource(id = R.string.title_word_motion_cjk_wave), label = stringResource(id = R.string.label_word_motion_wave_range), initialValue = wordMotionCjkWave, min = 0f, max = 10f, onDismiss = { showWordMotionCjkWaveDialog = false }, onConfirm = { value -> wordMotionCjkWave = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE, value) })
    FloatInputDialog(show = showWordMotionLatinLiftDialog, title = stringResource(id = R.string.title_word_motion_latin_lift), label = stringResource(id = R.string.label_word_motion_lift_range), initialValue = wordMotionLatinLift, min = 0f, max = 0.2f, onDismiss = { showWordMotionLatinLiftDialog = false }, onConfirm = { value -> wordMotionLatinLift = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT, value) })
    FloatInputDialog(show = showWordMotionLatinWaveDialog, title = stringResource(id = R.string.title_word_motion_latin_wave), label = stringResource(id = R.string.label_word_motion_wave_range), initialValue = wordMotionLatinWave, min = 0f, max = 10f, onDismiss = { showWordMotionLatinWaveDialog = false }, onConfirm = { value -> wordMotionLatinWave = value; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE, value) })

    NumberInputDialog(show = showTextSizeDialog, title = stringResource(id = R.string.title_size), label = stringResource(id = R.string.label_size_range), initialValue = textSize, min = 8, max = 16, onDismiss = { showTextSizeDialog = false }, onConfirm = { value -> textSize = value; saveConfig(RootConstants.KEY_HOOK_TEXT_SIZE, value) })
    NumberInputDialog(show = showFontWeightDialog, title = stringResource(id = R.string.title_font_weight), label = stringResource(id = R.string.label_font_weight_range), initialValue = fontWeight, min = 100, max = 900, onDismiss = { showFontWeightDialog = false }, onConfirm = { value -> fontWeight = value; saveConfig(RootConstants.KEY_HOOK_FONT_WEIGHT, value) })
    NumberInputDialog(show = showFadingEdgeDialog, title = stringResource(id = R.string.title_fading_edge), label = stringResource(id = R.string.label_fading_edge_range), initialValue = fadingEdge, min = 0, max = 100, onDismiss = { showFadingEdgeDialog = false }, onConfirm = { value -> fadingEdge = value; saveConfig(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, value) })
    NumberInputDialog(
        show = showMarqueeSpeedDialog,
        title = stringResource(id = R.string.title_marquee_speed),
        label = stringResource(id = R.string.label_marquee_speed_range),
        initialValue = marqueeSpeed, min = 5, max = 100,
        onDismiss = { showMarqueeSpeedDialog = false },
        onConfirm = { value -> marqueeSpeed = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_SPEED, value) }
    )
    NumberInputDialog(
        show = showMarqueeMetadataSpeedDialog,
        title = stringResource(id = R.string.title_marquee_metadata_speed),
        label = stringResource(id = R.string.label_marquee_speed_range),
        initialValue = marqueeMetadataSpeed, min = 5, max = 100,
        onDismiss = { showMarqueeMetadataSpeedDialog = false },
        onConfirm = { value -> marqueeMetadataSpeed = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_SPEED, value) }
    )
    NumberInputDialog(
        show = showMarqueeMetadataDelayDialog,
        title = stringResource(id = R.string.title_marquee_metadata_delay),
        label = stringResource(id = R.string.label_marquee_delay_range),
        initialValue = marqueeMetadataDelay, min = 0, max = 10000,
        onDismiss = { showMarqueeMetadataDelayDialog = false },
        onConfirm = { value -> marqueeMetadataDelay = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_DELAY, value) }
    )
    NumberInputDialog(
        show = showMarqueeMetadataLoopDialog,
        title = stringResource(id = R.string.title_marquee_metadata_loop),
        label = stringResource(id = R.string.label_marquee_loop_range),
        initialValue = marqueeMetadataLoopDelay, min = 0, max = 10000,
        onDismiss = { showMarqueeMetadataLoopDialog = false },
        onConfirm = { value -> marqueeMetadataLoopDelay = value; saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY, value) }
    )
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

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_lyrics),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    bottomContent = {
                        TabRow(
                            tabs = tabs,
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                        )
                    }
                )
            }
        }
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()
        val contentPadding = remember(topPadding, bottomPadding) {
            PaddingValues(top = topPadding, start = 0.dp, end = 0.dp, bottom = bottomPadding + 16.dp)
        }

        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = true, beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> {
                        LyricBasicTab(
                            lazyListState = basicLazyListState,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                            contentPadding = contentPadding,
                            lyricMode = lyricMode,
                            textSize = textSize,
                            onTextSizeClick = { showTextSizeDialog = true },
                            textSizeRatio = textSizeRatio,
                            onTextSizeRatioClick = { showTextSizeRatioDialog = true },
                            fadingEdge = fadingEdge,
                            onFadingEdgeChange = { fadingEdge = it; saveConfig(RootConstants.KEY_HOOK_FADING_EDGE_LENGTH, it) },
                            onFadingEdgeClick = { showFadingEdgeDialog = true },
                            extractCoverColor = extractCoverColor,
                            onExtractCoverColorChange = { extractCoverColor = it; saveConfig(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_COLOR, it) },
                            extractCoverGradient = extractCoverGradient,
                            onExtractCoverGradientChange = { extractCoverGradient = it; saveConfig(RootConstants.KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT, it) },
                            customFontPath = customFontPath,
                            onFontPathClick = { showFontPathDialog = true },
                            fontWeight = fontWeight,
                            onFontWeightClick = { showFontWeightDialog = true },
                            fontItalic = fontItalic,
                            onFontItalicChange = { fontItalic = it; saveConfig(RootConstants.KEY_HOOK_FONT_ITALIC, it) },
                            centerLyric = centerLyric,
                            onCenterLyricChange = { centerLyric = it; saveConfig(RootConstants.KEY_HOOK_CENTER_LYRIC, it) },
                            marqueeMode = marqueeMode,
                            onMarqueeModeChange = { marqueeMode = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_MODE, it) },
                            marqueeSpeed = marqueeSpeed,
                            onMarqueeSpeedClick = { showMarqueeSpeedDialog = true },
                            marqueeDelay = marqueeDelay,
                            onMarqueeDelayClick = { showMarqueeDelayDialog = true },
                            marqueeInfinite = marqueeInfinite,
                            onMarqueeInfiniteChange = { marqueeInfinite = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_INFINITE, it) },
                            marqueeLoop = marqueeLoop,
                            onMarqueeLoopClick = { showMarqueeLoopDialog = true },
                            marqueeStopEnd = marqueeStopEnd,
                            onMarqueeStopEndChange = { marqueeStopEnd = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_STOP_END, it) },
                            marqueeMetadataMode = marqueeMetadataMode,
                            onMarqueeMetadataModeChange = { marqueeMetadataMode = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_MODE, it) },
                            marqueeMetadataSpeed = marqueeMetadataSpeed,
                            onMarqueeMetadataSpeedClick = { showMarqueeMetadataSpeedDialog = true },
                            marqueeMetadataDelay = marqueeMetadataDelay,
                            onMarqueeMetadataDelayClick = { showMarqueeMetadataDelayDialog = true },
                            marqueeMetadataInfinite = marqueeMetadataInfinite,
                            onMarqueeMetadataInfiniteChange = { marqueeMetadataInfinite = it; saveConfig(RootConstants.KEY_HOOK_MARQUEE_METADATA_INFINITE, it) },
                            marqueeMetadataLoopDelay = marqueeMetadataLoopDelay,
                            onMarqueeMetadataLoopClick = { showMarqueeMetadataLoopDialog = true }
                        )
                    }
                    1 -> {
                        LyricAdvancedTab(
                            lazyListState = advancedLazyListState,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                            contentPadding = contentPadding,
                            lyricSource = lyricSource,
                            lyricMode = lyricMode,
                            gradientStyle = gradientStyle,
                            onGradientStyleChange = { gradientStyle = it; saveConfig(RootConstants.KEY_HOOK_GRADIENT_PROGRESS, it) },
                            syllableRelative = syllableRelative,
                            onSyllableRelativeChange = { syllableRelative = it; saveConfig(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, it) },
                            syllableHighlight = syllableHighlight,
                            onSyllableHighlightChange = { syllableHighlight = it; saveConfig(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, it) },
                            wordMotionEnabled = wordMotionEnabled,
                            onWordMotionEnabledChange = { wordMotionEnabled = it; saveConfig(RootConstants.KEY_HOOK_WORD_MOTION_ENABLED, it) },
                            wordMotionCjkLift = wordMotionCjkLift,
                            onWordMotionCjkLiftClick = { showWordMotionCjkLiftDialog = true },
                            wordMotionCjkWave = wordMotionCjkWave,
                            onWordMotionCjkWaveClick = { showWordMotionCjkWaveDialog = true },
                            wordMotionLatinLift = wordMotionLatinLift,
                            onWordMotionLatinLiftClick = { showWordMotionLatinLiftDialog = true },
                            wordMotionLatinWave = wordMotionLatinWave,
                            onWordMotionLatinWaveClick = { showWordMotionLatinWaveDialog = true },
                            disableTranslation = disableTranslation,
                            onDisableTranslationChange = { disableTranslation = it; saveConfig(RootConstants.KEY_HOOK_DISABLE_TRANSLATION, it) },
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
                            onNextLyricLineChange = { nextLyricLine = it; saveConfig(RootConstants.KEY_HOOK_NEXT_LYRIC_LINE, it) },
                            aiTransEnabled = aiTransEnabled,
                            onAiTransEnabledChange = { aiTransEnabled = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, it) },
                            autoIgnoreChinese = autoIgnoreChinese,
                            onAutoIgnoreChineseChange = { autoIgnoreChinese = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE, it) },
                            skipExistingTranslation = skipExistingTranslation,
                            onSkipExistingTranslationChange = { skipExistingTranslation = it; saveConfig(RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION, it) },
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
            }
        }
    }
}

