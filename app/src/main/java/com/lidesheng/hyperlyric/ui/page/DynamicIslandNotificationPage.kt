package com.lidesheng.hyperlyric.ui.page

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.edit
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.service.Constants as ServiceConstants
import com.lidesheng.hyperlyric.lyric.DynamicLyricData
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.lidesheng.hyperlyric.ui.page.lyricnotification.LyricNotificationConfigTab
import com.lidesheng.hyperlyric.ui.page.lyricnotification.LyricNotificationWhitelistTab
import com.lidesheng.hyperlyric.service.utils.shizuku.ShizukuManager

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

    val onlineLyricCacheLimit = prefs.getInt(
        ServiceConstants.KEY_ONLINE_LYRIC_CACHE_LIMIT,
        ServiceConstants.DEFAULT_ONLINE_LYRIC_CACHE_LIMIT
    )
    var onlineLyricEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                ServiceConstants.KEY_ONLINE_LYRIC_ENABLED,
                ServiceConstants.DEFAULT_ONLINE_LYRIC_ENABLED
            )
        )
    }
    var onlineLyricCacheLimitState by remember { mutableIntStateOf(onlineLyricCacheLimit) }
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
    var showCacheLimitDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) { DynamicLyricData.initWhitelist(context) }

    val whitelistSet by DynamicLyricData.whitelistState.collectAsState()
    val whitelist = remember(whitelistSet) { whitelistSet.toList() }

    var showAddWhitelistDialog by remember { mutableStateOf(false) }
    var showDeleteWhitelistDialog by remember { mutableStateOf(false) }
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

    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT) {
                bypassFocusLimitEnabled = sharedPreferences.getBoolean(
                    ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT,
                    ServiceConstants.DEFAULT_BYPASS_FOCUS_NOTIFICATION_LIMIT
                )
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
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
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_dynamic_island_lyrics),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    bottomContent = {
                        TabRow(
                            tabs = tabs,
                            selectedTabIndex = pagerState.currentPage,
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
                                .padding(bottom = 8.dp),
                            colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                        )
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
                start = 0.dp,
                end = 0.dp,
                bottom = bottomPadding + 16.dp
            )
        }

        NumberInputDialog(
            show = showCacheLimitDialog,
            title = stringResource(R.string.dialog_cache_limit_title),
            label = stringResource(R.string.label_cache_limit_range),
            initialValue = onlineLyricCacheLimitState,
            min = 0,
            max = 10000,
            onDismiss = { showCacheLimitDialog = false },
            onConfirm = {
                onlineLyricCacheLimitState = it
                prefs.edit { putInt(ServiceConstants.KEY_ONLINE_LYRIC_CACHE_LIMIT, it) }
                showCacheLimitDialog = false
            }
        )

        TextInputDialog(
            show = showAddWhitelistDialog,
            title = stringResource(R.string.dialog_add_whitelist_title),
            initialValue = tempWhitelistInput,
            label = stringResource(R.string.dialog_add_whitelist_hint),
            confirmText = stringResource(R.string.save),
            onDismiss = { showAddWhitelistDialog = false },
            onConfirm = { input ->
                if (input.isNotBlank()) {
                    val success = DynamicLyricData.addPackageToWhitelist(context, input)
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
            onConfirm = {
                DynamicLyricData.removePackageFromWhitelist(context, packageToDelete)
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
                            notificationType = notificationType,
                            onNotificationTypeChange = { index ->
                                val oldTypeKey = if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                prefs.edit { putInt(oldTypeKey, islandLeftIconStyle) }
                                notificationType = index
                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_TYPE, index) }
                                val newTypeKey = if (index == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                islandLeftIconStyle = prefs.getInt(newTypeKey, ServiceConstants.DEFAULT_ISLAND_LEFT_ICON)
                                prefs.edit { putInt(ServiceConstants.KEY_ISLAND_LEFT_ICON, islandLeftIconStyle) }
                            },
                            islandLeftIconStyle = islandLeftIconStyle,
                            onIslandLeftIconStyleChange = { index ->
                                islandLeftIconStyle = index
                                prefs.edit {
                                    putInt(if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL, index)
                                    putInt(ServiceConstants.KEY_ISLAND_LEFT_ICON, index)
                                }
                                if (index !in 0..2) {
                                    disableLyricSplitEnabled = false
                                    prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT, false) }
                                }
                            },
                            disableLyricSplitEnabled = disableLyricSplitEnabled,
                            onDisableLyricSplitToggle = { checked ->
                                disableLyricSplitEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT, checked) }
                            },
                            limitWidthEnabled = limitWidthEnabled,
                            onLimitWidthToggle = { checked ->
                                limitWidthEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH, checked) }
                            },
                            maxWidth = maxWidth,
                            onMaxWidthChange = { value ->
                                maxWidth = value
                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_ISLAND_MAX_WIDTH, value) }
                            },
                            notificationClickAction = notificationClickAction,
                            onNotificationClickActionChange = {
                                notificationClickAction = it
                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_CLICK_ACTION, it) }
                            },
                            showProgressEnabled = showProgressEnabled,
                            onShowProgressToggle = { checked ->
                                showProgressEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS, checked) }
                            },
                            progressColorEnabled = progressColorEnabled,
                            onProgressColorToggle = { checked ->
                                progressColorEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_PROGRESS_COLOR, checked) }
                            },
                            showAlbumArtEnabled = showAlbumArtEnabled,
                            onShowAlbumArtToggle = { checked ->
                                showAlbumArtEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ALBUM, checked) }
                            },
                            focusNotificationType = focusNotificationType,
                            onFocusNotificationTypeChange = {
                                focusNotificationType = it
                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_FOCUS_STYLE, it) }
                            },
                            normalNotificationTitleStyle = normalNotificationTitleStyle,
                            onNormalNotificationTitleStyleChange = {
                                normalNotificationTitleStyle = it
                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_TITLE_STYLE, it) }
                            },
                            onAutostartClick = {
                                try {
                                    val intent = Intent().apply {
                                        component = android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = "package:${context.packageName}".toUri()
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(message = msgAutostartFailed, duration = SnackbarDuration.Custom(2000L))
                                        }
                                    }
                                }
                            },
                            onBatteryOptimizationClick = {
                                try {
                                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = "package:${context.packageName}".toUri()
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(message = msgBatteryIgnored, duration = SnackbarDuration.Custom(2000L))
                                        }
                                    }
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(message = msgBatteryFailed, duration = SnackbarDuration.Custom(2000L))
                                        }
                                    }
                                }
                            },
                            onlineLyricEnabled = onlineLyricEnabled,
                            onOnlineLyricToggle = { checked ->
                                onlineLyricEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_ONLINE_LYRIC_ENABLED, checked) }
                            },
                            onlineLyricCacheLimit = onlineLyricCacheLimitState,
                            onCacheLimitClick = { showCacheLimitDialog = true },
                            highlightColorEnabled = highlightColorEnabled,
                            onHighlightColorToggle = { checked ->
                                highlightColorEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_HIGHLIGHT_COLOR, checked) }
                            },
                            songInfoHighlightColorEnabled = songInfoHighlightColorEnabled,
                            onSongInfoHighlightColorToggle = { checked ->
                                songInfoHighlightColorEnabled = checked
                                prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR, checked) }
                            },
                            bypassFocusLimitEnabled = bypassFocusLimitEnabled,
                            onBypassFocusLimitToggle = { checked ->
                                if (checked) {
                                    coroutineScope.launch {
                                        if (!ShizukuManager.isShizukuServiceRunning()) {
                                            bypassFocusLimitEnabled = false
                                            prefs.edit { putBoolean(ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT, false) }
                                            snackbarHostState.showSnackbar(
                                                message = msgShizukuNotRunning,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                            return@launch
                                        }
 
                                        val hasPermission = ShizukuManager.checkShizukuPermission()
                                        if (hasPermission) {
                                            bypassFocusLimitEnabled = true
                                            prefs.edit { putBoolean(ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT, true) }
                                        } else {
                                            bypassFocusLimitEnabled = false
                                            prefs.edit { putBoolean(ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT, false) }
                                            snackbarHostState.showSnackbar(
                                                message = msgShizukuPermissionRequired,
                                                duration = SnackbarDuration.Custom(2000L)
                                            )
                                        }
                                    }
                                } else {
                                    bypassFocusLimitEnabled = false
                                    prefs.edit { putBoolean(ServiceConstants.KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT, false) }
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
                                showAddWhitelistDialog = true
                            },
                            showAddDialog = showAddWhitelistDialog,
                            onDeleteClick = { pkg ->
                                packageToDelete = pkg
                                showDeleteWhitelistDialog = true
                            },
                            showDeleteDialog = showDeleteWhitelistDialog,
                            packageToDelete = packageToDelete
                        )
                    }
                }
            }
        }
    }
}
