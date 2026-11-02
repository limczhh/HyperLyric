package com.lidesheng.hyperlyric.ui.page.hooksettings.media

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaCardSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember {
        context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
    }
    var mediaCardSwitcherEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED
            )
        )
    }
    var mediaCardSwitcherMode by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE
            ).coerceIn(
                RootConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_SINGLE,
                RootConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI
            )
        )
    }
    var mediaCardSwitcherMaxCount by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT
            ).coerceIn(
                RootConstants.MIN_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                RootConstants.MAX_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT
            )
        )
    }
    var islandMediaCardSwitcherEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_MEDIA_CARD_SWITCHER_ENABLED,
                RootConstants.DEFAULT_HOOK_ISLAND_MEDIA_CARD_SWITCHER_ENABLED
            )
        )
    }
    var islandMediaCardSwitcherMode by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MODE,
                RootConstants.DEFAULT_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MODE
            ).coerceIn(
                RootConstants.ISLAND_MEDIA_CARD_SWITCHER_MODE_SINGLE,
                RootConstants.ISLAND_MEDIA_CARD_SWITCHER_MODE_MULTI
            )
        )
    }
    var islandMediaCardSwitcherMaxCount by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT,
                RootConstants.DEFAULT_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT
            ).coerceIn(
                RootConstants.MIN_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT,
                RootConstants.MAX_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT
            )
        )
    }
    var removeIslandWhitelist by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST,
                RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST
            )
        )
    }
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showMaxCountDialog by remember { mutableStateOf(false) }
    var showIslandMediaCardSwitcherMaxCountDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_media_cards),
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
                    }
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = innerPadding
            ) {
                item(key = "media_card_sections") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            ArrowPreference(
                                title = stringResource(id = R.string.title_notification_center),
                                onClick = { navigator.navigate(Route.NotificationMediaCardSettings) }
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_super_island),
                                onClick = { navigator.navigate(Route.SuperIslandMediaCardSettings) }
                            )
                            ArrowPreference(
                                title = stringResource(id = R.string.title_always_on_display),
                                onClick = { navigator.navigate(Route.AlwaysOnDisplaySettings) }
                            )
                        }
                    }
                }
                item(key = "media_card_remove_island_whitelist") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.title_remove_island_whitelist),
                            summary = stringResource(R.string.summary_remove_island_whitelist),
                            checked = removeIslandWhitelist,
                            onCheckedChange = { enabled ->
                                removeIslandWhitelist = enabled
                                PrefsBridge.putBoolean(
                                    RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST,
                                    enabled
                                )
                            }
                        )
                    }
                }
                item(key = "media_card_switcher") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.title_notification_media_card_switcher),
                                summary = stringResource(R.string.summary_notification_media_card_switcher),
                                checked = mediaCardSwitcherEnabled,
                                onCheckedChange = { enabled ->
                                    mediaCardSwitcherEnabled = enabled
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED,
                                        enabled
                                    )
                                }
                            )
                            AnimatedVisibility(visible = mediaCardSwitcherEnabled) {
                                Column {
                                    val modeValues = listOf(
                                        RootConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_SINGLE,
                                        RootConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI
                                    )
                                    OverlayDropdownPreference(
                                        title = stringResource(
                                            R.string.title_notification_media_card_switcher_mode
                                        ),
                                        items = listOf(
                                            stringResource(
                                                R.string.option_notification_media_card_switcher_single
                                            ),
                                            stringResource(
                                                R.string.option_notification_media_card_switcher_multi
                                            )
                                        ),
                                        selectedIndex = modeValues.indexOf(mediaCardSwitcherMode)
                                            .coerceAtLeast(0),
                                        onSelectedIndexChange = { index ->
                                            val mode = modeValues[index]
                                            mediaCardSwitcherMode = mode
                                            PrefsBridge.putInt(
                                                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE,
                                                mode
                                            )
                                        }
                                    )
                                    ArrowPreference(
                                        title = stringResource(
                                            R.string.title_notification_media_card_switcher_max_count
                                        ),
                                        endActions = {
                                            Text(
                                                text = "$mediaCardSwitcherMaxCount",
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        },
                                        onClick = { showMaxCountDialog = true }
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "island_media_card_switcher") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.title_notification_media_card_switcher),
                                summary = stringResource(R.string.summary_remove_island_whitelist),
                                checked = islandMediaCardSwitcherEnabled,
                                onCheckedChange = { enabled ->
                                    islandMediaCardSwitcherEnabled = enabled
                                    PrefsBridge.putBoolean(
                                        RootConstants.KEY_HOOK_ISLAND_MEDIA_CARD_SWITCHER_ENABLED,
                                        enabled
                                    )
                                }
                            )
                            AnimatedVisibility(visible = islandMediaCardSwitcherEnabled) {
                                Column {
                                    val modeValues = listOf(
                                        RootConstants.ISLAND_MEDIA_CARD_SWITCHER_MODE_SINGLE,
                                        RootConstants.ISLAND_MEDIA_CARD_SWITCHER_MODE_MULTI
                                    )
                                    OverlayDropdownPreference(
                                        title = stringResource(
                                            R.string.title_notification_media_card_switcher_mode
                                        ),
                                        items = listOf(
                                            stringResource(
                                                R.string.option_island_media_card_switcher_single
                                            ),
                                            stringResource(
                                                R.string.option_island_media_card_switcher_multi
                                            )
                                        ),
                                        selectedIndex = modeValues.indexOf(
                                            islandMediaCardSwitcherMode
                                        ).coerceAtLeast(0),
                                        onSelectedIndexChange = { index ->
                                            val mode = modeValues[index]
                                            islandMediaCardSwitcherMode = mode
                                            PrefsBridge.putInt(
                                                RootConstants.KEY_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MODE,
                                                mode
                                            )
                                        }
                                    )
                                    ArrowPreference(
                                        title = stringResource(
                                            R.string.title_notification_media_card_switcher_max_count
                                        ),
                                        endActions = {
                                            Text(
                                                text = islandMediaCardSwitcherMaxCount.toString(),
                                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                            )
                                        },
                                        onClick = {
                                            showIslandMediaCardSwitcherMaxCountDialog = true
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

    NumberInputDialog(
        show = showMaxCountDialog,
        title = stringResource(R.string.title_notification_media_card_switcher_max_count),
        label = stringResource(R.string.label_notification_media_card_switcher_max_count),
        initialValue = mediaCardSwitcherMaxCount,
        min = RootConstants.MIN_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
        max = RootConstants.MAX_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
        onDismiss = { showMaxCountDialog = false },
        onConfirm = { maxCount ->
            mediaCardSwitcherMaxCount = maxCount
            PrefsBridge.putInt(
                RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                maxCount
            )
        }
    )

    NumberInputDialog(
        show = showIslandMediaCardSwitcherMaxCountDialog,
        title = stringResource(R.string.title_notification_media_card_switcher_max_count),
        label = stringResource(R.string.label_notification_media_card_switcher_max_count),
        initialValue = islandMediaCardSwitcherMaxCount,
        min = RootConstants.MIN_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT,
        max = RootConstants.MAX_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT,
        onDismiss = { showIslandMediaCardSwitcherMaxCountDialog = false },
        onConfirm = { maxCount ->
            islandMediaCardSwitcherMaxCount = maxCount
            PrefsBridge.putInt(
                RootConstants.KEY_HOOK_ISLAND_MEDIA_CARD_SWITCHER_MAX_COUNT,
                maxCount
            )
        }
    )

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

