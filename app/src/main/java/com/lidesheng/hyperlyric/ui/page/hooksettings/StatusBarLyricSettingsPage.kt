package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.SharedPreferences
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.display.LyricDisplaySettings
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

private val STATUS_BAR_LYRIC_PADDING_INPUT_PATTERN = Regex("-?\\d*(\\.\\d*)?")

private data class StatusBarLyricGestureDropdown(
    val title: String,
    val items: List<String>,
    val values: List<Int>,
    val selectedValue: Int,
    val onSelectedValueChange: (Int) -> Unit,
)

@Composable
fun StatusBarLyricSettingsPage() {
    val sharedPrefs = rememberHookPrefs()
    val initialized = remember(sharedPrefs) {
        StatusBarLyricPreferences.initializeFromShared(sharedPrefs)
    }
    LaunchedEffect(sharedPrefs, initialized) {
        if (initialized) withContext(Dispatchers.IO) { PrefsBridge.syncAllToRemote() }
    }

    val prefs = rememberHookPrefs(statusBarLyrics = true)
    val saveConfig = rememberHookConfigSaver(prefs)
    val portraitWidthLimit = RootConstants.STATUS_BAR_LYRIC_PORTRAIT_MAX_WIDTH_DP
    val landscapeWidthLimit = RootConstants.STATUS_BAR_LYRIC_LANDSCAPE_MAX_WIDTH_DP

    var statusBarLyricsEnabled by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                StatusBarLyricPreferences.KEY_ENABLED,
                StatusBarLyricPreferences.DEFAULT_ENABLED
            )
        )
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == StatusBarLyricPreferences.KEY_ENABLED) {
                statusBarLyricsEnabled = changedPrefs.getBoolean(
                    StatusBarLyricPreferences.KEY_ENABLED,
                    StatusBarLyricPreferences.DEFAULT_ENABLED,
                )
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var insertionOrder by remember(prefs) {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_INSERTION_BEFORE_CLOCK,
                RootConstants.STATUS_BAR_LYRIC_INSERTION_AFTER_CLOCK,
            )
        )
    }
    var iconStyle by remember(prefs) {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_STYLE,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_STYLE,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_ICON_MUSIC_COVER,
                RootConstants.STATUS_BAR_LYRIC_ICON_MONOCHROME,
            )
        )
    }
    var iconEnabled by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED,
            )
        )
    }
    var iconOrder by remember(prefs) {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ORDER,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ORDER,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC,
                RootConstants.STATUS_BAR_LYRIC_ICON_AFTER_LYRIC,
            )
        )
    }
    var iconSizeDp by remember(prefs) {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_ICON_MIN_SIZE_DP,
                RootConstants.STATUS_BAR_LYRIC_ICON_MAX_SIZE_DP,
            )
        )
    }
    var portraitMaxWidth by remember(prefs, portraitWidthLimit) {
        mutableIntStateOf(
            readDynamicWidthMaxDp(
                prefs = prefs,
                maxKey = RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH,
                maxWidthDp = portraitWidthLimit,
                defaultMaxWidthDp =
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH,
            )
        )
    }
    var landscapeMaxWidth by remember(prefs, landscapeWidthLimit) {
        mutableIntStateOf(
            readDynamicWidthMaxDp(
                prefs = prefs,
                maxKey = RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH,
                maxWidthDp = landscapeWidthLimit,
                defaultMaxWidthDp =
                    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH,
            )
        )
    }
    var paddingLeftDp by remember(prefs) {
        mutableFloatStateOf(readStatusBarLyricPaddingDp(
            prefs,
            RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP,
        ))
    }
    var paddingRightDp by remember(prefs) {
        mutableFloatStateOf(readStatusBarLyricPaddingDp(
            prefs,
            RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP,
        ))
    }
    var showPaddingDialog by remember { mutableStateOf(false) }
    var showPortraitMaxWidthDialog by remember { mutableStateOf(false) }
    var showLandscapeMaxWidthDialog by remember { mutableStateOf(false) }
    var clockHideBehavior by remember(prefs) {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_NONE,
                RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT,
            )
        )
    }
    var islandHideBehavior by remember(prefs) {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
            ).coerceIn(
                RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
                RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS,
            )
        )
    }
    var adjustWidthForSuperIsland by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
            )
        )
    }
    var hideOnLockScreen by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN,
            )
        )
    }
    val pressActionValues = listOf(
        RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_NONE,
        RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK,
        RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_CLOCK,
        RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP,
    )
    val pressActionItems = listOf(
        stringResource(R.string.option_status_bar_lyric_gesture_none),
        stringResource(R.string.option_status_bar_lyric_gesture_toggle_playback),
        stringResource(R.string.option_status_bar_lyric_gesture_temporary_clock),
        stringResource(R.string.option_click_open_media),
    )
    val swipeActionValues = listOf(
        RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE,
        RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS,
        RootConstants.STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT,
    )
    val swipeActionItems = listOf(
        stringResource(R.string.option_status_bar_lyric_gesture_swipe_none),
        stringResource(R.string.option_status_bar_lyric_gesture_previous_track),
        stringResource(R.string.option_status_bar_lyric_gesture_next_track),
    )
    var doubleTapAction by remember(prefs) {
        mutableIntStateOf(readStatusBarLyricGestureValue(
            prefs,
            RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION,
            pressActionValues,
            RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION,
        ))
    }
    var longPressAction by remember(prefs) {
        mutableIntStateOf(readStatusBarLyricGestureValue(
            prefs,
            RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION,
            pressActionValues,
            RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION,
        ))
    }
    var swipeLeftAction by remember(prefs) {
        mutableIntStateOf(readStatusBarLyricGestureValue(
            prefs,
            RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION,
            swipeActionValues,
            RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION,
        ))
    }
    var swipeRightAction by remember(prefs) {
        mutableIntStateOf(readStatusBarLyricGestureValue(
            prefs,
            RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION,
            swipeActionValues,
            RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION,
        ))
    }
    var gestureHapticFeedbackEnabled by remember(prefs) {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK,
                RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK,
            )
        )
    }
    val interactionDropdowns = listOf(
        StatusBarLyricGestureDropdown(
            title = stringResource(R.string.title_status_bar_lyric_double_tap),
            items = pressActionItems,
            values = pressActionValues,
            selectedValue = doubleTapAction,
            onSelectedValueChange = { action ->
                doubleTapAction = action
                saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION, action)
            },
        ),
        StatusBarLyricGestureDropdown(
            title = stringResource(R.string.title_status_bar_lyric_long_press),
            items = pressActionItems,
            values = pressActionValues,
            selectedValue = longPressAction,
            onSelectedValueChange = { action ->
                longPressAction = action
                saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION, action)
            },
        ),
        StatusBarLyricGestureDropdown(
            title = stringResource(R.string.title_status_bar_lyric_swipe_left),
            items = swipeActionItems,
            values = swipeActionValues,
            selectedValue = swipeLeftAction,
            onSelectedValueChange = { action ->
                swipeLeftAction = action
                saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION, action)
            },
        ),
        StatusBarLyricGestureDropdown(
            title = stringResource(R.string.title_status_bar_lyric_swipe_right),
            items = swipeActionItems,
            values = swipeActionValues,
            selectedValue = swipeRightAction,
            onSelectedValueChange = { action ->
                swipeRightAction = action
                saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION, action)
            },
        ),
    )

    val tabs = listOf(
        stringResource(R.string.title_status_bar_lyric_config),
        stringResource(R.string.title_custom_config),
    )
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val basicConfigListState = rememberLazyListState()
    val textStyleListState = rememberLazyListState()

    LyricDisplaySettings(statusBarLyrics = true) { displaySections ->
        XposedLyricSettingPage(
            title = stringResource(R.string.title_status_bar_lyrics),
            topBarBottomContent = {
                Column {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.title_enable),
                            checked = statusBarLyricsEnabled,
                            onCheckedChange = { enabled ->
                                statusBarLyricsEnabled = enabled
                                saveConfig(StatusBarLyricPreferences.KEY_ENABLED, enabled)
                            },
                        )
                    }
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = pagerState.currentPage,
                        onTabSelected = { index ->
                            coroutineScope.launch { pagerState.scrollToPage(index) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent),
                    )
                }
            },
            pageContent = { innerPadding, scrollBehavior ->
                val topPadding = innerPadding.calculateTopPadding()
                val bottomPadding = innerPadding.calculateBottomPadding()
                val pageContentPadding = remember(topPadding, bottomPadding) {
                    PaddingValues(top = topPadding, bottom = bottomPadding + 16.dp)
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val listState = if (page == 0) {
                        basicConfigListState
                    } else {
                        textStyleListState
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.pageScrollModifiers(
                            enableScrollEndHaptic = true,
                            showTopAppBar = true,
                            topAppBarScrollBehavior = scrollBehavior,
                        ),
                        contentPadding = pageContentPadding,
                    ) {
                        if (page == 0) {
                            statusBarLyricLayoutSections(
                                insertionOrder = insertionOrder,
                                onInsertionOrderChange = { order ->
                                    insertionOrder = order
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER,
                                        order,
                                    )
                                },
                                portraitWidthLimit = portraitWidthLimit,
                                portraitMaxWidth = portraitMaxWidth,
                                onPortraitMaxWidthChange = { portraitMaxWidth = it },
                                onPortraitMaxWidthClick = { showPortraitMaxWidthDialog = true },
                                onPortraitMaxWidthCommit = {
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH,
                                        portraitMaxWidth,
                                    )
                                },
                                landscapeWidthLimit = landscapeWidthLimit,
                                landscapeMaxWidth = landscapeMaxWidth,
                                onLandscapeMaxWidthChange = { landscapeMaxWidth = it },
                                onLandscapeMaxWidthClick = { showLandscapeMaxWidthDialog = true },
                                onLandscapeMaxWidthCommit = {
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH,
                                        landscapeMaxWidth,
                                    )
                                },
                                paddingLeftDp = paddingLeftDp,
                                paddingRightDp = paddingRightDp,
                                onPaddingClick = { showPaddingDialog = true },
                                adjustWidthForSuperIsland = adjustWidthForSuperIsland,
                                onAdjustWidthForSuperIslandChange = { enabled ->
                                    adjustWidthForSuperIsland = enabled
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND,
                                        enabled,
                                    )
                                },
                            )
                            statusBarLyricVisibilitySections(
                                clockHideBehavior = clockHideBehavior,
                                onClockHideBehaviorChange = { behavior ->
                                    clockHideBehavior = behavior
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
                                        behavior,
                                    )
                                },
                                islandHideBehavior = islandHideBehavior,
                                onIslandHideBehaviorChange = { behavior ->
                                    islandHideBehavior = behavior
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR,
                                        behavior,
                                    )
                                },
                                hideOnLockScreen = hideOnLockScreen,
                                onHideOnLockScreenChange = { hidden ->
                                    hideOnLockScreen = hidden
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN,
                                        hidden,
                                    )
                                },
                            )
                            statusBarLyricInteractionSection(
                                dropdowns = interactionDropdowns,
                                hapticFeedbackEnabled = gestureHapticFeedbackEnabled,
                                onHapticFeedbackEnabledChange = { enabled ->
                                    gestureHapticFeedbackEnabled = enabled
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK,
                                        enabled,
                                    )
                                },
                            )
                        } else {
                            statusBarLyricIconSections(
                                iconEnabled = iconEnabled,
                                onIconEnabledChange = { enabled ->
                                    iconEnabled = enabled
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED,
                                        enabled,
                                    )
                                },
                                iconStyle = iconStyle,
                                onIconStyleChange = { style ->
                                    iconStyle = style
                                    saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_STYLE, style)
                                },
                                iconOrder = iconOrder,
                                onIconOrderChange = { order ->
                                    iconOrder = order
                                    saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ORDER, order)
                                },
                                iconSizeDp = iconSizeDp,
                                onIconSizeChange = { iconSizeDp = it },
                                onIconSizeCommit = {
                                    saveConfig(
                                        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP,
                                        iconSizeDp,
                                    )
                                },
                            )
                            displaySections()
                        }
                    }
                }
            },
        )
    }

    NumberInputDialog(
        show = showPortraitMaxWidthDialog,
        title = stringResource(R.string.title_status_bar_lyric_portrait_width),
        label = stringResource(
            R.string.label_status_bar_lyric_width_range,
            0,
            portraitWidthLimit,
        ),
        initialValue = portraitMaxWidth,
        min = 0,
        max = portraitWidthLimit,
        onDismiss = { showPortraitMaxWidthDialog = false },
        onConfirm = { value ->
            portraitMaxWidth = value
            saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH, value)
        },
    )
    NumberInputDialog(
        show = showLandscapeMaxWidthDialog,
        title = stringResource(R.string.title_status_bar_lyric_landscape_width),
        label = stringResource(
            R.string.label_status_bar_lyric_width_range,
            0,
            landscapeWidthLimit,
        ),
        initialValue = landscapeMaxWidth,
        min = 0,
        max = landscapeWidthLimit,
        onDismiss = { showLandscapeMaxWidthDialog = false },
        onConfirm = { value ->
            landscapeMaxWidth = value
            saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH, value)
        },
    )
    StatusBarLyricPaddingDialog(
        show = showPaddingDialog,
        leftDp = paddingLeftDp,
        rightDp = paddingRightDp,
        onDismiss = { showPaddingDialog = false },
        onConfirm = { left, right ->
            paddingLeftDp = left
            paddingRightDp = right
            saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP, left)
            saveConfig(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP, right)
        },
    )
}

