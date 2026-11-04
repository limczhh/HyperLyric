package com.lidesheng.hyperlyric.ui.page.hooksettings.media.controlcenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ControlCenterMediaCardPage() {
    val navigator = LocalNavigator.current
    var immersiveCoverLayout by remember {
        mutableStateOf(
            PrefsBridge.getBoolean(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_SQUARE_COVER,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_SQUARE_COVER
            )
        )
    }
    var progressHeadGlow by remember {
        mutableStateOf(
            PrefsBridge.getBoolean(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_PROGRESS_HEAD_GLOW,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_PROGRESS_HEAD_GLOW
            )
        )
    }
    var customActions by remember {
        mutableStateOf(
            PrefsBridge.getBoolean(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_CUSTOM_ACTIONS,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_CUSTOM_ACTIONS
            )
        )
    }
    var coverFlip by remember {
        mutableStateOf(PrefsBridge.getInt(
            RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_COVER_FLIP,
            RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_COVER_FLIP
        ).coerceIn(0, 2))
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
                    title = stringResource(id = R.string.title_control_center),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()
        val contentPadding = remember(topPadding, bottomPadding) {
            PaddingValues(
                top = topPadding,
                bottom = bottomPadding + 16.dp
            )
        }
        val lazyListState = rememberLazyListState()

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
                item(key = "control_center_media_effects") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.title_media_cover_flip_animation),
                            items = listOf(
                                stringResource(R.string.monet_default),
                                stringResource(R.string.option_island_media_ambient_flow_disabled),
                                stringResource(R.string.title_enable)
                            ),
                            selectedIndex = coverFlip,
                            onSelectedIndexChange = { mode ->
                                coverFlip = mode
                                PrefsBridge.putInt(RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_COVER_FLIP, mode)
                            }
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.title_media_progress_head_glow),
                            summary = stringResource(id = R.string.summary_media_progress_head_glow),
                            checked = progressHeadGlow,
                            onCheckedChange = { enabled ->
                                progressHeadGlow = enabled
                                PrefsBridge.putBoolean(
                                    RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_PROGRESS_HEAD_GLOW,
                                    enabled
                                )
                            }
                        )
                    }
                }
                item(key = "control_center_immersive_cover_layout") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_immersive_cover_layout),
                            checked = immersiveCoverLayout,
                            onCheckedChange = { enabled ->
                                immersiveCoverLayout = enabled
                                PrefsBridge.putBoolean(
                                    RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_SQUARE_COVER,
                                    enabled
                                )
                            }
                        )
                        if (immersiveCoverLayout) {
                            SwitchPreference(
                                title = stringResource(id = R.string.title_show_media_custom_actions),
                                checked = customActions,
                                onCheckedChange = { enabled ->
                                    customActions = enabled
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_CUSTOM_ACTIONS,
                                        enabled
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
