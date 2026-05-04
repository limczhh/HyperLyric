@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import com.lidesheng.hyperlyric.BuildConfig
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import com.lidesheng.hyperlyric.ui.component.NumberInputDialog
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.component.TextInputDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
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
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import com.lidesheng.hyperlyric.lyric.commonMusicApps

@SuppressLint("BatteryLife")
@Composable
fun DynamicIslandNotificationPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    val onlineLyricCacheLimit = prefs.getInt(ServiceConstants.KEY_ONLINE_LYRIC_CACHE_LIMIT, ServiceConstants.DEFAULT_ONLINE_LYRIC_CACHE_LIMIT)
    var onlineLyricEnabled by remember { mutableStateOf(prefs.getBoolean(ServiceConstants.KEY_ONLINE_LYRIC_ENABLED, ServiceConstants.DEFAULT_ONLINE_LYRIC_ENABLED)) }
    var onlineLyricCacheLimitState by remember { mutableIntStateOf(onlineLyricCacheLimit) }
    var limitWidthEnabled by remember { mutableStateOf(prefs.getBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH, ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_LIMIT_WIDTH)) }
    var maxWidth by remember { mutableIntStateOf(prefs.getInt(ServiceConstants.KEY_NOTIFICATION_ISLAND_MAX_WIDTH, ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_MAX_WIDTH)) }
    var showCacheLimitDialog by remember { mutableStateOf(false) }


    val tabs = listOf(stringResource(R.string.title_custom_config), stringResource(R.string.title_lyric_whitelist))
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val msgAppExists = stringResource(R.string.toast_app_exists)
    val msgPkgEmpty = stringResource(R.string.toast_pkg_empty)
    val msgAutostartFailed = stringResource(R.string.toast_autostart_failed)
    val msgBatteryIgnored = stringResource(R.string.toast_battery_ignored)
    val msgBatteryFailed = stringResource(R.string.toast_battery_failed)
    val fmtSongsCount = stringResource(R.string.format_songs_count)

    LaunchedEffect(Unit) { DynamicLyricData.initWhitelist(context) }

    val whitelistSet by DynamicLyricData.whitelistState.collectAsState()
    val whitelist = remember(whitelistSet) { whitelistSet.toList() }

    var showAddWhitelistDialog by remember { mutableStateOf(false) }
    var showDeleteWhitelistDialog by remember { mutableStateOf(false) }
    var tempWhitelistInput by remember { mutableStateOf("") }
    var packageToDelete by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                Column {
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
                        }
                    )
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = pagerState.currentPage,
                        onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                    )
                }
            }
        }
    ) { padding ->
        val contentPadding = remember(padding) {
            PaddingValues(top = padding.calculateTopPadding(), start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding())
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
                        Toast.makeText(context, msgAppExists, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, msgPkgEmpty, Toast.LENGTH_SHORT).show()
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


        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = true) { page ->
            when (page) {
                0 -> {
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.pageScrollModifiers(true, true, scrollBehavior),
                            contentPadding = contentPadding
                        ) {
                            item {
                                Column {
                                    var notificationType by remember { mutableIntStateOf(prefs.getInt(ServiceConstants.KEY_NOTIFICATION_TYPE, ServiceConstants.DEFAULT_NOTIFICATION_TYPE)) }
                                    val notificationTypeOptions = listOf(stringResource(R.string.option_notification_live), stringResource(R.string.option_notification_focus))
                                    val initialIconStyleKey = if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                    var islandLeftIconStyle by remember { mutableIntStateOf(prefs.getInt(initialIconStyleKey, ServiceConstants.DEFAULT_ISLAND_LEFT_ICON)) }
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        OverlayDropdownPreference(title = stringResource(R.string.title_notification_type), items = notificationTypeOptions, selectedIndex = notificationType, onSelectedIndexChange = { index ->
                                            val oldTypeKey = if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                            prefs.edit { putInt(oldTypeKey, islandLeftIconStyle) }
                                            notificationType = index
                                            prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_TYPE, index) }
                                            val newTypeKey = if (index == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                            islandLeftIconStyle = prefs.getInt(newTypeKey, ServiceConstants.DEFAULT_ISLAND_LEFT_ICON)
                                            prefs.edit { putInt(ServiceConstants.KEY_ISLAND_LEFT_ICON, islandLeftIconStyle) }
                                        })
                                    }

                                    SmallTitle(text = stringResource(R.string.title_island_settings), insideMargin = PaddingValues(10.dp, 4.dp))
                                    var disableLyricSplitEnabled by remember { mutableStateOf(prefs.getBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT, ServiceConstants.DEFAULT_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT)) }
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            val iconStyleOptions = if (notificationType == 1) {
                                                listOf(
                                                    stringResource(R.string.option_icon_style_note),
                                                    stringResource(R.string.option_icon_style_rounded),
                                                    stringResource(R.string.option_icon_style_circular),
                                                    stringResource(R.string.option_icon_style_none)
                                                )
                                            } else {
                                                listOf(
                                                    stringResource(R.string.option_icon_style_note),
                                                    stringResource(R.string.option_icon_style_rounded),
                                                    stringResource(R.string.option_icon_style_circular)
                                                )
                                            }
                                            val iconStyleKey = if (notificationType == 1) ServiceConstants.KEY_ISLAND_LEFT_ICON_FOCUS else ServiceConstants.KEY_ISLAND_LEFT_ICON_NORMAL
                                            WindowDropdownPreference(
                                                title = stringResource(R.string.title_island_left_icon),
                                                items = iconStyleOptions,
                                                selectedIndex = islandLeftIconStyle,
                                                onSelectedIndexChange = { index ->
                                                    islandLeftIconStyle = index
                                                    prefs.edit {
                                                        putInt(iconStyleKey, index)
                                                        putInt(ServiceConstants.KEY_ISLAND_LEFT_ICON, index)
                                                    }
                                                    if (index !in 0..2) {
                                                        disableLyricSplitEnabled = false
                                                        prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT, false) }
                                                    }
                                                }
                                            )
                                            AnimatedVisibility(visible = notificationType == 1 && islandLeftIconStyle in 0..2) {
                                                SwitchPreference(title = stringResource(R.string.title_disable_lyric_split), checked = disableLyricSplitEnabled, onCheckedChange = { checked -> disableLyricSplitEnabled = checked; prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT, checked) } })
                                            }
                                            SwitchPreference(
                                                title = stringResource(R.string.title_limit_width),
                                                summary = stringResource(R.string.summary_experimental),
                                                checked = limitWidthEnabled,
                                                onCheckedChange = { checked -> limitWidthEnabled = checked; prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH, checked) } }
                                            )
                                            AnimatedVisibility(visible = limitWidthEnabled){
                                                BasicComponent  (
                                                    title = stringResource(R.string.title_limit_width_desc),
                                                    summary = stringResource(R.string.summary_limit_width),
                                                    endActions  = { top.yukonga.miuix.kmp.basic.Text("$maxWidth", fontSize = MiuixTheme.textStyles.body2.fontSize, color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                                                    bottomAction = {
                                                        Slider(
                                                            value = maxWidth.toFloat(),
                                                            onValueChange = {
                                                                maxWidth = it.toInt()
                                                                prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_ISLAND_MAX_WIDTH, it.toInt()) } },
                                                            valueRange = 100f..720f
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    SmallTitle(text = stringResource(R.string.title_notification_settings), insideMargin = PaddingValues(10.dp, 4.dp))
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        var notificationClickAction by remember { mutableIntStateOf(prefs.getInt(ServiceConstants.KEY_NOTIFICATION_CLICK_ACTION, ServiceConstants.DEFAULT_NOTIFICATION_CLICK_ACTION)) }
                                        val clickOptions = listOf(stringResource(R.string.option_click_pause), stringResource(R.string.option_click_open_app), stringResource(R.string.option_click_open_media))
                                        WindowDropdownPreference(title = stringResource(R.string.title_notification_click), items = clickOptions, selectedIndex = notificationClickAction, onSelectedIndexChange = { notificationClickAction = it; prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_CLICK_ACTION, it) } })

                                        var showProgressEnabled by remember { mutableStateOf(prefs.getBoolean(ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS, ServiceConstants.DEFAULT_NOTIFICATION_SHOW_PROGRESS)) }
                                        SwitchPreference(title = stringResource(R.string.title_show_progress), summary = stringResource(R.string.summary_show_progress), checked = showProgressEnabled, onCheckedChange = { checked -> showProgressEnabled = checked; prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_SHOW_PROGRESS, checked) } })

                                        AnimatedVisibility(visible = showProgressEnabled) {
                                            var progressColorEnabled by remember { mutableStateOf(prefs.getBoolean(ServiceConstants.KEY_NOTIFICATION_PROGRESS_COLOR, ServiceConstants.DEFAULT_NOTIFICATION_PROGRESS_COLOR)) }
                                            SwitchPreference(title = stringResource(R.string.title_progress_color), summary = stringResource(R.string.summary_progress_color), checked = progressColorEnabled, onCheckedChange = { checked -> progressColorEnabled = checked; prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_PROGRESS_COLOR, checked) } })
                                        }

                                        var showAlbumArtEnabled by remember { mutableStateOf(prefs.getBoolean(ServiceConstants.KEY_NOTIFICATION_ALBUM, ServiceConstants.DEFAULT_NOTIFICATION_ALBUM)) }
                                        SwitchPreference(title = stringResource(R.string.title_show_album_art), checked = showAlbumArtEnabled, onCheckedChange = { checked -> showAlbumArtEnabled = checked; prefs.edit { putBoolean(ServiceConstants.KEY_NOTIFICATION_ALBUM, checked) } })

                                        val focusStyleOptions = listOf("OS2", "OS3")
                                        var focusNotificationType by remember { mutableIntStateOf(prefs.getInt(ServiceConstants.KEY_NOTIFICATION_FOCUS_STYLE, ServiceConstants.DEFAULT_NOTIFICATION_FOCUS_STYLE)) }
                                        AnimatedVisibility(visible = notificationType == 1) {
                                            WindowDropdownPreference(title = stringResource(R.string.title_focus_style), items = focusStyleOptions, selectedIndex = 1 - focusNotificationType, onSelectedIndexChange = { index -> val storedValue = 1 - index; focusNotificationType = storedValue; prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_FOCUS_STYLE, storedValue) } })
                                        }

                                        val normalTitleOptions = listOf(
                                            stringResource(R.string.option_info_none),
                                            stringResource(R.string.option_info_title),
                                            stringResource(R.string.option_info_artist),
                                            stringResource(R.string.option_info_album),
                                            stringResource(R.string.option_info_title_artist),
                                            stringResource(R.string.option_info_artist_title),
                                            stringResource(R.string.option_info_artist_album)
                                        )
                                        var normalNotificationTitleStyle by remember { mutableIntStateOf(prefs.getInt(ServiceConstants.KEY_NOTIFICATION_TITLE_STYLE, ServiceConstants.DEFAULT_NOTIFICATION_TITLE_STYLE)) }
                                        WindowDropdownPreference(title = stringResource(R.string.title_song_info), items = normalTitleOptions, selectedIndex = normalNotificationTitleStyle, onSelectedIndexChange = { normalNotificationTitleStyle = it; prefs.edit { putInt(ServiceConstants.KEY_NOTIFICATION_TITLE_STYLE, it) } })
                                    }

                                    SmallTitle(text = stringResource(R.string.title_advanced_features), insideMargin = PaddingValues(10.dp, 4.dp))
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            ArrowPreference(title = stringResource(R.string.title_autostart), onClick = {
                                                try {
                                                    val intent = Intent().apply { component = android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    try {
                                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = "package:${context.packageName}".toUri() }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) { Toast.makeText(context, msgAutostartFailed, Toast.LENGTH_SHORT).show() }
                                                }
                                            })
                                            ArrowPreference(title = stringResource(R.string.title_battery_optimization), onClick = {
                                                try {
                                                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                                                    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = "package:${context.packageName}".toUri() }
                                                        context.startActivity(intent)
                                                    } else { Toast.makeText(context, msgBatteryIgnored, Toast.LENGTH_SHORT).show() }
                                                } catch (_: Exception) {
                                                    try {
                                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) { Toast.makeText(context, msgBatteryFailed, Toast.LENGTH_SHORT).show() }
                                                }
                                            })
                                            if (BuildConfig.ONLINE_FEATURES_ENABLED) {
                                                SwitchPreference(title = stringResource(R.string.title_online_lyric), summary = stringResource(R.string.summary_online_lyric), checked = onlineLyricEnabled, onCheckedChange = { checked -> onlineLyricEnabled = checked; prefs.edit { putBoolean(ServiceConstants.KEY_ONLINE_LYRIC_ENABLED, checked) } })
                                                if (onlineLyricEnabled) {
                                                    ArrowPreference(title = stringResource(R.string.dialog_cache_limit_title), summary = fmtSongsCount.format(onlineLyricCacheLimitState), onClick = { showCacheLimitDialog = true })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollBar(
                            adapter = rememberScrollBarAdapter(lazyListState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            trackPadding = contentPadding,
                        )
                    }
                }
                1 -> {
                    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.pageScrollModifiers(true, true, scrollBehavior),
                            contentPadding = contentPadding
                        ) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    ArrowPreference(title = stringResource(R.string.title_add_whitelist), onClick = { tempWhitelistInput = ""; showAddWhitelistDialog = true }, holdDownState = showAddWhitelistDialog)
                                }
                            }
                            item { SmallTitle(text = stringResource(R.string.title_added_apps), insideMargin = PaddingValues(10.dp, 4.dp)) }
                            item {
                                if (whitelist.isNotEmpty()) {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            whitelist.forEachIndexed { _, packageName ->
                                                val appName = commonMusicApps[packageName]
                                                BasicComponent(
                                                    title = appName ?: packageName,
                                                    summary = if (appName != null) packageName else null,
                                                    endActions = {
                                                        IconButton(onClick = { packageToDelete = packageName; showDeleteWhitelistDialog = true }) { Icon(imageVector = MiuixIcons.Delete, contentDescription = stringResource(R.string.delete), tint = MiuixTheme.colorScheme.onSurfaceVariantActions) }
                                                    },
                                                    onClick = { packageToDelete = packageName; showDeleteWhitelistDialog = true },
                                                    holdDownState = showDeleteWhitelistDialog && packageToDelete == packageName
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        BasicComponent(
                                            title = stringResource(R.string.title_no_whitelist),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                        VerticalScrollBar(
                            adapter = rememberScrollBarAdapter(lazyListState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            trackPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
}