@Suppress("LongParameterList")
private fun LazyListScope.statusBarLyricIconSections(
    iconEnabled: Boolean,
    onIconEnabledChange: (Boolean) -> Unit,
    iconStyle: Int,
    onIconStyleChange: (Int) -> Unit,
    iconOrder: Int,
    onIconOrderChange: (Int) -> Unit,
    iconSizeDp: Int,
    onIconSizeChange: (Int) -> Unit,
    onIconSizeCommit: () -> Unit,
) {
    item(key = "status_bar_lyric_icon_title") {
        SmallTitle(text = stringResource(R.string.title_status_bar_lyric_icon))
    }
    item(key = "status_bar_lyric_icon") {
        val styles = listOf(
            RootConstants.STATUS_BAR_LYRIC_ICON_MUSIC_COVER,
            RootConstants.STATUS_BAR_LYRIC_ICON_CIRCLE_COVER,
            RootConstants.STATUS_BAR_LYRIC_ICON_ROTATING_COVER,
            RootConstants.STATUS_BAR_LYRIC_ICON_APP,
            RootConstants.STATUS_BAR_LYRIC_ICON_MONOCHROME,
        )
        val styleItems = listOf(
            stringResource(R.string.option_status_bar_lyric_icon_music_cover),
            stringResource(R.string.option_status_bar_lyric_icon_circle_cover),
            stringResource(R.string.option_status_bar_lyric_icon_rotating_cover),
            stringResource(R.string.option_status_bar_lyric_icon_app),
            stringResource(R.string.option_status_bar_lyric_icon_monochrome),
        )
        val orderItems = listOf(
            stringResource(R.string.option_status_bar_lyric_icon_before_lyric),
            stringResource(R.string.option_status_bar_lyric_icon_after_lyric),
        )
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                SwitchPreference(
                    title = stringResource(R.string.title_enable),
                    checked = iconEnabled,
                    onCheckedChange = onIconEnabledChange,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_status_bar_lyric_icon_style),
                    items = styleItems,
                    selectedIndex = styles.indexOf(iconStyle).coerceAtLeast(0),
                    onSelectedIndexChange = { index ->
                        styles.getOrNull(index)?.let(onIconStyleChange)
                    },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_status_bar_lyric_insertion_order),
                    items = orderItems,
                    selectedIndex = iconOrder.coerceIn(0, 1),
                    onSelectedIndexChange = { index ->
                        if (index == RootConstants.STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC ||
                            index == RootConstants.STATUS_BAR_LYRIC_ICON_AFTER_LYRIC
                        ) onIconOrderChange(index)
                    },
                )
                StatusBarLyricIconSizeControl(
                    sizeDp = iconSizeDp,
                    onSizeChange = onIconSizeChange,
                    onSizeCommit = onIconSizeCommit,
                )
            }
        }
    }
}

