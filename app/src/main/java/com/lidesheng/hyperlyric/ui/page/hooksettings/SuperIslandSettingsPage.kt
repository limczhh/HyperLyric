package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.PaddingInputDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
    val lyricMode by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)) }
    var audioCover by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM)) }
    var audioRhythm by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON)) }
    var leftPaddingLeft by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT)) }
    var leftPaddingRight by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT)) }
    var rightPaddingLeft by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT)) }
    var rightPaddingRight by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT)) }
    var leftContentWidth by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH).coerceIn(20, 100)) }
    var rightContentWidth by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH).coerceIn(20, 100)) }
    var afterPauseBehavior by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE, RootConstants.DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE)) }
    var extractGlowColor by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR, RootConstants.DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR)) }
    var progressGlow by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW, RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_GLOW)) }
    var progressStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_PROGRESS_STYLE
            ).coerceIn(
                RootConstants.ISLAND_PROGRESS_STYLE_TOP_CLOCKWISE,
                RootConstants.ISLAND_PROGRESS_STYLE_LEFT_BIDIRECTIONAL
            )
        )
    }

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
        when (value) {
            is Int -> PrefsBridge.putInt(key, value)
            is Boolean -> PrefsBridge.putBoolean(key, value)
        }
    }

    val contentOptions = remember {
        listOf(R.string.option_content_none, R.string.option_content_title, R.string.option_content_artist, R.string.option_content_album, R.string.option_content_title_artist, R.string.option_content_title_plus_artist, R.string.option_content_title_plus_artist_album, R.string.option_content_lyric)
    }.map { stringResource(id = it) }
    val afterPauseOptions = remember {
        listOf(R.string.option_after_pause_default, R.string.option_after_pause_keep)
    }.map { stringResource(id = it) }
    val progressStyleOptions = remember {
        listOf(
            R.string.option_island_progress_top_clockwise,
            R.string.option_island_progress_right_clockwise,
            R.string.option_island_progress_bottom_clockwise,
            R.string.option_island_progress_left_clockwise,
            R.string.option_island_progress_left_bidirectional
        )
    }.map { stringResource(id = it) }

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
        NumberInputDialog(
            show = showLeftContentWidthDialog, 
            title = stringResource(id = R.string.title_left_content_width), 
            label = stringResource(id = R.string.label_content_width_range), 
            initialValue = leftContentWidth, 
            min = 20, 
            max = 100, 
            onDismiss = { showLeftContentWidthDialog = false }, 
            onConfirm = { value -> leftContentWidth = value; saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH, value) }
        )
        NumberInputDialog(
            show = showRightContentWidthDialog, 
            title = stringResource(id = R.string.title_right_content_width), 
            label = stringResource(id = R.string.label_content_width_range), 
            initialValue = rightContentWidth, 
            min = 20, 
            max = 100, 
            onDismiss = { showRightContentWidthDialog = false }, 
            onConfirm = { value -> rightContentWidth = value; saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH, value) }
        )
        PaddingInputDialog(show = showLeftPaddingDialog, title = stringResource(id = R.string.title_left_padding), initialLeft = leftPaddingLeft, initialRight = leftPaddingRight, onDismiss = { showLeftPaddingDialog = false }, onConfirm = { l, r -> leftPaddingLeft = l; leftPaddingRight = r; saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_LEFT, l); saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT, r) })
        PaddingInputDialog(show = showRightPaddingDialog, title = stringResource(id = R.string.title_right_padding), initialLeft = rightPaddingLeft, initialRight = rightPaddingRight, onDismiss = { showRightPaddingDialog = false }, onConfirm = { l, r -> rightPaddingLeft = l; rightPaddingRight = r; saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT, l); saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT, r) })

        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(top = top, start = 0.dp, end = 0.dp, bottom = bottom)
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding,
            ) {
                item(key = "layout_title") { SmallTitle(text = stringResource(id = R.string.title_layout)) }
                item(key = "layout_content") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                        Column {
                            ArrowPreference(
                                title = stringResource(id = R.string.title_left_content_width), 
                                endActions = { Text("$leftContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, 
                                onClick = { showLeftContentWidthDialog = true }
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_right_content_width), 
                                endActions = { Text("$rightContentWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) }, 
                                onClick = { showRightContentWidthDialog = true }
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
                    }
                }
                item(key = "content_title") { SmallTitle(text = stringResource(id = R.string.title_content)) }
                item(key = "content_options") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                        Column {
                            SwitchPreference(title = stringResource(id = R.string.title_audio_cover), checked = audioCover, onCheckedChange = { audioCover = it; saveConfig(RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM, it) })
                            SwitchPreference(title = stringResource(id = R.string.title_audio_rhythm), checked = audioRhythm, onCheckedChange = { audioRhythm = it; saveConfig(RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON, it) })
                            AnimatedVisibility(visible = lyricMode == 0) {
                                Column {
                                    OverlayDropdownPreference(
                                        title = stringResource(id = R.string.title_super_island_left),
                                        items = contentOptions,
                                        selectedIndex = islandContentLeft,
                                        onSelectedIndexChange = {
                                            islandContentLeft = it; saveConfig(
                                            RootConstants.KEY_HOOK_ISLAND_CONTENT_LEFT,
                                            it
                                            )
                                        }
                                    )
                                    OverlayDropdownPreference(
                                        title = stringResource(id = R.string.title_super_island_right),
                                        items = contentOptions,
                                        selectedIndex = islandContentRight,
                                        onSelectedIndexChange = {
                                            islandContentRight = it; saveConfig(
                                            RootConstants.KEY_HOOK_ISLAND_CONTENT_RIGHT,
                                            it
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "special_features_title") { SmallTitle(text = stringResource(id = R.string.title_special_features)) }
                item(key = "special_features_content") {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                        Column {
                            OverlayDropdownPreference(
                                title = stringResource(id = R.string.title_behavior_after_pause),
                                items = afterPauseOptions,
                                selectedIndex = afterPauseBehavior,
                                onSelectedIndexChange = {
                                    afterPauseBehavior = it; saveConfig(
                                    RootConstants.KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE,
                                    it
                                    )
                                }
                            )
                            SwitchPreference(
                                title = stringResource(id = R.string.title_glow_cover_color),
                                checked = extractGlowColor,
                                onCheckedChange = { extractGlowColor = it; saveConfig(RootConstants.KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR, it) }
                            )
                            SwitchPreference(
                                title = stringResource(id = R.string.title_island_progress_glow),
                                checked = progressGlow,
                                onCheckedChange = { progressGlow = it; saveConfig(RootConstants.KEY_HOOK_ISLAND_PROGRESS_GLOW, it) }
                            )
                            AnimatedVisibility(visible = progressGlow) {
                                Column {
                                    OverlayDropdownPreference(
                                        title = stringResource(id = R.string.title_island_progress_style),
                                        items = progressStyleOptions,
                                        selectedIndex = progressStyle,
                                        onSelectedIndexChange = {
                                            progressStyle = it
                                            saveConfig(
                                                RootConstants.KEY_HOOK_ISLAND_PROGRESS_STYLE,
                                                it
                                            )
                                        }
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
