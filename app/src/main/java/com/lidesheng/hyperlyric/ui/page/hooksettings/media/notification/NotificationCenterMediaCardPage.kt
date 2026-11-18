package com.lidesheng.hyperlyric.ui.page.hooksettings.media.notification

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.preview.MediaPreviewCard
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import com.lidesheng.hyperlyric.ui.utils.AdaptiveIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val LEGACY_NOTIFICATION_MEDIA_PROGRESS_STYLE_GLOW = 2

@Composable
fun NotificationCenterMediaCardPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember {
        context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
    }

    var notificationAmbientFlowMode by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE
            ).coerceIn(
                RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED,
                RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL
            )
        )
    }
    var notificationBackgroundStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE
            ).coerceIn(
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT,
                RootConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER
            )
        )
    }
    var notificationBackgroundColorAnimation by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION
            )
        )
    }
    var notificationBackgroundBlur by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR
            ).coerceIn(1, 20)
        )
    }
    var notificationBackgroundAutoInvert by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT
            )
        )
    }
    var notificationSoftCoverTone by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE
            ).coerceIn(
                RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
                RootConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM
            )
        )
    }
    var notificationCardTheme by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME
            ).coerceIn(
                RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
                RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK
            )
        )
    }
    var notificationCoverStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE
            ).coerceIn(
                RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT,
                RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN
            )
        )
    }
    var hideNotificationCoverSource by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE
            )
        )
    }
    var hideNotificationCoverShadow by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW
            )
        )
    }
    var hideNotificationDeviceSwitch by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH
            )
        )
    }
    var disableNotificationCoverFlip by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP
            )
        )
    }
    var hideNotificationCustomActions by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS
            )
        )
    }
    var hideNotificationTime by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_TIME
            )
        )
    }
    val storedNotificationProgressStyle = prefs.getInt(
        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE,
        RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE
    )
    var notificationProgressStyle by remember {
        mutableIntStateOf(
            if (
                storedNotificationProgressStyle ==
                RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE
            ) {
                RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE
            } else {
                RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT
            }
        )
    }
    var notificationProgressHeadGlow by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW
            ) ||
                storedNotificationProgressStyle ==
                LEGACY_NOTIFICATION_MEDIA_PROGRESS_STYLE_GLOW
        )
    }
    var notificationThumbStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE
            ).coerceIn(
                RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT,
                RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN
            )
        )
    }

    var notificationActionOrder by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER
            ).coerceIn(
                RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT,
                RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT
            )
        )
    }
    var notificationActionAlignLeft by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT
            )
        )
    }
    var notificationLayoutStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE
            ).takeIf { it in notificationMediaLayoutStyles }
                ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE
        )
    }
    var notificationLayoutPromptDismissed by remember {
        mutableStateOf(
            prefs.getBoolean("hide_notification_media_layout_prompt", false)
        )
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val tabs = listOf(
        stringResource(R.string.tab_background),
        stringResource(R.string.tab_elements),
        stringResource(R.string.tab_layout)
    )
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRestartDialog by remember { mutableStateOf(false) }

    val backgroundListState = rememberLazyListState()
    val elementListState = rememberLazyListState()
    val layoutListState = rememberLazyListState()

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_notification_center),
                    scrollBehavior = topAppBarScrollBehavior,
                    isContentScrolled = (when (pagerState.currentPage) { 0 -> backgroundListState; 1 -> elementListState; else -> layoutListState }).canScrollBackward,
                    navigationIcon = {
                        AdaptiveIconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = {
                        AdaptiveIconButton(onClick = { showRestartDialog = true }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(id = R.string.dialog_restart_title)
                            )
                        }
                    },
                    bottomContent = {
                        Column {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr
                            ) {
                                MediaPreviewCard(
                                    showShadow = !hideNotificationCoverShadow,
                                    coverStyle = notificationCoverStyle,
                                    hideCoverSource = hideNotificationCoverSource,
                                    disableCoverFlip = disableNotificationCoverFlip,
                                    hideDeviceSwitch = hideNotificationDeviceSwitch,
                                    hideCustomActions = hideNotificationCustomActions,
                                    hideTime = hideNotificationTime,
                                    actionOrder = notificationActionOrder,
                                    actionAlignLeft = notificationActionAlignLeft,
                                    cardTheme = notificationCardTheme,
                                    backgroundStyle = notificationBackgroundStyle,
                                    backgroundBlur = notificationBackgroundBlur,
                                    softCoverTone = notificationSoftCoverTone,
                                    ambientFlowMode = notificationAmbientFlowMode,
                                    waveProgress =
                                        notificationProgressStyle ==
                                            RootConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE,
                                    verticalProgressThumb =
                                        notificationThumbStyle ==
                                            RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_VERTICAL,
                                    hideProgressThumb =
                                        notificationThumbStyle ==
                                            RootConstants.NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN
                                )
                            }
                            AdaptiveTabRow(
                                tabs = tabs,
                                selectedTabIndex = pagerState.targetPage,
                                onTabSelected = { index ->
                                    coroutineScope.launch { pagerState.scrollToPage(index) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                                colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 2
            ) { page ->
                val listState = when (page) {
                    0 -> backgroundListState
                    1 -> elementListState
                    else -> layoutListState
                }
                val topPadding = innerPadding.calculateTopPadding()
                val bottomPadding = innerPadding.calculateBottomPadding()
                val contentPadding = pageContentPadding(innerPadding, extraBottom = 16.dp)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                    contentPadding = contentPadding
                ) {
                    when (page) {
                        0 -> {
                            notificationCenterMediaBackgroundSection(
                                cardTheme = notificationCardTheme,
                                onCardThemeChange = { theme ->
                                    notificationCardTheme = theme
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
                                            theme
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
                                        theme
                                    )
                                },
                                backgroundStyle = notificationBackgroundStyle,
                                onBackgroundStyleChange = { style ->
                                    notificationBackgroundStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE,
                                        style
                                    )
                                },
                                backgroundColorAnimation = notificationBackgroundColorAnimation,
                                onBackgroundColorAnimationChange = { anim ->
                                    notificationBackgroundColorAnimation = anim
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                                            anim
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                                        anim
                                    )
                                },
                                backgroundBlur = notificationBackgroundBlur,
                                onBackgroundBlurChange = { blur ->
                                    notificationBackgroundBlur = blur
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR,
                                            blur
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR,
                                        blur
                                    )
                                },
                                backgroundAutoInvert = notificationBackgroundAutoInvert,
                                onBackgroundAutoInvertChange = { invert ->
                                    notificationBackgroundAutoInvert = invert
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                                            invert
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                                        invert
                                    )
                                },
                                softCoverTone = notificationSoftCoverTone,
                                onSoftCoverToneChange = { tone ->
                                    notificationSoftCoverTone = tone
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE,
                                            tone
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE,
                                        tone
                                    )
                                },
                                ambientFlowMode = notificationAmbientFlowMode,
                                onAmbientFlowModeChange = { mode ->
                                    notificationAmbientFlowMode = mode
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE,
                                            mode
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE,
                                        mode
                                    )
                                }
                            )
                        }

                        1 -> {
                            notificationCenterMediaElementSection(
                                coverStyle = notificationCoverStyle,
                                onCoverStyleChange = { style ->
                                    notificationCoverStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                                        style
                                    )
                                },
                                hideCoverSource = hideNotificationCoverSource,
                                onHideCoverSourceChange = { hide ->
                                    hideNotificationCoverSource = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                                        hide
                                    )
                                },
                                hideCoverShadow = hideNotificationCoverShadow,
                                onHideCoverShadowChange = { hide ->
                                    hideNotificationCoverShadow = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW,
                                        hide
                                    )
                                },
                                disableCoverFlip = disableNotificationCoverFlip,
                                onDisableCoverFlipChange = { disable ->
                                    disableNotificationCoverFlip = disable
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP,
                                            disable
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP,
                                        disable
                                    )
                                },
                                hideDeviceSwitch = hideNotificationDeviceSwitch,
                                onHideDeviceSwitchChange = { hide ->
                                    hideNotificationDeviceSwitch = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                                        hide
                                    )
                                },
                                hideCustomActions = hideNotificationCustomActions,
                                onHideCustomActionsChange = { hide ->
                                    hideNotificationCustomActions = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS,
                                        hide
                                    )
                                },
                                hideTime = hideNotificationTime,
                                onHideTimeChange = { hide ->
                                    hideNotificationTime = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME,
                                        hide
                                    )
                                },
                                progressStyle = notificationProgressStyle,
                                onProgressStyleChange = { style ->
                                    notificationProgressStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE,
                                        style
                                    )
                                },
                                progressHeadGlow = notificationProgressHeadGlow,
                                onProgressHeadGlowChange = { enabled ->
                                    notificationProgressHeadGlow = enabled
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW,
                                            enabled
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW,
                                        enabled
                                    )
                                },
                                thumbStyle = notificationThumbStyle,
                                onThumbStyleChange = { style ->
                                    notificationThumbStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE,
                                        style
                                    )
                                }
                            )
                        }

                        2 -> {
                            notificationCenterMediaLayoutSection(
                                layoutStyle = notificationLayoutStyle,
                                onLayoutStyleChange = { style ->
                                    notificationLayoutStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE,
                                        style
                                    )
                                },
                                layoutPromptDismissed = notificationLayoutPromptDismissed,
                                onLayoutPromptDismissed = {
                                    notificationLayoutPromptDismissed = true
                                    prefs.edit {
                                        putBoolean(
                                            "hide_notification_media_layout_prompt",
                                            true
                                        )
                                    }
                                },
                                actionAlignLeft = notificationActionAlignLeft,
                                onActionAlignLeftChange = { alignLeft ->
                                    notificationActionAlignLeft = alignLeft
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT,
                                            alignLeft
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT,
                                        alignLeft
                                    )
                                },
                                actionOrder = notificationActionOrder,
                                onActionOrderChange = { order ->
                                    notificationActionOrder = order
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER,
                                            order
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER,
                                        order
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    SimpleDialog(
        show = showRestartDialog,
        title = stringResource(R.string.dialog_restart_title),
        summary = stringResource(R.string.dialog_restart_summary),
        onDismiss = { showRestartDialog = false },
        onConfirm = {
            showRestartDialog = false
            coroutineScope.launch {
                val success = ShellUtils.restartSystemUI()
                if (!success) {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.toast_no_root),
                        duration = SnackbarDuration.Custom(2000L)
                    )
                }
            }
        }
    )
}

