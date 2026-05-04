@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import android.content.Intent
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.PaddingInputDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
            RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON,
            RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT,
            RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH,
            RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE
        )
        if (key in refreshKeys) context.sendBroadcast(Intent("com.lidesheng.hyperlyric.REFRESH_ISLAND"))
    }

    val contentOptionResList = listOf(R.string.option_content_none, R.string.option_content_metadata_title, R.string.option_content_lyricon_title, R.string.option_content_artist, R.string.option_content_album, R.string.option_content_lyricon_title_artist, R.string.option_content_lyricon_title_plus_artist, R.string.option_content_lyricon_title_plus_artist_album, R.string.option_content_lyricon_lyric)
    val contentOptions = contentOptionResList.map { stringResource(id = it) }
    val afterPauseOptionResList = listOf(R.string.option_after_pause_default, R.string.option_after_pause_keep)
    val afterPauseOptions = afterPauseOptionResList.map { stringResource(id = it) }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_super_island),
                    subtitle = stringResource(id = R.string.subtitle_super_island),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back)) }
                    }
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        NumberInputDialog(show = showLeftContentWidthDialog, title = stringResource(id = R.string.title_left_content_width), label = stringResource(id = R.string.label_content_width_range), initialValue = leftContentWidth, min = 0, max = 100, onDismiss = { showLeftContentWidthDialog = false }, onConfirm = { value -> leftContentWidth = value; saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, value) })
        NumberInputDialog(show = showRightContentWidthDialog, title = stringResource(id = R.string.title_right_content_width), label = stringResource(id = R.string.label_content_width_range), initialValue = rightContentWidth, min = 0, max = 100, onDismiss = { showRightContentWidthDialog = false }, onConfirm = { value -> rightContentWidth = value; saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, value) })
        PaddingInputDialog(show = showLeftPaddingDialog, title = stringResource(id = R.string.title_left_padding), initialLeft = leftPaddingLeft, initialRight = leftPaddingRight, onDismiss = { showLeftPaddingDialog = false }, onConfirm = { l, r -> leftPaddingLeft = l; leftPaddingRight = r; saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, l); saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, r) })
        PaddingInputDialog(show = showRightPaddingDialog, title = stringResource(id = R.string.title_right_padding), initialLeft = rightPaddingLeft, initialRight = rightPaddingRight, onDismiss = { showRightPaddingDialog = false }, onConfirm = { l, r -> rightPaddingLeft = l; rightPaddingRight = r; saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, l); saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, r) })

        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(top = top, start = 12.dp, end = 12.dp, bottom = bottom)
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(true, true, topAppBarScrollBehavior),
                contentPadding = contentPadding,
            ) {
                superIslandSettingsSections(
                    islandContentLeft, islandContentRight, audioCover, audioRhythm,
                    leftPaddingLeft, leftPaddingRight, rightPaddingLeft, rightPaddingRight,
                    leftContentWidth, rightContentWidth, afterPauseBehavior,
                    contentOptions, afterPauseOptions,
                    { showLeftContentWidthDialog = true }, { showRightContentWidthDialog = true },
                    { showLeftPaddingDialog = true }, { showRightPaddingDialog = true },
                    ::saveConfig
                )
            }
            VerticalScrollBar(adapter = rememberScrollBarAdapter(lazyListState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), trackPadding = contentPadding)
        }
    }
}

private fun LazyListScope.superIslandSettingsSections(
    islandContentLeft: Int, islandContentRight: Int,
    audioCover: Boolean, audioRhythm: Boolean,
    leftPaddingLeft: Int, leftPaddingRight: Int,
    rightPaddingLeft: Int, rightPaddingRight: Int,
    leftContentWidth: Int, rightContentWidth: Int,
    afterPauseBehavior: Int,
    contentOptions: List<String>, afterPauseOptions: List<String>,
    onLeftContentWidthClick: () -> Unit, onRightContentWidthClick: () -> Unit,
    onLeftPaddingClick: () -> Unit, onRightPaddingClick: () -> Unit,
    saveConfig: (String, Any) -> Unit
) {
    item(key = "layout_title") { SmallTitle(text = stringResource(id = R.string.title_layout), insideMargin = PaddingValues(10.dp, 4.dp)) }
    item(key = "layout_content") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ArrowPreference(title = stringResource(id = R.string.title_left_content_width), endActions = { Text("$leftContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = onLeftContentWidthClick, bottomAction = { Slider(value = leftContentWidth.toFloat(), onValueChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, it.toInt()) }, valueRange = 0f..100f) })
                ArrowPreference(title = stringResource(id = R.string.title_right_content_width), endActions = { Text("$rightContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = onRightContentWidthClick, bottomAction = { Slider(value = rightContentWidth.toFloat(), onValueChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, it.toInt()) }, valueRange = 0f..100f) })
                ArrowPreference(title = stringResource(id = R.string.title_left_padding), endActions = { Text(stringResource(id = R.string.format_padding_pair, leftPaddingLeft, leftPaddingRight), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = onLeftPaddingClick)
                ArrowPreference(title = stringResource(id = R.string.title_right_padding), endActions = { Text(stringResource(id = R.string.format_padding_pair, rightPaddingLeft, rightPaddingRight), fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, onClick = onRightPaddingClick)
            }
        }
    }
    item(key = "content_title") { SmallTitle(text = stringResource(id = R.string.title_content), insideMargin = PaddingValues(10.dp, 4.dp)) }
    item(key = "content_options") {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                SwitchPreference(title = stringResource(id = R.string.title_audio_cover), checked = audioCover, onCheckedChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, it) })
                SwitchPreference(title = stringResource(id = R.string.title_audio_rhythm), checked = audioRhythm, onCheckedChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, it) })
                OverlayDropdownPreference(title = stringResource(id = R.string.title_super_island_left), items = contentOptions, selectedIndex = islandContentLeft, onSelectedIndexChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT, it) })
                OverlayDropdownPreference(title = stringResource(id = R.string.title_super_island_right), items = contentOptions, selectedIndex = islandContentRight, onSelectedIndexChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT, it) })
            }
        }
    }
    item(key = "special_features_title") { SmallTitle(text = stringResource(id = R.string.title_special_features), insideMargin = PaddingValues(10.dp, 4.dp)) }
    item(key = "special_features_content") {
        Card(modifier = Modifier.fillMaxWidth()) {
            OverlayDropdownPreference(title = stringResource(id = R.string.title_behavior_after_pause), items = afterPauseOptions, selectedIndex = afterPauseBehavior, onSelectedIndexChange = { saveConfig(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, it) })
        }
    }
}
