package com.lidesheng.hyperlyric.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.root.ConfigSync
import com.lidesheng.hyperlyric.utils.ThemeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.abs

class SettingsActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE)
    }

    private val BASE_MAX_WIDTH = 240
    private var detectedNotchWidthState = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                SettingsScreen()
            }
        }

        window.decorView.post {
            val width = getNaturalCutoutWidth(window.decorView)
            detectedNotchWidthState.intValue = if (width <= 0) 40 else width
        }
    }

    @Composable
    fun SettingsScreen() {
        var textSize by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_TEXT_SIZE, Constants.DEFAULT_TEXT_SIZE)) }
        var isMarquee by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_MARQUEE_MODE, Constants.DEFAULT_MARQUEE)) }
        var hideNotch by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_HIDE_NOTCH, Constants.DEFAULT_HIDE_NOTCH)) }
        var maxLeftWidth by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_MAX_LEFT_WIDTH, Constants.DEFAULT_MAX_LEFT_WIDTH)) }
        var marqueeSpeed by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_MARQUEE_SPEED, Constants.DEFAULT_MARQUEE_SPEED)) }
        var marqueeDelay by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_MARQUEE_DELAY, Constants.DEFAULT_MARQUEE_DELAY)) }
        var animMode by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ANIM_MODE, Constants.DEFAULT_ANIM_MODE)) }

        var showTextSizeDialog by remember { mutableStateOf(false) }
        var showWidthDialog by remember { mutableStateOf(false) }
        var showSpeedDialog by remember { mutableStateOf(false) }
        var showDelayDialog by remember { mutableStateOf(false) }

        val currentMaxWidthLimit = remember(hideNotch, detectedNotchWidthState.intValue) {
            if (hideNotch) BASE_MAX_WIDTH + detectedNotchWidthState.intValue else BASE_MAX_WIDTH
        }

        val animOptions = remember {
            listOf(
                SpinnerEntry(title = "无动画（默认）"),
                SpinnerEntry(title = "左右滑动"),
                SpinnerEntry(title = "淡入淡出"),
                SpinnerEntry(title = "缩放弹跳"),
                SpinnerEntry(title = "纵向推挤")
            )
        }

        fun saveConfig(key: String, value: Any) {
            prefs.edit {
                when (value) {
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
            ConfigSync.syncPreference(Constants.PREF_NAME, key, value)
        }

        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
        
        val hazeState = remember { HazeState() }
        val hazeStyle = HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    color = Color.Transparent,
                    title = "自定义配置",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { finish() },
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
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
                    ),
                ) {
                    item {
                        Column {
                            SmallTitle(
                                text = "歌词显示",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    ConfigSliderItem(
                                        title = "超级岛最大宽度",
                                        value = maxLeftWidth,
                                        valueRange = 40f..currentMaxWidthLimit.toFloat(),
                                        unit = "px",
                                        onValueChange = { maxLeftWidth = it },
                                        onValueChangeFinished = { saveConfig(Constants.KEY_MAX_LEFT_WIDTH, maxLeftWidth) },
                                        onTextClick = { showWidthDialog = true }
                                    )
                                    ConfigSliderItem(
                                        title = "歌词大小",
                                        value = textSize,
                                        valueRange = 8f..27f,
                                        unit = "sp",
                                        onValueChange = { textSize = it },
                                        onValueChangeFinished = { saveConfig(Constants.KEY_TEXT_SIZE, textSize) },
                                        onTextClick = { showTextSizeDialog = true }
                                    )
                                    SuperSpinner(
                                        title = "切换动画",
                                        items = animOptions,
                                        selectedIndex = animMode,
                                        onSelectedIndexChange = {
                                            animMode = it
                                            saveConfig(Constants.KEY_ANIM_MODE, it)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Column {
                            SmallTitle(
                                text = "特殊功能",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    SuperSwitch(
                                        title = "隐藏缺口",
                                        checked = hideNotch,
                                        onCheckedChange = { checked ->
                                            hideNotch = checked
                                            saveConfig(Constants.KEY_HIDE_NOTCH, checked)
                                            if (maxLeftWidth > currentMaxWidthLimit) {
                                                maxLeftWidth = currentMaxWidthLimit
                                                saveConfig(Constants.KEY_MAX_LEFT_WIDTH, maxLeftWidth)
                                            }
                                        }
                                    )
                                    SuperSwitch(
                                        title = "歌词滚动",
                                        checked = isMarquee,
                                        onCheckedChange = { checked ->
                                            isMarquee = checked
                                            saveConfig(Constants.KEY_MARQUEE_MODE, checked)
                                        }
                                    )
                                    AnimatedVisibility(visible = isMarquee) {
                                        Column {
                                            ConfigSliderItem(
                                                title = "滚动速率",
                                                value = marqueeSpeed,
                                                valueRange = 10f..200f,
                                                unit = "",
                                                onValueChange = { marqueeSpeed = it },
                                                onValueChangeFinished = { saveConfig(Constants.KEY_MARQUEE_SPEED, marqueeSpeed) },
                                                onTextClick = { showSpeedDialog = true }
                                            )
                                            ConfigSliderItem(
                                                title = "滚动延迟",
                                                value = marqueeDelay,
                                                valueRange = 0f..5000f,
                                                unit = "ms",
                                                onValueChange = { marqueeDelay = it },
                                                onValueChangeFinished = { saveConfig(Constants.KEY_MARQUEE_DELAY, marqueeDelay) },
                                                onTextClick = { showDelayDialog = true }
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                var textSizeInput by remember(showTextSizeDialog) { mutableStateOf(textSize.toString()) }
                if (showTextSizeDialog) {
                    WindowDialog(
                        title = "歌词大小",
                        show = true,
                        onDismissRequest = { showTextSizeDialog = false }
                    ) {
                        InputDialogContent(
                            inputValue = textSizeInput,
                            onValueChange = { textSizeInput = it },
                            label = "默认值：13，范围：8 ~ 27",
                            onCancel = { showTextSizeDialog = false },
                            onConfirm = {
                                textSizeInput.toIntOrNull()?.let {
                                    val newSize = it.coerceIn(8, 27)
                                    textSize = newSize
                                    saveConfig(Constants.KEY_TEXT_SIZE, newSize)
                                }
                                showTextSizeDialog = false
                            }
                        )
                    }
                }
                var widthInput by remember(showWidthDialog) { mutableStateOf(maxLeftWidth.toString()) }
                if (showWidthDialog) {
                    WindowDialog(
                        title = "输入最大宽度",
                        show = true,
                        onDismissRequest = { showWidthDialog = false }
                    ) {
                        InputDialogContent(
                            inputValue = widthInput,
                            onValueChange = { widthInput = it },
                            label = "默认值：240，范围：40 ~ 240",
                            onCancel = { showWidthDialog = false },
                            onConfirm = {
                                widthInput.toIntOrNull()?.let {
                                    val newWidth = it.coerceIn(40, currentMaxWidthLimit)
                                    maxLeftWidth = newWidth
                                    saveConfig(Constants.KEY_MAX_LEFT_WIDTH, newWidth)
                                }
                                showWidthDialog = false
                            }
                        )
                    }
                }

                var speedInput by remember(showSpeedDialog) { mutableStateOf(marqueeSpeed.toString()) }
                if (showSpeedDialog) {
                    WindowDialog(
                        title = "滚动速率",
                        show = true,
                        onDismissRequest = { showSpeedDialog = false }
                    ) {
                        InputDialogContent(
                            inputValue = speedInput,
                            onValueChange = { speedInput = it },
                            label = "默认值：100，范围：10 ~ 200",
                            onCancel = { showSpeedDialog = false },
                            onConfirm = {
                                speedInput.toIntOrNull()?.let {
                                    val newSpeed = it.coerceIn(10, 200)
                                    marqueeSpeed = newSpeed
                                    saveConfig(Constants.KEY_MARQUEE_SPEED, newSpeed)
                                }
                                showSpeedDialog = false
                            }
                        )
                    }
                }

                var delayInput by remember(showDelayDialog) { mutableStateOf(marqueeDelay.toString()) }
                if (showDelayDialog) {
                    WindowDialog(
                        title = "滚动延迟 (ms)",
                        show = true,
                        onDismissRequest = { showDelayDialog = false }
                    ) {
                        InputDialogContent(
                            inputValue = delayInput,
                            onValueChange = { delayInput = it },
                            label = "默认值：1500，范围：0 ~ 5000",
                            onCancel = { showDelayDialog = false },
                            onConfirm = {
                                delayInput.toIntOrNull()?.let {
                                    val newDelay = it.coerceIn(0, 5000)
                                    marqueeDelay = newDelay
                                    saveConfig(Constants.KEY_MARQUEE_DELAY, newDelay)
                                }
                                showDelayDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun InputDialogContent(
        inputValue: String,
        onValueChange: (String) -> Unit,
        label: String,
        onCancel: () -> Unit,
        onConfirm: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { newValue -> if (newValue.all { it.isDigit() }) onValueChange(newValue) },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = "取消",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = "确认",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }

    @Composable
    fun ConfigSliderItem(
        title: String,
        value: Int,
        valueRange: ClosedFloatingPointRange<Float>,
        unit: String,
        onValueChange: (Int) -> Unit,
        onValueChangeFinished: () -> Unit,
        onTextClick: () -> Unit
    ) {
        val layoutDirection = LocalLayoutDirection.current
        val defaultInsideMargin = BasicComponentDefaults.InsideMargin
        val startPadding = defaultInsideMargin.calculateStartPadding(layoutDirection)
        val endPadding = defaultInsideMargin.calculateEndPadding(layoutDirection)

        Column(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = title,
                endActions = {
                    Text(
                        text = "$value$unit",
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onTextClick
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                },
                insideMargin = defaultInsideMargin
            )

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = valueRange,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = startPadding, end = endPadding)
                    .padding(bottom = 6.dp)
            )
        }
    }

    private fun getNaturalCutoutWidth(view: View): Int {
        val insets = view.rootWindowInsets?.displayCutout
        insets?.boundingRects?.forEach { rect ->
            if (abs((rect.left + rect.right) / 2 - view.resources.displayMetrics.widthPixels / 2) < 200) {
                return rect.width()
            }
        }
        return 0
    }



}
