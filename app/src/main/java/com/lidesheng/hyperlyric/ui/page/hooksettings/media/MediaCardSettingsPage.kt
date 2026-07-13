package com.lidesheng.hyperlyric.ui.page.hooksettings.media

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
                RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
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
                RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
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
