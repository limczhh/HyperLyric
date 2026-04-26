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
import androidx.compose.ui.res.stringResource
import com.lidesheng.hyperlyric.R

@Composable
fun SuperIslandSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    
    var islandContentLeft by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_LEFT)) }
    var islandContentRight by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_CONTENT_RIGHT)) }
    
    var audioCover by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM)) }
    var audioRhythm by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON)) }
    
    var leftPaddingLeft by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT)) }
    var leftPaddingRight by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT)) }
    var rightPaddingLeft by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT)) }
    var rightPaddingRight by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT)) }
    var leftContentWidth by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH)) }
    var rightContentWidth by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH)) }
    var afterPauseBehavior by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)) }

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
        ConfigSync.syncPreference(UIConstants.PREF_NAME, key, value)
        val refreshKeys = setOf(
            RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )
        if (key in refreshKeys) {
            context.sendBroadcast(Intent("com.lidesheng.hyperlyric.REFRESH_ISLAND"))
        }
    }
    val contentOptionResList = listOf(
        R.string.option_content_none,
        R.string.option_content_metadata_title,
        R.string.option_content_lyricon_title,
        R.string.option_content_artist,
        R.string.option_content_album,
        R.string.option_content_lyricon_title_artist,
        R.string.option_content_lyricon_title_plus_artist,
        R.string.option_content_lyricon_title_plus_artist_album,
        R.string.option_content_lyricon_lyric
    )
    val contentOptions = contentOptionResList.map { stringResource(id = it) }

    val afterPauseOptionResList = listOf(
        R.string.option_after_pause_default,
        R.string.option_after_pause_keep
    )
    val afterPauseOptions = afterPauseOptionResList.map { stringResource(id = it) }


    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(backgroundColor = MiuixTheme.colorScheme.surface, tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f)))

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = stringResource(id = R.string.title_super_island),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back)) }
                },
                modifier = Modifier.hazeEffect(hazeState) { style = hazeStyle; blurRadius = 25.dp; noiseFactor = 0f }
            )
        }
    ) { padding ->
        NumberInputDialog(show = showLeftContentWidthDialog, title = stringResource(id = R.string.title_left_content_width), label = stringResource(id = R.string.label_content_width_range), initialValue = leftContentWidth, min = 0, max = 100, onDismiss = { showLeftContentWidthDialog = false }, onConfirm = { value -> leftContentWidth = value; saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, value) })

        NumberInputDialog(show = showRightContentWidthDialog, title = stringResource(id = R.string.title_right_content_width), label = stringResource(id = R.string.label_content_width_range), initialValue = rightContentWidth, min = 0, max = 100, onDismiss = { showRightContentWidthDialog = false }, onConfirm = { value -> rightContentWidth = value; saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, value) })

        PaddingInputDialog(
            show = showLeftPaddingDialog,
            title = stringResource(id = R.string.title_left_padding),
            initialLeft = leftPaddingLeft,
            initialRight = leftPaddingRight,
            onDismiss = { showLeftPaddingDialog = false },
            onConfirm = { l, r ->
                leftPaddingLeft = l
                leftPaddingRight = r
                saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, l)
                saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, r)
            }
        )

        PaddingInputDialog(
            show = showRightPaddingDialog,
            title = stringResource(id = R.string.title_right_padding),
            initialLeft = rightPaddingLeft,
            initialRight = rightPaddingRight,
            onDismiss = { showRightPaddingDialog = false },
            onConfirm = { l, r ->
                rightPaddingLeft = l
                rightPaddingRight = r
                saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, l)
                saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, r)
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollEndHaptic().hazeSource(state = hazeState).overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding())
        ) {
            item {
                SmallTitle(text = stringResource(id = R.string.title_layout), insideMargin = PaddingValues(10.dp, 4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = stringResource(id = R.string.title_left_content_width),
                        endActions = { Text("$leftContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showLeftContentWidthDialog = true },
                        bottomAction = {
                            Slider(
                                value = leftContentWidth.toFloat(),
                                onValueChange = {
                                    leftContentWidth = it.toInt()
                                    saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, leftContentWidth)
                                },
                                valueRange = 0f..100f
                            )
                        }
                    )

                    ArrowPreference(
                        title = stringResource(id = R.string.title_right_content_width),
                        endActions = { Text("$rightContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showRightContentWidthDialog = true },
                        bottomAction = {
                            Slider(
                                value = rightContentWidth.toFloat(),
                                onValueChange = {
                                    rightContentWidth = it.toInt()
                                    saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, rightContentWidth)
                                },
                                valueRange = 0f..100f
                            )
                        }
                    )

                    ArrowPreference(
                        title = stringResource(id = R.string.title_left_padding),
                        endActions = { Text(stringResource(id = R.string.format_padding_pair, leftPaddingLeft, leftPaddingRight), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showLeftPaddingDialog = true }
                    )
                    ArrowPreference(
                        title = stringResource(id = R.string.title_right_padding),
                        endActions = { Text(stringResource(id = R.string.format_padding_pair, rightPaddingLeft, rightPaddingRight), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = { showRightPaddingDialog = true }
                    )
                }

                SmallTitle(text = stringResource(id = R.string.title_content), insideMargin = PaddingValues(10.dp, 4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_audio_cover),
                            checked = audioCover,
                            onCheckedChange = {
                                audioCover = it
                                saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, it)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.title_audio_rhythm),
                            checked = audioRhythm,
                            onCheckedChange = {
                                audioRhythm = it
                                saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, it)
                            }
                        )
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.title_super_island_left),
                            items = contentOptions,
                            selectedIndex = islandContentLeft,
                            onSelectedIndexChange = {
                                islandContentLeft = it
                                saveConfig(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, it)
                            }
                        )
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.title_super_island_right),
                            items = contentOptions,
                            selectedIndex = islandContentRight,
                            onSelectedIndexChange = {
                                islandContentRight = it
                                saveConfig(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, it)
                            }
                        )
                    }
                }
                SmallTitle(text = stringResource(id = R.string.title_special_features), insideMargin = PaddingValues(10.dp, 4.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = stringResource(id = R.string.title_behavior_after_pause),
                        items = afterPauseOptions,
                        selectedIndex = afterPauseBehavior,
                        onSelectedIndexChange = {
                            afterPauseBehavior = it
                            saveConfig(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, it)
                        }
                    )
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
                label = stringResource(id = R.string.label_left_padding),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = rightValue,
                onValueChange = { if (filter(it)) rightValue = it },
                label = stringResource(id = R.string.label_right_padding),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(text = stringResource(id = R.string.cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(id = R.string.confirm),
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
