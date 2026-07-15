package com.lidesheng.hyperlyric.ui.page.hooksettings.media

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.island.islandExpandedMediaCardSection
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.notification.notificationCenterMediaCardSection
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaCardSettingsPage() {
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
                RootConstants.MEDIA_SOFT_COVER_TONE_DARK
            )
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
                RootConstants.MEDIA_SOFT_COVER_TONE_DARK
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
    var hideNotificationDeviceSwitch by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH
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

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_media_cards),
                    subtitle = stringResource(R.string.summary_media_cards),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(
                top = top,
                bottom = bottom + 16.dp
            )
        }

        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding
            ) {
                notificationCenterMediaCardSection(
                    cardTheme = notificationCardTheme,
                    onCardThemeChange = { theme ->
                        notificationCardTheme = theme
                        prefs.edit {
                            putInt(RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME, theme)
                        }
                        PrefsBridge.putInt(
                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
                            theme
                        )
                    },
                    coverStyle = notificationCoverStyle,
                    onCoverStyleChange = { style ->
                        notificationCoverStyle = style
                        prefs.edit {
                            putInt(RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE, style)
                        }
                        PrefsBridge.putInt(
                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                            style
                        )
                    },
                    hideCoverSource = hideNotificationCoverSource,
                    onHideCoverSourceChange = { hidden ->
                        hideNotificationCoverSource = hidden
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                                hidden
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                            hidden
                        )
                    },
                    hideDeviceSwitch = hideNotificationDeviceSwitch,
                    onHideDeviceSwitchChange = { hidden ->
                        hideNotificationDeviceSwitch = hidden
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                                hidden
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                            hidden
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
                    onBackgroundColorAnimationChange = { enabled ->
                        notificationBackgroundColorAnimation = enabled
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                                enabled
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                            enabled
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
                    onBackgroundAutoInvertChange = { enabled ->
                        notificationBackgroundAutoInvert = enabled
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                                enabled
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                            enabled
                        )
                    },
                    softCoverTone = notificationSoftCoverTone,
                    onSoftCoverToneChange = { tone ->
                        notificationSoftCoverTone = tone
                        prefs.edit {
                            putInt(RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE, tone)
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
                islandExpandedMediaCardSection(
                    cardTheme = islandExpandedCardTheme,
                    onCardThemeChange = { theme ->
                        islandExpandedCardTheme = theme
                        prefs.edit {
                            putInt(RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME, theme)
                        }
                        PrefsBridge.putInt(
                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
                            theme
                        )
                    },
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
                    onHideCoverSourceChange = { hidden ->
                        hideIslandExpandedCoverSource = hidden
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                                hidden
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                            hidden
                        )
                    },
                    hideDeviceSwitch = hideIslandExpandedDeviceSwitch,
                    onHideDeviceSwitchChange = { hidden ->
                        hideIslandExpandedDeviceSwitch = hidden
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                                hidden
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                            hidden
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
                    onBackgroundColorAnimationChange = { enabled ->
                        islandExpandedBackgroundColorAnimation = enabled
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                                enabled
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                            enabled
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
                    onBackgroundAutoInvertChange = { enabled ->
                        islandExpandedBackgroundAutoInvert = enabled
                        prefs.edit {
                            putBoolean(
                                RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                                enabled
                            )
                        }
                        PrefsBridge.putBoolean(
                            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                            enabled
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
        }
    }
}
