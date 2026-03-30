package com.lidesheng.hyperlyric.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.root.ConfigSync
import com.lidesheng.hyperlyric.utils.LyricModule
import com.lidesheng.hyperlyric.utils.LyricProviderManager
import com.lidesheng.hyperlyric.utils.ProviderUiState
import com.lidesheng.hyperlyric.utils.ThemeUtils
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.core.net.toUri

private val animLabelMap = mapOf(
    "default" to "默认",
    "fade_out_fade_in" to "渐隐渐现",
    "fade_out_up_fade_in_up" to "向上渐隐＆向上渐现",
    "fade_out_down_fade_in_down" to "向下渐隐＆向下渐现",
    "fade_out_left_fade_in_right" to "向左渐隐＆右侧渐现",
    "fade_out_left_fade_in_up" to "向左渐隐＆向上渐现",
    "fade_out_left_zoom_in" to "向左渐隐＆缩放渐现",
    "fade_out_left_landing" to "向左渐隐＆柔缓着陆",
    "fade_out_right_fade_in_left" to "向右渐隐＆左侧渐现",
    "fade_out_right_fade_in_up" to "向右渐隐＆向上渐现",
    "fade_out_right_zoom_in" to "向右渐隐＆缩放渐现",
    "fade_out_right_landing" to "向右渐隐＆聚焦着陆",
    "fade_out_left_zoom_in_right" to "向左渐隐＆右侧缩放渐现",
    "fade_out_right_zoom_in_left" to "向右渐隐＆左侧缩放渐现",
    "slide_out_left_slide_in_right" to "左侧滑出＆右侧滑入",
    "slide_out_left_fade_in_up" to "左侧滑出＆向上渐现",
    "slide_out_left_zoom_in" to "左侧滑出＆缩放渐现",
    "slide_out_left_landing" to "左侧滑出＆柔缓着陆",
    "slide_out_right_slide_in_left" to "右侧滑出＆左侧滑入",
    "slide_out_right_fade_in_up" to "右侧滑出＆向上渐现",
    "slide_out_right_zoom_in" to "右侧滑出＆缩放渐现",
    "slide_out_right_landing" to "右侧滑出＆柔缓着陆",
    "flip_out_x_flip_in_x" to "X轴翻转",
    "flip_out_y_flip_in_y" to "Y轴翻转",
    "rotate_out_rotate_in" to "旋转",
    "zoom_out_zoom_in" to "缩放",
)

class HookSettingsActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                HookSettingsScreen()
            }
        }
    }

    @Composable
    fun HookSettingsScreen() {
        var textSize by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_TEXT_SIZE,
                    Constants.DEFAULT_TEXT_SIZE
                )
            )
        }
        var fontWeight by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_FONT_WEIGHT,
                    Constants.DEFAULT_FONT_WEIGHT
                )
            )
        }
        var fontBold by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_FONT_BOLD,
                    Constants.DEFAULT_FONT_BOLD
                )
            )
        }
        var fontItalic by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_FONT_ITALIC,
                    Constants.DEFAULT_FONT_ITALIC
                )
            )
        }
        var fadingEdge by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_FADING_EDGE_LENGTH,
                    Constants.DEFAULT_FADING_EDGE_LENGTH
                )
            )
        }
        var gradientStyle by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_GRADIENT_PROGRESS,
                    Constants.DEFAULT_GRADIENT_PROGRESS
                )
            )
        }

        var marqueeMode by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_MARQUEE_MODE,
                    Constants.DEFAULT_MARQUEE_MODE
                )
            )
        }
        var marqueeSpeed by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_MARQUEE_SPEED,
                    Constants.DEFAULT_MARQUEE_SPEED
                )
            )
        }
        var marqueeDelay by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_MARQUEE_DELAY,
                    Constants.DEFAULT_MARQUEE_DELAY
                )
            )
        }
        var marqueeLoop by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_MARQUEE_LOOP_DELAY,
                    Constants.DEFAULT_MARQUEE_LOOP_DELAY
                )
            )
        }
        var marqueeInfinite by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_MARQUEE_INFINITE,
                    Constants.DEFAULT_MARQUEE_INFINITE
                )
            )
        }
        var marqueeStopEnd by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_MARQUEE_STOP_END,
                    Constants.DEFAULT_MARQUEE_STOP_END
                )
            )
        }

        var syllableRelative by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_SYLLABLE_RELATIVE,
                    Constants.DEFAULT_SYLLABLE_RELATIVE
                )
            )
        }
        var syllableHighlight by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_SYLLABLE_HIGHLIGHT,
                    Constants.DEFAULT_SYLLABLE_HIGHLIGHT
                )
            )
        }
        var textSizeRatio by remember {
            mutableFloatStateOf(
                prefs.getFloat(
                    Constants.KEY_TEXT_SIZE_RATIO,
                    Constants.DEFAULT_TEXT_SIZE_RATIO
                )
            )
        }

        var islandLength by remember {
            mutableIntStateOf(
                prefs.getInt(
                    Constants.KEY_MAX_LEFT_WIDTH,
                    Constants.DEFAULT_MAX_LEFT_WIDTH
                )
            )
        }
        var animEnable by remember {
            mutableStateOf(
                prefs.getBoolean(
                    Constants.KEY_ANIM_ENABLE,
                    Constants.DEFAULT_ANIM_ENABLE
                )
            )
        }
        var animId by remember {
            mutableStateOf(
                prefs.getString(
                    Constants.KEY_ANIM_ID,
                    Constants.DEFAULT_ANIM_ID
                ) ?: Constants.DEFAULT_ANIM_ID
            )
        }

        // State variables for dialogs
        var showTextSizeDialog by remember { mutableStateOf(false) }
        var showFontWeightDialog by remember { mutableStateOf(false) }
        var showFadingEdgeDialog by remember { mutableStateOf(false) }
        var showMarqueeSpeedDialog by remember { mutableStateOf(false) }
        var showMarqueeDelayDialog by remember { mutableStateOf(false) }
        var showMarqueeLoopDialog by remember { mutableStateOf(false) }
        var showTextSizeRatioDialog by remember { mutableStateOf(false) }
        var showIslandLengthDialog by remember { mutableStateOf(false) }

        fun saveConfig(key: String, value: Any) {
            prefs.edit {
                when (value) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }
            ConfigSync.syncPreference(Constants.PREF_NAME, key, value)
            // 设置变更后通知 Hook 端立即刷新灵动岛
            val refreshKeys = setOf(
                Constants.KEY_MAX_LEFT_WIDTH,
                Constants.KEY_TEXT_SIZE,
                Constants.KEY_FONT_WEIGHT,
                Constants.KEY_FONT_BOLD,
                Constants.KEY_FONT_ITALIC,
                Constants.KEY_FADING_EDGE_LENGTH,
                Constants.KEY_ANIM_ENABLE,
                Constants.KEY_ANIM_ID
            )
            if (key in refreshKeys) {
                sendBroadcast(Intent("com.lidesheng.hyperlyric.REFRESH_ISLAND"))
            }
        }

        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
        val hazeState = remember { HazeState() }
        val hazeStyle = HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )

        // Tab state
        val tabs = listOf("歌词", "动画", "歌词提供者")
        val pagerState = rememberPagerState { tabs.size }
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val coroutineScope = rememberCoroutineScope()
        val context = LocalContext.current
        val providerUiStateFlow = remember { MutableStateFlow(ProviderUiState()) }
        val providerUiState = providerUiStateFlow.collectAsState()

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                selectedTabIndex = page
            }
        }

        LaunchedEffect(Unit) {
            LyricProviderManager.loadProviders(context, providerUiStateFlow)
        }

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
                        title = "自定义配置",
                        largeTitle = "自定义配置",
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(
                                onClick = { finish() }
                            ) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    try {
                                        val url = getString(com.lidesheng.hyperlyric.R.string.provider_release_home)
                                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                        startActivity(intent)
                                    } catch (_: Exception) { }
                                },
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = com.lidesheng.hyperlyric.R.drawable.ic_github),
                                    contentDescription = "GitHub",
                                    tint = MiuixTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        },
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        pressFeedbackType = PressFeedbackType.Sink,
                        onClick = { }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color(0xFF3582FF).copy(alpha = 0.4f))
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Powered by lyricon词幕",
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = {
                            selectedTabIndex = it
                            coroutineScope.launch {
                                pagerState.scrollToPage(it)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = TabRowDefaults.tabRowColors(
                            backgroundColor = Color.Transparent
                        )
                    )
                }
            }
        ) { padding ->

            NumberInputDialog(
                show = showTextSizeDialog,
                title = "文字大小",
                label = "范围：8 ~ 16",
                initialValue = textSize,
                min = 8, max = 16,
                onDismiss = { showTextSizeDialog = false },
                onConfirm = { value ->
                    textSize = value
                    saveConfig(Constants.KEY_TEXT_SIZE, value)
                }
            )
            NumberInputDialog(
                show = showFontWeightDialog,
                title = "字重",
                label = "范围：100 ~ 900",
                initialValue = fontWeight,
                min = 100, max = 900,
                onDismiss = { showFontWeightDialog = false },
                onConfirm = { value ->
                    fontWeight = value
                    saveConfig(Constants.KEY_FONT_WEIGHT, value)
                }
            )
            NumberInputDialog(
                show = showFadingEdgeDialog,
                title = "羽化边缘长度",
                label = "范围：0 ~ 100",
                initialValue = fadingEdge,
                min = 0, max = 100,
                onDismiss = { showFadingEdgeDialog = false },
                onConfirm = { value ->
                    fadingEdge = value
                    saveConfig(Constants.KEY_FADING_EDGE_LENGTH, value)
                }
            )
            NumberInputDialog(
                show = showMarqueeSpeedDialog,
                title = "滚动速度",
                label = "范围：10 ~ 500",
                initialValue = marqueeSpeed,
                min = 10, max = 500,
                onDismiss = { showMarqueeSpeedDialog = false },
                onConfirm = { value ->
                    marqueeSpeed = value
                    saveConfig(Constants.KEY_MARQUEE_SPEED, value)
                }
            )
            NumberInputDialog(
                show = showMarqueeDelayDialog,
                title = "初始滚动延迟",
                label = "ms 范围：0 ~ 5000",
                initialValue = marqueeDelay,
                min = 0, max = 5000,
                onDismiss = { showMarqueeDelayDialog = false },
                onConfirm = { value ->
                    marqueeDelay = value
                    saveConfig(Constants.KEY_MARQUEE_DELAY, value)
                }
            )
            NumberInputDialog(
                show = showMarqueeLoopDialog,
                title = "循环间隔",
                label = "ms 范围：0 ~ 5000",
                initialValue = marqueeLoop,
                min = 0, max = 5000,
                onDismiss = { showMarqueeLoopDialog = false },
                onConfirm = { value ->
                    marqueeLoop = value
                    saveConfig(Constants.KEY_MARQUEE_LOOP_DELAY, value)
                }
            )
            NumberInputDialog(
                show = showIslandLengthDialog,
                title = "超级岛长度",
                label = "范围：40 ~ 120",
                initialValue = islandLength,
                min = 40, max = 120,
                onDismiss = { showIslandLengthDialog = false },
                onConfirm = { value ->
                    islandLength = value
                    saveConfig(Constants.KEY_MAX_LEFT_WIDTH, value)
                }
            )
            NumberInputDialog(
                show = showTextSizeRatioDialog,
                title = "多行模式下文字大小比例",
                label = "范围：10 ~ 100",
                initialValue = (textSizeRatio * 100).toInt(),
                min = 10, max = 100,
                onDismiss = { showTextSizeRatioDialog = false },
                onConfirm = { value ->
                    textSizeRatio = value.toFloat() / 100f
                    saveConfig(Constants.KEY_TEXT_SIZE_RATIO, textSizeRatio)
                }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollEndHaptic()
                                .hazeSource(state = hazeState)
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = padding.calculateTopPadding(),
                                start = 12.dp,
                                end = 12.dp,
                                bottom = padding.calculateBottomPadding()
                            )
                        ) {
                            item {
                                Column {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        ArrowPreference(
                                            title = "超级岛长度",
                                            endActions = {
                                                Text(
                                                    "$islandLength",
                                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            },
                                            onClick = { showIslandLengthDialog = true },
                                            bottomAction = {
                                                Slider(
                                                    value = islandLength.toFloat(),
                                                    onValueChange = {
                                                        islandLength = it.toInt()
                                                        saveConfig(Constants.KEY_MAX_LEFT_WIDTH, islandLength)
                                                    },
                                                    valueRange = 40f..120f,
                                                    keyPoints = listOf(40f, 50f, 60f, 70f, 80f, 90f, 100f, 110f, 120f),
                                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                Column {
                                    SmallTitle(
                                        text = "文字",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ArrowPreference(
                                                title = "大小",
                                                endActions = {
                                                    Text(
                                                        "$textSize",
                                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = { showTextSizeDialog = true },
                                                bottomAction = {
                                                    Slider(
                                                        value = textSize.toFloat(),
                                                        onValueChange = {
                                                            textSize = it.toInt()
                                                            saveConfig(Constants.KEY_TEXT_SIZE, textSize)
                                                        },
                                                        steps = 8,
                                                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                                        valueRange = 8f..16f
                                                    )
                                                }
                                            )
                                            ArrowPreference(
                                                title = "字重",
                                                endActions = {
                                                    Text(
                                                        fontWeight.toString(),
                                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = { showFontWeightDialog = true },
                                                bottomAction = {
                                                    Slider(
                                                        value = fontWeight.toFloat(),
                                                        onValueChange = {
                                                            fontWeight = it.toInt()
                                                            saveConfig(Constants.KEY_FONT_WEIGHT, fontWeight)
                                                        },
                                                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                                        keyPoints = listOf(100f, 200f, 300f, 400f, 500f, 600f, 700f, 800f, 900f),
                                                        valueRange = 100f..900f
                                                    )
                                                }
                                            )
                                            SwitchPreference(
                                                title = "粗体",
                                                checked = fontBold,
                                                onCheckedChange = {
                                                    fontBold = it
                                                    saveConfig(Constants.KEY_FONT_BOLD, it)
                                                }
                                            )
                                            SwitchPreference(
                                                title = "斜体",
                                                checked = fontItalic,
                                                onCheckedChange = {
                                                    fontItalic = it
                                                    saveConfig(Constants.KEY_FONT_ITALIC, it)
                                                }
                                            )
                                            ArrowPreference(
                                                title = "多行模式下文字大小比例",
                                                endActions = {
                                                    Text(
                                                        "${(textSizeRatio * 100).toInt()}%",
                                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = { showTextSizeRatioDialog = true },
                                                bottomAction = {
                                                    Slider(
                                                        value = textSizeRatio,
                                                        onValueChange = {
                                                            textSizeRatio = it
                                                            saveConfig(Constants.KEY_TEXT_SIZE_RATIO, textSizeRatio)
                                                        },
                                                        valueRange = 0.1f..1f
                                                    )
                                                }
                                            )
                                            ArrowPreference(
                                                title = "羽化边缘长度",
                                                endActions = {
                                                    Text(
                                                        "$fadingEdge",
                                                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                    )
                                                },
                                                onClick = { showFadingEdgeDialog = true },
                                                bottomAction = {
                                                    Slider(
                                                        value = fadingEdge.toFloat(),
                                                        onValueChange = {
                                                            fadingEdge = it.toInt()
                                                            saveConfig(Constants.KEY_FADING_EDGE_LENGTH, fadingEdge)
                                                        },
                                                        valueRange = 0f..100f
                                                    )
                                                }
                                            )
                                            SwitchPreference(
                                                title = "羽化进度样式",
                                                checked = gradientStyle,
                                                onCheckedChange = {
                                                    gradientStyle = it
                                                    saveConfig(Constants.KEY_GRADIENT_PROGRESS, it)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. 歌词滚动
                            item {
                                Column {
                                    SmallTitle(
                                        text = "歌词滚动",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            // 跑马灯部分
                                            SwitchPreference(
                                                title = "歌词滚动",
                                                checked = marqueeMode,
                                                onCheckedChange = {
                                                    marqueeMode = it
                                                    saveConfig(Constants.KEY_MARQUEE_MODE, it)
                                                }
                                            )
                                            AnimatedVisibility(visible = marqueeMode) {
                                                Column {
                                                    ArrowPreference(
                                                        title = "滚动速度",
                                                        endActions = {
                                                            Text(
                                                                "$marqueeSpeed",
                                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = { showMarqueeSpeedDialog = true },
                                                        bottomAction = {
                                                            Slider(
                                                                value = marqueeSpeed.toFloat(),
                                                                onValueChange = {
                                                                    marqueeSpeed = it.toInt()
                                                                    saveConfig(Constants.KEY_MARQUEE_SPEED, marqueeSpeed)
                                                                },
                                                                valueRange = 10f..500f
                                                            )
                                                        }
                                                    )
                                                    ArrowPreference(
                                                        title = "初始滚动延迟",
                                                        endActions = {
                                                            Text(
                                                                "${marqueeDelay}ms",
                                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = { showMarqueeDelayDialog = true },
                                                        bottomAction = {
                                                            Slider(
                                                                value = marqueeDelay.toFloat(),
                                                                onValueChange = {
                                                                    marqueeDelay = it.toInt()
                                                                    saveConfig(Constants.KEY_MARQUEE_DELAY, marqueeDelay)
                                                                },
                                                                valueRange = 0f..5000f
                                                            )
                                                        }
                                                    )
                                                    SwitchPreference(
                                                        title = "无限循环",
                                                        checked = marqueeInfinite,
                                                        onCheckedChange = {
                                                            marqueeInfinite = it
                                                            saveConfig(Constants.KEY_MARQUEE_INFINITE, it)
                                                        }
                                                    )
                                                    ArrowPreference(
                                                        title = "循环间隔",
                                                        endActions = {
                                                            Text(
                                                                "${marqueeLoop}ms",
                                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                            )
                                                        },
                                                        onClick = { showMarqueeLoopDialog = true },
                                                        bottomAction = {
                                                            Slider(
                                                                value = marqueeLoop.toFloat(),
                                                                onValueChange = {
                                                                    marqueeLoop = it.toInt()
                                                                    saveConfig(Constants.KEY_MARQUEE_LOOP_DELAY, marqueeLoop)
                                                                },
                                                                valueRange = 0f..5000f
                                                            )
                                                        }
                                                    )
                                                    SwitchPreference(
                                                        title = "结束时在末尾停止",
                                                        checked = marqueeStopEnd,
                                                        onCheckedChange = {
                                                            marqueeStopEnd = it
                                                            saveConfig(Constants.KEY_MARQUEE_STOP_END, it)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. 逐字歌词
                            item {
                                Column {
                                    SmallTitle(
                                        text = "逐字歌词",
                                        insideMargin = PaddingValues(10.dp, 4.dp)
                                    )
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            SwitchPreference(
                                                title = "启用相对进度歌词",
                                                checked = syllableRelative,
                                                onCheckedChange = {
                                                    syllableRelative = it
                                                    saveConfig(Constants.KEY_SYLLABLE_RELATIVE, it)
                                                }
                                            )
                                            SwitchPreference(
                                                title = "显示相对进度歌词高亮进度",
                                                checked = syllableHighlight,
                                                onCheckedChange = {
                                                    syllableHighlight = it
                                                    saveConfig(Constants.KEY_SYLLABLE_HIGHLIGHT, it)
                                                }
                                            )
                                            // 注意：逐字歌词功能的开启取决于数据源是否提供逐字数据，不再提供全局开关
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollEndHaptic()
                                .hazeSource(state = hazeState)
                                .overScrollVertical()
                                .nestedScroll(scrollBehavior.nestedScrollConnection),
                            contentPadding = PaddingValues(
                                top = padding.calculateTopPadding(),
                                start = 12.dp,
                                end = 12.dp,
                                bottom = padding.calculateBottomPadding()
                            )
                        ) {
                            item {
                                SmallTitle(
                                    text = "歌词切换动画",
                                    insideMargin = PaddingValues(10.dp, 4.dp, 10.dp, 4.dp)
                                )
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        RadioButtonPreference(
                                            title = "无动画",
                                            selected = !animEnable,
                                            onClick = {
                                                animEnable = false
                                                saveConfig(Constants.KEY_ANIM_ENABLE, false)
                                            }
                                        )
                                        val registry = io.github.proify.lyricon.lyric.view.yoyo.YoYoPresets.registry
                                        val keys = registry.keys.toList()
                                        keys.forEach { key ->
                                            val label = animLabelMap[key] ?: key
                                            RadioButtonPreference(
                                                title = label,
                                                selected = animEnable && animId == key,
                                                onClick = {
                                                    animEnable = true
                                                    saveConfig(Constants.KEY_ANIM_ENABLE, true)
                                                    animId = key
                                                    saveConfig(Constants.KEY_ANIM_ID, key)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        LyricProviderTab(
                            uiState = providerUiState.value,
                            padding = padding,
                            hazeState = hazeState,
                            scrollBehavior = scrollBehavior
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun NumberInputDialog(
        show: Boolean,
        title: String,
        label: String,
        initialValue: Int,
        min: Int,
        max: Int,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit
    ) {
        if (!show) return
        var inputValue by remember { mutableStateOf(initialValue.toString()) }

        WindowDialog(
            title = title,
            show = true,
            onDismissRequest = onDismiss
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = inputValue,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) inputValue = newValue
                    },
                    label = label,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    maxLines = 1
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = "确认",
                        onClick = {
                            inputValue.toIntOrNull()?.let {
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

    @Composable
    private fun LyricProviderTab(
        uiState: ProviderUiState,
        padding: PaddingValues,
        hazeState: HazeState,
        scrollBehavior: ScrollBehavior
    ) {
        val groupedModules = remember(uiState.modules) {
            LyricProviderManager.categorizeModules(uiState.modules, "其他")
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .hazeSource(state = hazeState)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                start = 12.dp,
                end = 12.dp,
                bottom = padding.calculateBottomPadding()
            )
        ) {
            if (uiState.isLoading && uiState.modules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "正在扫描...",
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            } else if (!uiState.isLoading && uiState.modules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂未发现歌词提供者，请点击右上角图标前往LyricProvider仓库下载",
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            } else {
                groupedModules.forEach { category ->
                    if (category.name.isNotBlank()) {
                        item(key = "header_${category.name}") {
                            SmallTitle(
                                text = category.name,
                                insideMargin = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }

                    items(category.items.size) { index ->
                        val module = category.items[index]
                        ModuleCard(module)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun ModuleCard(module: LyricModule) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Sink
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = module.label,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${module.packageInfo.versionName ?: "未知"} | ${module.author ?: "未知作者"}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )

                if (!module.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 0.5.dp,
                        color = MiuixTheme.colorScheme.outline
                    )
                    Text(
                        modifier = Modifier.padding(top = 12.dp),
                        text = module.description,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
        }
    }

}