@Composable
private fun StatusBarLyricIconSizeControl(
    sizeDp: Int,
    onSizeChange: (Int) -> Unit,
    onSizeCommit: () -> Unit,
) {
    ArrowPreference(
        title = stringResource(R.string.title_status_bar_lyric_icon_size),
        endActions = {
            Text(
                stringResource(R.string.format_status_bar_lyric_icon_size, sizeDp),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = {},
        bottomAction = {
            Slider(
                value = sizeDp.coerceIn(
                    RootConstants.STATUS_BAR_LYRIC_ICON_MIN_SIZE_DP,
                    RootConstants.STATUS_BAR_LYRIC_ICON_MAX_SIZE_DP,
                ).toFloat(),
                onValueChange = { value ->
                    onSizeChange(
                        value.roundToInt().coerceIn(
                            RootConstants.STATUS_BAR_LYRIC_ICON_MIN_SIZE_DP,
                            RootConstants.STATUS_BAR_LYRIC_ICON_MAX_SIZE_DP,
                        )
                    )
                },
                valueRange = RootConstants.STATUS_BAR_LYRIC_ICON_MIN_SIZE_DP.toFloat()..
                        RootConstants.STATUS_BAR_LYRIC_ICON_MAX_SIZE_DP.toFloat(),
                steps = 0,
                onValueChangeFinished = onSizeCommit,
            )
        },
    )
}

private fun LazyListScope.statusBarLyricInteractionSection(
    dropdowns: List<StatusBarLyricGestureDropdown>,
    hapticFeedbackEnabled: Boolean,
    onHapticFeedbackEnabledChange: (Boolean) -> Unit,
) {
    item(key = "status_bar_lyric_interaction_title") {
        SmallTitle(text = stringResource(R.string.title_interaction))
    }
    item(key = "status_bar_lyric_interaction") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                SwitchPreference(
                    title = stringResource(R.string.title_status_bar_lyric_haptic_feedback),
                    checked = hapticFeedbackEnabled,
                    onCheckedChange = onHapticFeedbackEnabledChange,
                )
                dropdowns.forEach { dropdown ->
                    OverlayDropdownPreference(
                        title = dropdown.title,
                        items = dropdown.items,
                        selectedIndex = dropdown.values.indexOf(dropdown.selectedValue)
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            dropdown.values.getOrNull(index)?.let(
                                dropdown.onSelectedValueChange
                            )
                        },
                    )
                }
            }
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.statusBarLyricLayoutSections(
    insertionOrder: Int,
    onInsertionOrderChange: (Int) -> Unit,
    portraitWidthLimit: Int,
    portraitMaxWidth: Int,
    onPortraitMaxWidthChange: (Int) -> Unit,
    onPortraitMaxWidthClick: () -> Unit,
    onPortraitMaxWidthCommit: () -> Unit,
    landscapeWidthLimit: Int,
    landscapeMaxWidth: Int,
    onLandscapeMaxWidthChange: (Int) -> Unit,
    onLandscapeMaxWidthClick: () -> Unit,
    onLandscapeMaxWidthCommit: () -> Unit,
    paddingLeftDp: Float,
    paddingRightDp: Float,
    onPaddingClick: () -> Unit,
    adjustWidthForSuperIsland: Boolean,
    onAdjustWidthForSuperIslandChange: (Boolean) -> Unit,
) {
    item(key = "status_bar_lyric_layout_title") {
        SmallTitle(text = stringResource(R.string.title_status_bar_lyric_layout))
    }

    item(key = "status_bar_lyric_layout") {
        val insertionOptions = listOf(
            stringResource(R.string.option_status_bar_lyric_before_clock),
            stringResource(R.string.option_status_bar_lyric_after_clock),
        )
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_status_bar_lyric_insertion_order),
                    items = insertionOptions,
                    selectedIndex = insertionOrder,
                    onSelectedIndexChange = onInsertionOrderChange,
                )
                StatusBarLyricWidthControl(
                    title = stringResource(R.string.title_status_bar_lyric_portrait_width),
                    maxWidth = portraitMaxWidth,
                    widthLimit = portraitWidthLimit,
                    onMaxWidthChange = onPortraitMaxWidthChange,
                    onMaxWidthClick = onPortraitMaxWidthClick,
                    onMaxWidthCommit = onPortraitMaxWidthCommit,
                )
                StatusBarLyricWidthControl(
                    title = stringResource(R.string.title_status_bar_lyric_landscape_width),
                    maxWidth = landscapeMaxWidth,
                    widthLimit = landscapeWidthLimit,
                    onMaxWidthChange = onLandscapeMaxWidthChange,
                    onMaxWidthClick = onLandscapeMaxWidthClick,
                    onMaxWidthCommit = onLandscapeMaxWidthCommit,
                )
                ArrowPreference(
                    title = stringResource(R.string.title_status_bar_lyric_padding),
                    summary = stringResource(
                        R.string.summary_status_bar_lyric_padding_values,
                        formatStatusBarLyricPaddingDp(paddingLeftDp),
                        formatStatusBarLyricPaddingDp(paddingRightDp),
                    ),
                    onClick = onPaddingClick,
                )
                SwitchPreference(
                    title = stringResource(R.string.title_status_bar_lyric_adjust_width_for_super_island),
                    checked = adjustWidthForSuperIsland,
                    onCheckedChange = onAdjustWidthForSuperIslandChange,
                )
            }
        }
    }
}

