package com.lidesheng.hyperlyric.ui.page.hooksettings.media.island

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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun IslandExpandedMediaCardPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember {
        context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
    }

    var islandExpandedLayoutStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE
            ).takeIf { it in islandExpandedMediaLayoutStyles }
                ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE
        )
    }
    var islandExpandedLayoutPromptDismissed by remember {
        mutableStateOf(
            prefs.getBoolean("hide_island_expanded_media_layout_prompt", false)
        )
    }

    var islandExpandedAmbientFlowMode by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE
            ).coerceIn(
                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL
            )
        )
    }

    var islandExpandedBackgroundStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE
            ).coerceIn(
                RootConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_SOFT_COVER
            )
        )
    }
    var islandExpandedBackgroundColorAnimation by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION
            )
        )
    }
    var islandExpandedBackgroundBlur by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR
            ).coerceIn(1, 20)
        )
    }
    var islandExpandedBackgroundAutoInvert by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT
            )
        )
    }
    var islandExpandedSoftCoverTone by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE
            ).coerceIn(
                RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
                RootConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM
            )
        )
    }
    var islandExpandedCardTheme by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME
            ).coerceIn(
                RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
                RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK
            )
        )
    }
    var islandExpandedCoverStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE
            ).coerceIn(
                RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN
            )
        )
    }
    var hideIslandExpandedCoverSource by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE
            )
        )
    }
    var hideIslandExpandedDeviceSwitch by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH
            )
        )
    }
    var disableIslandExpandedCoverFlip by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP
            )
        )
    }
    var hideIslandExpandedCustomActions by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS
            )
        )
    }
    var hideIslandExpandedTime by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME
            )
        )
    }
    var islandExpandedProgressStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE
            ).coerceIn(
                RootConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE
            )
        )
    }
    var islandExpandedProgressHeadGlow by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW
            )
        )
    }
    var islandExpandedThumbStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE
            ).coerceIn(
                RootConstants.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_HIDDEN
            )
        )
    }

    var islandExpandedActionOrder by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER
            ).coerceIn(
                RootConstants.ISLAND_EXPANDED_MEDIA_ACTION_ORDER_DEFAULT,
                RootConstants.ISLAND_EXPANDED_MEDIA_ACTION_ORDER_PLAY_LEFT
            )
        )
    }
    var islandExpandedActionAlignLeft by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT,
                RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT
            )
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
                    title = stringResource(id = R.string.title_super_island),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showRestartDialog = true }) {
                            Icon(
                                imageVector = MiuixIcons.Refresh,
                                contentDescription = stringResource(id = R.string.dialog_restart_title)
                            )
                        }
                    },
                    bottomContent = {
                        Column {
                            val mappedAmbientFlowMode = when (islandExpandedAmbientFlowMode) {
                                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT -> RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC
                                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED -> RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
                                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR -> RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
                                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL -> RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL
                                else -> RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
                            }

                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Ltr
                            ) {
                                MediaPreviewCard(
                                    showShadow = false,
                                    coverStyle = islandExpandedCoverStyle,
                                    hideCoverSource = hideIslandExpandedCoverSource,
                                    disableCoverFlip = disableIslandExpandedCoverFlip,
                                    hideDeviceSwitch = hideIslandExpandedDeviceSwitch,
                                    hideCustomActions = hideIslandExpandedCustomActions,
                                    hideTime = hideIslandExpandedTime,
                                    actionOrder = islandExpandedActionOrder,
                                    actionAlignLeft = islandExpandedActionAlignLeft,
                                    cardTheme = islandExpandedCardTheme,
                                    backgroundStyle = islandExpandedBackgroundStyle,
                                    backgroundBlur = islandExpandedBackgroundBlur,
                                    softCoverTone = islandExpandedSoftCoverTone,
                                    ambientFlowMode = mappedAmbientFlowMode,
                                    waveProgress =
                                        islandExpandedProgressStyle ==
                                            RootConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE,
                                    verticalProgressThumb =
                                        islandExpandedThumbStyle ==
                                            RootConstants.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_VERTICAL,
                                    hideProgressThumb =
                                        islandExpandedThumbStyle ==
                                            RootConstants.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_HIDDEN
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
                            islandExpandedMediaBackgroundSection(
                                cardTheme = islandExpandedCardTheme,
                                onCardThemeChange = { theme ->
                                    islandExpandedCardTheme = theme
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
                                            theme
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
                                        theme
                                    )
                                },
                                backgroundStyle = islandExpandedBackgroundStyle,
                                onBackgroundStyleChange = { style ->
                                    islandExpandedBackgroundStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE,
                                        style
                                    )
                                },
                                backgroundColorAnimation = islandExpandedBackgroundColorAnimation,
                                onBackgroundColorAnimationChange = { anim ->
                                    islandExpandedBackgroundColorAnimation = anim
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                                            anim
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                                        anim
                                    )
                                },
                                backgroundBlur = islandExpandedBackgroundBlur,
                                onBackgroundBlurChange = { blur ->
                                    islandExpandedBackgroundBlur = blur
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR,
                                            blur
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR,
                                        blur
                                    )
                                },
                                backgroundAutoInvert = islandExpandedBackgroundAutoInvert,
                                onBackgroundAutoInvertChange = { invert ->
                                    islandExpandedBackgroundAutoInvert = invert
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                                            invert
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                                        invert
                                    )
                                },
                                softCoverTone = islandExpandedSoftCoverTone,
                                onSoftCoverToneChange = { tone ->
                                    islandExpandedSoftCoverTone = tone
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE,
                                            tone
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE,
                                        tone
                                    )
                                },
                                ambientFlowMode = islandExpandedAmbientFlowMode,
                                onAmbientFlowModeChange = { mode ->
                                    islandExpandedAmbientFlowMode = mode
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE,
                                            mode
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE,
                                        mode
                                    )
                                }
                            )
                        }

                        1 -> {
                            islandExpandedMediaElementSection(
                                coverStyle = islandExpandedCoverStyle,
                                onCoverStyleChange = { style ->
                                    islandExpandedCoverStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
                                        style
                                    )
                                },
                                hideCoverSource = hideIslandExpandedCoverSource,
                                onHideCoverSourceChange = { hide ->
                                    hideIslandExpandedCoverSource = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                                        hide
                                    )
                                },
                                disableCoverFlip = disableIslandExpandedCoverFlip,
                                onDisableCoverFlipChange = { disable ->
                                    disableIslandExpandedCoverFlip = disable
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP,
                                            disable
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP,
                                        disable
                                    )
                                },
                                hideDeviceSwitch = hideIslandExpandedDeviceSwitch,
                                onHideDeviceSwitchChange = { hide ->
                                    hideIslandExpandedDeviceSwitch = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                                        hide
                                    )
                                },
                                hideCustomActions = hideIslandExpandedCustomActions,
                                onHideCustomActionsChange = { hide ->
                                    hideIslandExpandedCustomActions = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS,
                                        hide
                                    )
                                },
                                hideTime = hideIslandExpandedTime,
                                onHideTimeChange = { hide ->
                                    hideIslandExpandedTime = hide
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME,
                                            hide
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME,
                                        hide
                                    )
                                },
                                progressStyle = islandExpandedProgressStyle,
                                onProgressStyleChange = { style ->
                                    islandExpandedProgressStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE,
                                        style
                                    )
                                },
                                progressHeadGlow = islandExpandedProgressHeadGlow,
                                onProgressHeadGlowChange = { enabled ->
                                    islandExpandedProgressHeadGlow = enabled
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW,
                                            enabled
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW,
                                        enabled
                                    )
                                },
                                thumbStyle = islandExpandedThumbStyle,
                                onThumbStyleChange = { style ->
                                    islandExpandedThumbStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE,
                                        style
                                    )
                                }
                            )
                        }

                        2 -> {
                            islandExpandedMediaLayoutSection(
                                layoutStyle = islandExpandedLayoutStyle,
                                onLayoutStyleChange = { style ->
                                    islandExpandedLayoutStyle = style
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE,
                                            style
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE,
                                        style
                                    )
                                },
                                layoutPromptDismissed = islandExpandedLayoutPromptDismissed,
                                onLayoutPromptDismissed = {
                                    islandExpandedLayoutPromptDismissed = true
                                    prefs.edit {
                                        putBoolean(
                                            "hide_island_expanded_media_layout_prompt",
                                            true
                                        )
                                    }
                                },
                                actionAlignLeft = islandExpandedActionAlignLeft,
                                onActionAlignLeftChange = { alignLeft ->
                                    islandExpandedActionAlignLeft = alignLeft
                                    prefs.edit {
                                        putBoolean(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT,
                                            alignLeft
                                        )
                                    }
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT,
                                        alignLeft
                                    )
                                },
                                actionOrder = islandExpandedActionOrder,
                                onActionOrderChange = { order ->
                                    islandExpandedActionOrder = order
                                    prefs.edit {
                                        putInt(
                                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER,
                                            order
                                        )
                                    }
                                    PrefsBridge.putInt(
                                        RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER,
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
