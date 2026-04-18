package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.Constants
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
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SuperIslandSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
    
    var islandContentLeft by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_CONTENT_LEFT, Constants.DEFAULT_ISLAND_CONTENT_LEFT)) }
    var islandContentRight by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_CONTENT_RIGHT, Constants.DEFAULT_ISLAND_CONTENT_RIGHT)) }
    
    var audioCover by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_ISLAND_LEFT_ALBUM, Constants.DEFAULT_ISLAND_LEFT_ALBUM)) }
    var audioRhythm by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_PROGRESS_COLOR_ENABLED, Constants.DEFAULT_PROGRESS_COLOR_ENABLED)) }
    
    var leftPaddingLeft by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_LEFT_PADDING_LEFT, Constants.DEFAULT_ISLAND_LEFT_PADDING_LEFT)) }
    var leftPaddingRight by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_LEFT_PADDING_RIGHT, Constants.DEFAULT_ISLAND_LEFT_PADDING_RIGHT)) }
    var rightPaddingLeft by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_RIGHT_PADDING_LEFT, Constants.DEFAULT_ISLAND_RIGHT_PADDING_LEFT)) }
    var rightPaddingRight by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_RIGHT_PADDING_RIGHT, Constants.DEFAULT_ISLAND_RIGHT_PADDING_RIGHT)) }
    var leftContentWidth by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_LEFT_CONTENT_MAX_WIDTH, Constants.DEFAULT_ISLAND_LEFT_CONTENT_MAX_WIDTH)) }
    var rightContentWidth by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_ISLAND_RIGHT_CONTENT_MAX_WIDTH, Constants.DEFAULT_ISLAND_RIGHT_CONTENT_MAX_WIDTH)) }

    var showLeftPaddingDialog by remember { mutableStateOf(false) }
    var showRightPaddingDialog by remember { mutableStateOf(false) }
    var showLeftContentWidthDialog by remember { mutableStateOf(false) }
    var showRightContentWidthDialog by remember { mutableStateOf(false) }

    fun saveConfig(key: String, value: Any) {
        prefs.edit {
            when (value) {
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
            }
        }
        ConfigSync.syncPreference(Constants.PREF_NAME, key, value)
        if (key == Constants.KEY_ISLAND_LEFT_ALBUM || key == Constants.KEY_PROGRESS_COLOR_ENABLED || 
            key == Constants.KEY_ISLAND_CONTENT_LEFT || key == Constants.KEY_ISLAND_CONTENT_RIGHT ||
            key == Constants.KEY_ISLAND_LEFT_PADDING_LEFT || key == Constants.KEY_ISLAND_LEFT_PADDING_RIGHT ||
            key == Constants.KEY_ISLAND_RIGHT_PADDING_LEFT || key == Constants.KEY_ISLAND_RIGHT_PADDING_RIGHT ||
            key == Constants.KEY_ISLAND_LEFT_CONTENT_MAX_WIDTH || key == Constants.KEY_ISLAND_RIGHT_CONTENT_MAX_WIDTH) {
            context.sendBroadcast(Intent("com.lidesheng.hyperlyric.REFRESH_ISLAND"))
        }
    }
    val contentOptions = listOf("无内容", "metadata标题", "lyricon标题", "艺术家", "专辑", "lyricon标题-艺术家", "lyricon标题+艺术家", "lyricon标题+艺术家-专辑", "lyricon歌词")

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(backgroundColor = MiuixTheme.colorScheme.surface, tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f)))

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = "超级岛",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = "返回") }
                },
                modifier = Modifier.hazeEffect(hazeState) { style = hazeStyle; blurRadius = 25.dp; noiseFactor = 0f }
            )
        }
    ) { padding ->
        NumberInputDialog(show = showLeftContentWidthDialog, title = "左侧内容长度", label = "范围：0 ~ 100", initialValue = leftContentWidth, min = 0, max = 100, onDismiss = { showLeftContentWidthDialog = false }, onConfirm = { value -> leftContentWidth = value; saveConfig(Constants.KEY_ISLAND_LEFT_CONTENT_MAX_WIDTH, value) })

        NumberInputDialog(show = showRightContentWidthDialog, title = "右侧内容长度", label = "范围：0 ~ 100", initialValue = rightContentWidth, min = 0, max = 100, onDismiss = { showRightContentWidthDialog = false }, onConfirm = { value -> rightContentWidth = value; saveConfig(Constants.KEY_ISLAND_RIGHT_CONTENT_MAX_WIDTH, value) })

        PaddingInputDialog(
            show = showLeftPaddingDialog,
            title = "左侧内容内边距",
            initialLeft = leftPaddingLeft,
            initialRight = leftPaddingRight,
            onDismiss = { showLeftPaddingDialog = false },
            onConfirm = { l, r ->
                leftPaddingLeft = l
                leftPaddingRight = r
                saveConfig(Constants.KEY_ISLAND_LEFT_PADDING_LEFT, l)
                saveConfig(Constants.KEY_ISLAND_LEFT_PADDING_RIGHT, r)
            }
        )

        PaddingInputDialog(
            show = showRightPaddingDialog,
            title = "右侧内容内边距",
            initialLeft = rightPaddingLeft,
            initialRight = rightPaddingRight,
            onDismiss = { showRightPaddingDialog = false },
            onConfirm = { l, r ->
                rightPaddingLeft = l
                rightPaddingRight = r
                saveConfig(Constants.KEY_ISLAND_RIGHT_PADDING_LEFT, l)
                saveConfig(Constants.KEY_ISLAND_RIGHT_PADDING_RIGHT, r)
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollEndHaptic().hazeSource(state = hazeState).overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding())
        ) {
            item {
                SmallTitle(text = "布局", insideMargin = PaddingValues(10.dp, 4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "左侧内容长度",
                        endActions = { Text("$leftContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showLeftContentWidthDialog = true },
                        bottomAction = {
                            Slider(
                                value = leftContentWidth.toFloat(),
                                onValueChange = {
                                    leftContentWidth = it.toInt()
                                    saveConfig(Constants.KEY_ISLAND_LEFT_CONTENT_MAX_WIDTH, leftContentWidth)
                                },
                                valueRange = 0f..100f
                            )
                        }
                    )

                    ArrowPreference(
                        title = "右侧内容长度",
                        endActions = { Text("$rightContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showRightContentWidthDialog = true },
                        bottomAction = {
                            Slider(
                                value = rightContentWidth.toFloat(),
                                onValueChange = {
                                    rightContentWidth = it.toInt()
                                    saveConfig(Constants.KEY_ISLAND_RIGHT_CONTENT_MAX_WIDTH, rightContentWidth)
                                },
                                valueRange = 0f..100f
                            )
                        }
                    )

                    ArrowPreference(
                        title = "左侧内容内边距",
                        endActions = { Text("$leftPaddingLeft,$leftPaddingRight", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showLeftPaddingDialog = true }
                    )
                    ArrowPreference(
                        title = "右侧内容内边距",
                        endActions = { Text("$rightPaddingLeft,$rightPaddingRight", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showRightPaddingDialog = true }
                    )
                }

                SmallTitle(text = "内容", insideMargin = PaddingValues(10.dp, 4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwitchPreference(
                            title = "音频封面",
                            checked = audioCover,
                            onCheckedChange = {
                                audioCover = it
                                saveConfig(Constants.KEY_ISLAND_LEFT_ALBUM, it)
                            }
                        )
                        SwitchPreference(
                            title = "音频律动",
                            checked = audioRhythm,
                            onCheckedChange = {
                                audioRhythm = it
                                saveConfig(Constants.KEY_PROGRESS_COLOR_ENABLED, it)
                            }
                        )
                        OverlayDropdownPreference(
                            title = "超级岛左侧",
                            items = contentOptions,
                            selectedIndex = islandContentLeft,
                            onSelectedIndexChange = {
                                islandContentLeft = it
                                saveConfig(Constants.KEY_ISLAND_CONTENT_LEFT, it)
                            }
                        )
                        OverlayDropdownPreference(
                            title = "超级岛右侧",
                            items = contentOptions,
                            selectedIndex = islandContentRight,
                            onSelectedIndexChange = {
                                islandContentRight = it
                                saveConfig(Constants.KEY_ISLAND_CONTENT_RIGHT, it)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaddingInputDialog(
    show: Boolean,
    title: String,
    initialLeft: Int,
    initialRight: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    if (!show) return
    var leftValue by remember { mutableStateOf(initialLeft.toString()) }
    var rightValue by remember { mutableStateOf(initialRight.toString()) }

    WindowDialog(title = title, show = true, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val filter = { text: String ->
                if (text == "-" || text.isEmpty()) true
                else text.toIntOrNull() != null
            }
            TextField(
                value = leftValue,
                onValueChange = { if (filter(it)) leftValue = it },
                label = "左边距",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = rightValue,
                onValueChange = { if (filter(it)) rightValue = it },
                label = "右边距",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = "确认",
                    onClick = {
                        val l = leftValue.toIntOrNull() ?: 0
                        val r = rightValue.toIntOrNull() ?: 0
                        onConfirm(l.coerceIn(-50, 100), r.coerceIn(-50, 100))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
