package com.lidesheng.hyperlyric.ui.page

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.Quotes
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.service.LiveLyricService
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun MainPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    var randomQuote by rememberSaveable { mutableStateOf(Quotes.list.random()) }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    
    // Toast messages fetched at top level to avoid Lint warnings
    val msgPermissionGranted = stringResource(R.string.toast_permission_granted)
    val msgPermissionDenied = stringResource(R.string.toast_permission_denied)
    val msgNoRoot = stringResource(R.string.toast_no_root)
    val msgPermissionNotGranted = stringResource(R.string.toast_permission_not_granted)
    val msgOpenSettingsFailed = stringResource(R.string.toast_open_settings_failed)

    var showRestartDialog by remember { mutableStateOf(false) }

    val navItems = listOf(
        NavigationItem(stringResource(R.string.home), MiuixIcons.Settings),
        NavigationItem(stringResource(R.string.about), MiuixIcons.Info)
    )

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )
    
    val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    var floatingNavBarEnabled by remember { mutableStateOf(prefs.getBoolean(UIConstants.KEY_FLOATING_NAV_BAR, UIConstants.DEFAULT_FLOATING_NAV_BAR)) }
    var enableSuperIsland by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) }
    var enableDynamicIsland by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND)) }

    var showPermissionSheet by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            val message = if (isGranted) msgPermissionGranted else msgPermissionDenied
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    )

    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                UIConstants.KEY_FLOATING_NAV_BAR -> floatingNavBarEnabled = p.getBoolean(UIConstants.KEY_FLOATING_NAV_BAR, UIConstants.DEFAULT_FLOATING_NAV_BAR)
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND -> enableSuperIsland = p.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
                RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND -> enableDynamicIsland = p.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND)
            }
        }
    }

    LaunchedEffect(Unit) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val isDynamicIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND)

        if (hasListenerPermission && isDynamicIslandEnabled) {
            LiveLyricService.ensureListenerBound(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = if (pagerState.currentPage == 0) "HyperLyric" else stringResource(R.string.about),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 25.dp
                    noiseFactor = 0f
                }
            )
        },
        bottomBar = {
            if (floatingNavBarEnabled) {
                FloatingNavigationBar(
                    mode = FloatingNavigationBarDisplayMode.IconOnly,
                    showDivider = true,
                    color = Color.Transparent,
                    modifier = Modifier
                        .hazeEffect(hazeState) {
                            style = hazeStyle
                            blurRadius = 5.dp
                            noiseFactor = 0f
                        }
                ) {
                    navItems.forEachIndexed { index, item ->
                        FloatingNavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            } else {
                NavigationBar(
                    mode = NavigationBarDisplayMode.IconWithSelectedLabel,
                    color = Color.Transparent,
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            }
        }
    ) { padding ->
        WindowDialog(
            title = stringResource(R.string.dialog_restart_title),
            summary = stringResource(R.string.dialog_restart_summary),
            show = showRestartDialog,
            onDismissRequest = { showRestartDialog = false }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showRestartDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showRestartDialog = false
                        scope.launch {
                            val success = ShellUtils.restartSystemUI()
                            if (!success) {
                                Toast.makeText(context, msgNoRoot, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->

            if (page == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .hazeSource(state = hazeState)
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        start = 12.dp,
                        end = 12.dp,
                        bottom = padding.calculateBottomPadding()
                    )
                ) {
                    item {
                        Column {
                             Card(
                                 modifier = Modifier.fillMaxWidth(),
                                 onClick = {
                                     randomQuote = Quotes.list.random()
                                 },
                                 onLongPress = {
                                     navigator.navigate(Route.Poetry)
                                 }
                             ) {
                                Text(
                                    text = randomQuote,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            SmallTitle(
                                text = stringResource(R.string.title_basic_features),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    SwitchPreference(
                                        title = stringResource(R.string.title_super_island_lyrics),
                                        summary = stringResource(R.string.summary_super_island_lyrics),
                                        checked = enableSuperIsland,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                if (ConfigSync.xposedService != null) {
                                                    enableSuperIsland = true
                                                    prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, true) }
                                                    ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, true)
                                                } else {
                                                    Toast.makeText(context, R.string.toast_xposed_module_not_active, Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                enableSuperIsland = false
                                                prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, false) }
                                                ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, false)
                                            }
                                        }
                                    )
                                    AnimatedVisibility(visible = enableSuperIsland) {
                                        ArrowPreference(
                                            title = stringResource(R.string.title_super_island_config),
                                            onClick = {
                                                navigator.navigate(Route.HookSettings)
                                            }
                                        )
                                    }
                                    SwitchPreference(
                                        title = stringResource(R.string.title_dynamic_island_lyrics),
                                        summary = stringResource(R.string.summary_dynamic_island_lyrics),
                                        checked = enableDynamicIsland,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) {
                                                val hasPostNotification =
                                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                                val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

                                                if (hasPostNotification && hasListenerPermission) {
                                                    enableDynamicIsland = true
                                                    prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true) }
                                                    ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true)
                                                    LiveLyricService.ensureListenerBound(context)
                                                } else {
                                                    showPermissionSheet = true
                                                }
                                            } else {
                                                enableDynamicIsland = false
                                                prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, false) }
                                                ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, false)
                                            }
                                        }
                                    )
                                    AnimatedVisibility(visible = enableDynamicIsland) {
                                        ArrowPreference(
                                            title = stringResource(R.string.title_dynamic_island_config),
                                            onClick = {
                                                navigator.navigate(Route.DynamicIslandNotification)
                                            }
                                        )
                                    }
                                }
                            }

                            SmallTitle(
                                text = stringResource(R.string.title_special_features),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )

                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    ArrowPreference(
                                        title = stringResource(R.string.title_restart_ui),
                                        onClick = { showRestartDialog = true }
                                    )
                                    var removeFocusWhitelist by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST)) }
                                    SwitchPreference(
                                        title = stringResource(R.string.title_remove_focus_whitelist),
                                        summary = stringResource(R.string.summary_remove_focus_whitelist),
                                        checked = removeFocusWhitelist,
                                        onCheckedChange = {
                                            removeFocusWhitelist = it
                                            prefs.edit { putBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, it) }
                                            ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, it)
                                        }
                                    )
                                    var removeIslandWhitelist by remember { mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST)) }
                                    SwitchPreference(
                                        title = stringResource(R.string.title_remove_island_whitelist),
                                        checked = removeIslandWhitelist,
                                        onCheckedChange = {
                                            removeIslandWhitelist = it
                                            prefs.edit { putBoolean(RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, it) }
                                            ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, it)
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Card(modifier = Modifier.fillMaxWidth()) {
                                ArrowPreference(
                                    title = stringResource(R.string.title_app_settings),
                                    summary = stringResource(R.string.summary_app_settings),
                                    onClick = {
                                        navigator.navigate(Route.Settings)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .hazeSource(state = hazeState)
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        start = 12.dp,
                        end = 12.dp,
                        bottom = padding.calculateBottomPadding()
                    )
                ) {
                    item {
                        AboutContent(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    WindowBottomSheet(
        show = showPermissionSheet,
        title = stringResource(R.string.sheet_permission_title),
        allowDismiss = false,
        startAction = {
            IconButton(onClick = { showPermissionSheet = false }) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            IconButton(onClick = {
                val hasPostNotification =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                if (hasPostNotification && hasListenerPermission) {
                    showPermissionSheet = false
                    enableDynamicIsland = true
                    prefs.edit { putBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true) }
                    ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, true)
                    LiveLyricService.ensureListenerBound(context)
                } else {
                    Toast.makeText(context, msgPermissionNotGranted, Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.confirm),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = {
            showPermissionSheet = false
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()
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
                            Toast.makeText(context, msgOpenSettingsFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AboutContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val versionUnknown = stringResource(R.string.version_unknown)
    val appVersion = remember(versionUnknown) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName
            val vCode = PackageInfoCompat.getLongVersionCode(pInfo)
            "v $vName-$vCode"
        } catch (_: Exception) {
            versionUnknown
        }
    }


    val deviceModel = remember {
        getSystemProperty("ro.product.marketname") ?: Build.MODEL
    }
    val osVersion = remember {
        getSystemProperty("ro.build.version.incremental") ?: Build.DISPLAY
    }
    val androidVersion = Build.VERSION.RELEASE

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "HyperLyric",
            style = MiuixTheme.textStyles.title1,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = appVersion,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Column {
        SmallTitle(
            text = stringResource(R.string.title_system_info),
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                BasicComponent(title = deviceModel, summary = stringResource(R.string.info_device_model))
                BasicComponent(title = osVersion, summary = stringResource(R.string.info_os_version))
                BasicComponent(title = androidVersion, summary = stringResource(R.string.info_android_version))
            }
        }

        SmallTitle(
            text = stringResource(R.string.title_help),
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            ArrowPreference(
                title = stringResource(R.string.title_help),
                onClick = {
                    navigator.navigate(Route.Help)
                }
            )
        }

        SmallTitle(
            text = stringResource(R.string.title_developer),
            insideMargin = PaddingValues(10.dp, 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.dev_name),
                startAction = {
                    Image(
                        painter = painterResource(id = R.drawable.avatar),
                        contentDescription = stringResource(R.string.content_description_avatar),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer)
                    )
                },
                endActions = {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = stringResource(R.string.content_description_go),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                },
                onClick = {
                    val uri = "https://github.com/limczhh/HyperLyric".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    try {
                        intent.setPackage("com.coolapk.market")
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        intent.setPackage(null)
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }
                }
            )
            ArrowPreference(
                title = stringResource(R.string.title_licenses),
                summary = stringResource(R.string.summary_licenses),
                onClick = {
                    navigator.navigate(Route.Licenses)
                }
            )
        }
    }
}

fun getSystemProperty(key: String): String? {
    return try {
        val process = Runtime.getRuntime().exec("getprop $key")
        BufferedReader(InputStreamReader(process.inputStream)).use {
            val line = it.readLine()
            if (line.isNullOrEmpty()) null else line
        }
    } catch (_: Exception) {
        null
    }
}
