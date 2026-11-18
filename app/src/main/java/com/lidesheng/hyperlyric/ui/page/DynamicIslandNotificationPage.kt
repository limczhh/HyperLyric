package com.lidesheng.hyperlyric.ui.page

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.ServiceConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.lyric.ConfigRepository
import com.lidesheng.hyperlyric.service.LiveLyricService
import com.lidesheng.hyperlyric.service.utils.shizuku.ShizukuManager
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.page.lyricnotification.LyricNotificationConfigTab
import com.lidesheng.hyperlyric.ui.page.lyricnotification.LyricNotificationWhitelistTab
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
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
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import com.lidesheng.hyperlyric.ui.utils.AdaptiveBottomSheet

@SuppressLint("BatteryLife")
@Composable
fun DynamicIslandNotificationPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs =
        remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    val scrollBehavior = MiuixScrollBehavior()
    val configLazyListState = rememberLazyListState()
    val whitelistLazyListState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetSnackbarHostState = remember { SnackbarHostState() }

    var dynamicIslandEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND
            )
        )
    }
    var showPermissionSheet by remember { mutableStateOf(false) }

    var lyricSource by remember {
        mutableIntStateOf(
            prefs.getInt(
                ServiceConstants.KEY_SERVICE_LYRIC_SOURCE,
                ServiceConstants.DEFAULT_SERVICE_LYRIC_SOURCE
            )
        )
    }
    var limitWidthEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH,
                ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_LIMIT_WIDTH
            )
        )
    }
    var maxWidth by remember {
        mutableIntStateOf(
            prefs.getInt(
                ServiceConstants.KEY_NOTIFICATION_ISLAND_MAX_WIDTH,
                ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_MAX_WIDTH
            )
        )
    }


    var notificationType by remember {
        mutableIntStateOf(
            prefs.getInt(
                ServiceConstants.KEY_NOTIFICATION_TYPE,
                ServiceConstants.DEFAULT_NOTIFICATION_TYPE
            )
        )
    }
    val initialIconStyleKey =
        if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
    var islandLeftIconStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                initialIconStyleKey,
                ServiceConstants.DEFAULT_ISLAND_LEFT_ICON
            )
        )
    }

    val tabs = listOf(
        stringResource(R.string.title_custom_config),
        stringResource(R.string.title_lyric_whitelist)
    )
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val msgAppExists = stringResource(R.string.toast_app_exists)
    val msgPkgEmpty = stringResource(R.string.toast_pkg_empty)
    val msgAutostartFailed = stringResource(R.string.toast_autostart_failed)
    val msgBatteryIgnored = stringResource(R.string.toast_battery_ignored)
    val msgBatteryFailed = stringResource(R.string.toast_battery_failed)
    val msgShizukuNotRunning = stringResource(R.string.toast_shizuku_not_running)
    val msgShizukuPermissionRequired = stringResource(R.string.toast_shizuku_permission_required)
    val msgPermissionGranted = stringResource(R.string.toast_permission_granted)
    val msgPermissionDenied = stringResource(R.string.toast_permission_denied)
    val msgPermissionNotGranted = stringResource(R.string.toast_permission_not_granted)
    val msgOpenSettingsFailed = stringResource(R.string.toast_open_settings_failed)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            coroutineScope.launch {
                sheetSnackbarHostState.showSnackbar(
                    message = if (isGranted) msgPermissionGranted else msgPermissionDenied,
                    duration = SnackbarDuration.Custom(2000L)
                )
            }
        }
    )

    LaunchedEffect(Unit) { ConfigRepository.initWhitelist(context) }

    val whitelistSet by ConfigRepository.whitelistState.collectAsState()
    val whitelist = remember(whitelistSet) { whitelistSet.toList() }

    var showAddWhitelistDialog by remember { mutableStateOf(false) }
    var holdAddWhitelistDialog by remember { mutableStateOf(false) }
    var showDeleteWhitelistDialog by remember { mutableStateOf(false) }
    var holdDeleteWhitelistDialog by remember { mutableStateOf(false) }
    var tempWhitelistInput by remember { mutableStateOf("") }
    var packageToDelete by remember { mutableStateOf("") }

    var bypassFocusLimitEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                ServiceConstants.DEFAULT_BYPASS_FOCUS_NOTIFICATION_LIMIT
            )
        )
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND -> {
                    dynamicIslandEnabled = sharedPreferences.getBoolean(
                        RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND,
                        RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND
                    )
                }

                ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT -> {
                    bypassFocusLimitEnabled = sharedPreferences.getBoolean(
                        ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                        ServiceConstants.DEFAULT_BYPASS_FOCUS_NOTIFICATION_LIMIT
                    )
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val toggleDynamicIsland: (Boolean) -> Unit = remember {
        { isChecked ->
            if (isChecked) {
                val hasPostNotification = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                val hasListenerPermission =
                    NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
                if (hasPostNotification && hasListenerPermission) {
                    dynamicIslandEnabled = true
                    prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true) }
                    PrefsBridge.putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true)
                    LiveLyricService.ensureListenerBound(context)
                } else {
                    showPermissionSheet = true
                }
            } else {
                dynamicIslandEnabled = false
                prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, false) }
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, false)
            }
        }
    }

    val confirmPermissionSheet: () -> Unit = remember {
        {
            val hasPostNotification = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            val hasListenerPermission =
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            if (hasPostNotification && hasListenerPermission) {
                showPermissionSheet = false
                dynamicIslandEnabled = true
                prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true) }
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true)
                LiveLyricService.ensureListenerBound(context)
            } else {
                coroutineScope.launch {
                    sheetSnackbarHostState.showSnackbar(
                        message = msgPermissionNotGranted,
                        duration = SnackbarDuration.Custom(2000L)
                    )
                }
            }
        }
    }

    var disableLyricSplitEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT,
                ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT
            )
        )
    }

    var notificationClickAction by remember {
        mutableIntStateOf(
            prefs.getInt(
                ServiceConstants.KEY_NOTIFICATION_CLICK_ACTION,
                ServiceConstants.DEFAULT_NOTIFICATION_CLICK_ACTION
            )
        )
    }

    var showProgressEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS,
                ServiceConstants.DEFAULT_NOTIFICATION_SHOW_PROGRESS
            )
        )
    }

    var progressColorEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_PROGRESS_COLOR,
                ServiceConstants.DEFAULT_NOTIFICATION_PROGRESS_COLOR
            )
        )
    }

    var showAlbumArtEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_ALBUM,
                ServiceConstants.DEFAULT_NOTIFICATION_ALBUM
            )
        )
    }

    var focusNotificationType by remember {
        mutableIntStateOf(
            prefs.getInt(
                ServiceConstants.KEY_NOTIFICATION_FOCUS_STYLE,
                ServiceConstants.DEFAULT_NOTIFICATION_FOCUS_STYLE
            )
        )
    }

    var focusShowNotificationEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_FOCUS_SHOW,
                ServiceConstants.DEFAULT_NOTIFICATION_FOCUS_SHOW
            )
        )
    }

    var normalNotificationTitleStyle by remember {
        mutableIntStateOf(
            prefs.getInt(
                ServiceConstants.KEY_NOTIFICATION_TITLE_STYLE,
                ServiceConstants.DEFAULT_NOTIFICATION_TITLE_STYLE
            )
        )
    }

    var highlightColorEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_HIGHLIGHT_COLOR,
                ServiceConstants.DEFAULT_NOTIFICATION_HIGHLIGHT_COLOR
            )
        )
    }

    var songInfoHighlightColorEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR,
                ServiceConstants.DEFAULT_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR
            )
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_dynamic_island_lyrics),
                    scrollBehavior = scrollBehavior,
                    isContentScrolled = (if (pagerState.currentPage == 0) configLazyListState else whitelistLazyListState).canScrollBackward,
                    navigationIcon = {
                        AdaptiveIconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    bottomContent = {
                        AdaptiveTabRow(
                            tabs = tabs,
                            selectedTabIndex = pagerState.targetPage,
                            onTabSelected = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        it
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                        )
                    }
                )
            }
        }
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()
        val contentPadding = pageContentPadding(padding, extraBottom = 16.dp)



        TextInputDialog(
            show = showAddWhitelistDialog,
            title = stringResource(R.string.dialog_add_whitelist_title),
            initialValue = tempWhitelistInput,
            label = stringResource(R.string.dialog_add_whitelist_hint),
            confirmText = stringResource(R.string.save),
            onDismiss = { showAddWhitelistDialog = false },
            onDismissFinished = { if (!showAddWhitelistDialog) holdAddWhitelistDialog = false },
            onConfirm = { input ->
                if (input.isNotBlank()) {
                    val success = ConfigRepository.addPackageToWhitelist(context, input)
                    if (success) {
                        showAddWhitelistDialog = false
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = msgAppExists,
                                duration = SnackbarDuration.Custom(2000L)
                            )
                        }
                    }
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            message = msgPkgEmpty,
                            duration = SnackbarDuration.Custom(2000L)
                        )
                    }
                }
            }
        )

        SimpleDialog(
            show = showDeleteWhitelistDialog,
            title = stringResource(R.string.dialog_delete_whitelist_title),
            onDismiss = { showDeleteWhitelistDialog = false },
            onDismissFinished = { if (!showDeleteWhitelistDialog) holdDeleteWhitelistDialog = false },
            onConfirm = {
                ConfigRepository.removePackageFromWhitelist(context, packageToDelete)
                showDeleteWhitelistDialog = false
            }
        )

        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> {
                        LyricNotificationConfigTab(
                            lazyListState = configLazyListState,
                            scrollBehavior = scrollBehavior,
                            contentPadding = contentPadding,
                            dynamicIslandEnabled = dynamicIslandEnabled,
                            onDynamicIslandToggle = toggleDynamicIsland,
                            notificationType = notificationType,
                            onNotificationTypeChange = { index ->
                                val oldTypeKey =
                                    if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                prefs.edit { putInt(oldTypeKey, islandLeftIconStyle) }
                                notificationType = index
                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_TYPE, index) }
                                val newTypeKey =
                                    if (index == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                islandLeftIconStyle = prefs.getInt(
                                    newTypeKey,
                                    ServiceConstants.DEFAULT_ISLAND_LEFT_ICON
                                )
                                prefs.edit {
                                    putInt(
                                        ServiceConstants.KEY_ISLAND_LEFT_ICON,
                                        islandLeftIconStyle
                                    )
                                }
                            },
                            islandLeftIconStyle = islandLeftIconStyle,
                            onIslandLeftIconStyleChange = { index ->
                                islandLeftIconStyle = index
                                prefs.edit {
                                    putInt(
                                        if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL,
                                        index
                                    )
                                    putInt(ServiceConstants.KEY_ISLAND_LEFT_ICON, index)
                                }
                                if (index !in 0..2) {
                                    disableLyricSplitEnabled = false
                                    prefs.edit {
                                        putBoolean(
                                            ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT,
                                            false
                                        )
                                    }
                                }
                            },
                            disableLyricSplitEnabled = disableLyricSplitEnabled,
                            onDisableLyricSplitToggle = { checked ->
                                disableLyricSplitEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT,
                                        checked
                                    )
                                }
                            },
                            limitWidthEnabled = limitWidthEnabled,
                            onLimitWidthToggle = { checked ->
                                limitWidthEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH,
                                        checked
                                    )
                                }
                            },
                            maxWidth = maxWidth,
                            onMaxWidthChange = { value ->
                                maxWidth = value
                                prefs.edit {
                                    putInt(
                                        ServiceConstants.KEY_NOTIFICATION_ISLAND_MAX_WIDTH,
                                        value
                                    )
                                }
                            },
                            notificationClickAction = notificationClickAction,
                            onNotificationClickActionChange = {
                                notificationClickAction = it
                                prefs.edit {
                                    putInt(
                                        ServiceConstants.KEY_NOTIFICATION_CLICK_ACTION,
                                        it
                                    )
                                }
                            },
                            showProgressEnabled = showProgressEnabled,
                            onShowProgressToggle = { checked ->
                                showProgressEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS,
                                        checked
                                    )
                                }
                            },
                            progressColorEnabled = progressColorEnabled,
                            onProgressColorToggle = { checked ->
                                progressColorEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_PROGRESS_COLOR,
                                        checked
                                    )
                                }
                            },
                            showAlbumArtEnabled = showAlbumArtEnabled,
                            onShowAlbumArtToggle = { checked ->
                                showAlbumArtEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_ALBUM,
                                        checked
                                    )
                                }
                            },
                            focusNotificationType = focusNotificationType,
                            onFocusNotificationTypeChange = {
                                focusNotificationType = it
                                prefs.edit {
                                    putInt(
                                        ServiceConstants.KEY_NOTIFICATION_FOCUS_STYLE,
                                        it
                                    )
                                }
                            },
                            focusShowNotificationEnabled = focusShowNotificationEnabled,
                            onFocusShowNotificationToggle = { checked ->
                                focusShowNotificationEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_FOCUS_SHOW,
                                        checked
                                    )
                                }
                            },
                            normalNotificationTitleStyle = normalNotificationTitleStyle,
                            onNormalNotificationTitleStyleChange = {
                                normalNotificationTitleStyle = it
                                prefs.edit {
                                    putInt(
                                        ServiceConstants.KEY_NOTIFICATION_TITLE_STYLE,
                                        it
                                    )
                                }
                            },
                            onAutostartClick = {
                                try {
                                    val intent = Intent().apply {
                                        component = android.content.ComponentName(
                                            "com.miui.securitycenter",
                                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                        )
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent =
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = msgAutostartFailed,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                        }
                                    }
                                }
                            },
                            onBatteryOptimizationClick = {
                                try {
                                    val pm =
                                        context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                        val intent =
                                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                        context.startActivity(intent)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = msgBatteryIgnored,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                        }
                                    }
                                } catch (_: Exception) {
                                    try {
                                        val intent =
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = msgBatteryFailed,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                        }
                                    }
                                }
                            },
                            lyricSource = lyricSource,
                            onLyricSourceChange = { value ->
                                lyricSource = value
                                prefs.edit {
                                    putInt(
                                        ServiceConstants.KEY_SERVICE_LYRIC_SOURCE,
                                        value
                                    )
                                }
                            },
                            highlightColorEnabled = highlightColorEnabled,
                            onHighlightColorToggle = { checked ->
                                highlightColorEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_HIGHLIGHT_COLOR,
                                        checked
                                    )
                                }
                            },
                            songInfoHighlightColorEnabled = songInfoHighlightColorEnabled,
                            onSongInfoHighlightColorToggle = { checked ->
                                songInfoHighlightColorEnabled = checked
                                prefs.edit {
                                    putBoolean(
                                        ServiceConstants.KEY_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR,
                                        checked
                                    )
                                }
                            },
                            bypassFocusLimitEnabled = bypassFocusLimitEnabled,
                            onBypassFocusLimitToggle = { checked ->
                                if (checked) {
                                    coroutineScope.launch {
                                        if (!ShizukuManager.isShizukuServiceRunning()) {
                                            bypassFocusLimitEnabled = false
                                            prefs.edit {
                                                putBoolean(
                                                    ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                                                    false
                                                )
                                            }
                                            snackbarHostState.showSnackbar(
                                                message = msgShizukuNotRunning,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                            return@launch
                                        }

                                        val hasPermission = ShizukuManager.checkShizukuPermission()
                                        if (hasPermission) {
                                            bypassFocusLimitEnabled = true
                                            prefs.edit {
                                                putBoolean(
                                                    ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                                                    true
                                                )
                                            }
                                        } else {
                                            bypassFocusLimitEnabled = false
                                            prefs.edit {
                                                putBoolean(
                                                    ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                                                    false
                                                )
                                            }
                                            snackbarHostState.showSnackbar(
                                                message = msgShizukuPermissionRequired,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                        }
                                    }
                                } else {
                                    bypassFocusLimitEnabled = false
                                    prefs.edit {
                                        putBoolean(
                                            ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                                            false
                                        )
                                    }
                                }
                            }
                        )
                    }

                    1 -> {
                        LyricNotificationWhitelistTab(
                            lazyListState = whitelistLazyListState,
                            scrollBehavior = scrollBehavior,
                            contentPadding = contentPadding,
                            whitelist = whitelist,
                            onAddClick = {
                                tempWhitelistInput = ""
                                holdAddWhitelistDialog = true
                                showAddWhitelistDialog = true
                            },
                            showAddDialog = holdAddWhitelistDialog,
                            onDeleteClick = { pkg ->
                                packageToDelete = pkg
                                holdDeleteWhitelistDialog = true
                                showDeleteWhitelistDialog = true
                            },
                            showDeleteDialog = holdDeleteWhitelistDialog,
                            packageToDelete = packageToDelete
                        )
                    }
                }
            }
        }
    }

    AdaptiveBottomSheet(
        show = showPermissionSheet,
        title = stringResource(R.string.sheet_permission_title),
        backgroundColor = MiuixTheme.colorScheme.surface,
        startAction = {
            AdaptiveIconButton(onClick = { showPermissionSheet = false }) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            AdaptiveIconButton(onClick = confirmPermissionSheet) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.confirm),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = { showPermissionSheet = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val paddingPx = 24.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = constraints.maxWidth + paddingPx * 2)
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.place(-paddingPx, 0)
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.title_permission_post_notification),
                        onClick = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.title_permission_listener),
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                coroutineScope.launch {
                                    sheetSnackbarHostState.showSnackbar(
                                        message = msgOpenSettingsFailed,
                                        duration = SnackbarDuration.Custom(2000L)
                                    )
                                }
                            }
                        }
                    )
                }
            }
            SnackbarHost(
                state = sheetSnackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