private fun LazyListScope.statusBarLyricVisibilitySections(
    clockHideBehavior: Int,
    onClockHideBehaviorChange: (Int) -> Unit,
    islandHideBehavior: Int,
    onIslandHideBehaviorChange: (Int) -> Unit,
    hideOnLockScreen: Boolean,
    onHideOnLockScreenChange: (Boolean) -> Unit,
) {
    item(key = "status_bar_lyric_visibility_title") {
        SmallTitle(text = stringResource(R.string.title_status_bar_lyric_behavior))
    }

    item(key = "status_bar_lyric_visibility") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_status_bar_lyric_clock_hide_behavior),
                    items = listOf(
                        stringResource(R.string.option_status_bar_lyric_clock_hide_never),
                        stringResource(R.string.option_status_bar_lyric_clock_hide_playing),
                        stringResource(R.string.option_status_bar_lyric_clock_hide_island),
                    ),
                    selectedIndex = clockHideBehavior,
                    onSelectedIndexChange = onClockHideBehaviorChange,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_status_bar_lyric_island_hide_behavior),
                    items = listOf(
                        stringResource(R.string.option_status_bar_lyric_island_hide_none),
                        stringResource(R.string.option_status_bar_lyric_island_hide_playing),
                        stringResource(R.string.option_status_bar_lyric_island_hide_always),
                    ),
                    selectedIndex = islandHideBehavior,
                    onSelectedIndexChange = onIslandHideBehaviorChange,
                )
                SwitchPreference(
                    title = stringResource(R.string.title_status_bar_lyric_hide_lock_screen),
                    checked = hideOnLockScreen,
                    onCheckedChange = onHideOnLockScreenChange,
                )
            }
        }
    }
}

@Composable
private fun StatusBarLyricWidthControl(
    title: String,
    maxWidth: Int,
    widthLimit: Int,
    onMaxWidthChange: (Int) -> Unit,
    onMaxWidthClick: () -> Unit,
    onMaxWidthCommit: () -> Unit,
) {
    ArrowPreference(
        title = title,
        endActions = {
            Text(
                stringResource(R.string.format_status_bar_lyric_width_value, maxWidth),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        onClick = onMaxWidthClick,
        bottomAction = {
            Slider(
                value = maxWidth.coerceIn(0, widthLimit).toFloat(),
                onValueChange = { value ->
                    onMaxWidthChange(value.roundToInt().coerceIn(0, widthLimit))
                },
                valueRange = 0f..widthLimit.toFloat(),
                steps = 0,
                onValueChangeFinished = onMaxWidthCommit,
            )
        },
    )
}

private fun readDynamicWidthMaxDp(
    prefs: android.content.SharedPreferences,
    maxKey: String,
    maxWidthDp: Int,
    defaultMaxWidthDp: Int,
): Int {
    val storedMax = prefs.getInt(
        maxKey,
        defaultMaxWidthDp,
    )
    return if (storedMax < 0) defaultMaxWidthDp else storedMax.coerceIn(0, maxWidthDp)
}

@Composable
private fun StatusBarLyricPaddingDialog(
    show: Boolean,
    leftDp: Float,
    rightDp: Float,
    onDismiss: () -> Unit,
    onConfirm: (leftDp: Float, rightDp: Float) -> Unit,
) {
    if (!show) return

    val initialValues = listOf(leftDp, rightDp)
    var inputValues by remember(initialValues) {
        mutableStateOf(initialValues.map(::formatStatusBarLyricPaddingDp))
    }
    val parsedValues = inputValues.map { it.toFloatOrNull() }
    val canConfirm = parsedValues.all { value ->
        value != null && value.isFinite() &&
                value in RootConstants.MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP..
                RootConstants.MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP
    }
    val labels = listOf(
        stringResource(R.string.label_status_bar_lyric_padding_left),
        stringResource(R.string.label_status_bar_lyric_padding_right),
    )

    WindowDialog(
        title = stringResource(R.string.title_status_bar_lyric_padding),
        summary = stringResource(
            R.string.summary_status_bar_lyric_padding_dialog,
            formatStatusBarLyricPaddingDp(RootConstants.MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP),
            formatStatusBarLyricPaddingDp(RootConstants.MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP),
        ),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        fun updateValue(index: Int, value: String) {
            if (!STATUS_BAR_LYRIC_PADDING_INPUT_PATTERN.matches(value)) return
            inputValues = inputValues.toMutableList().also { it[index] = value }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatusBarLyricPaddingField(
                    value = inputValues[0],
                    label = labels[0],
                    onValueChange = { updateValue(0, it) },
                    modifier = Modifier.weight(1f),
                )
                StatusBarLyricPaddingField(
                    value = inputValues[1],
                    label = labels[1],
                    onValueChange = { updateValue(1, it) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        val values = inputValues.mapNotNull { it.toFloatOrNull() }
                        if (values.size == 2 && canConfirm) {
                            onConfirm(values[0], values[1])
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = canConfirm,
                )
            }
        }
    }
}

@Composable
private fun StatusBarLyricPaddingField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier,
        maxLines = 1,
    )
}

private fun readStatusBarLyricPaddingDp(
    prefs: SharedPreferences,
    key: String,
): Float = prefs.getFloat(
    key,
    RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
).takeIf { it.isFinite() }?.coerceIn(
    RootConstants.MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
    RootConstants.MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
) ?: RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP

private fun readStatusBarLyricGestureValue(
    prefs: SharedPreferences,
    key: String,
    allowedValues: List<Int>,
    defaultValue: Int,
): Int {
    val fallback = defaultValue.takeIf(allowedValues::contains)
        ?: allowedValues.firstOrNull()
        ?: RootConstants.STATUS_BAR_LYRIC_GESTURE_ACTION_NONE
    return prefs.getInt(key, fallback).takeIf(allowedValues::contains) ?: fallback
}

private fun formatStatusBarLyricPaddingDp(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString() else value.toString()
