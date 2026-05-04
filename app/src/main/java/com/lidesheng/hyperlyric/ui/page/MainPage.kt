@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.Quotes
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.service.LiveLyricService
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import com.lidesheng.hyperlyric.ui.page.main.AboutPage
import com.lidesheng.hyperlyric.ui.page.main.HomePage
import com.lidesheng.hyperlyric.ui.page.main.rememberMainPagerState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun MainPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()

    // --- pager ---
    val pagerState = rememberPagerState(pageCount = { 2 })
    val mainPagerState = rememberMainPagerState(pagerState)
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    // --- quote ---
    var randomQuote by rememberSaveable { mutableStateOf(Quotes.list.random()) }

    // --- toast messages ---
    val msgPermissionGranted = stringResource(R.string.toast_permission_granted)
    val msgPermissionDenied = stringResource(R.string.toast_permission_denied)
    val msgNoRoot = stringResource(R.string.toast_no_root)
    val msgPermissionNotGranted = stringResource(R.string.toast_permission_not_granted)
    val msgOpenSettingsFailed = stringResource(R.string.toast_open_settings_failed)

    // --- prefs & state ---
    val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    var floatingNavBarEnabled by remember {
        mutableStateOf(prefs.getBoolean(UIConstants.KEY_FLOATING_NAV_BAR, UIConstants.DEFAULT_FLOATING_NAV_BAR))
    }
    var enableSuperIsland by remember {
        mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND))
    }
    var enableDynamicIsland by remember {
        mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND))
    }
    var removeFocusWhitelist by remember {
        mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST))
    }
    var removeIslandWhitelist by remember {
        mutableStateOf(prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST))
    }

    // --- dialogs ---
    var showRestartDialog by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }

    // --- permission launcher ---
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            Toast.makeText(
                context,
                if (isGranted) msgPermissionGranted else msgPermissionDenied,
                Toast.LENGTH_SHORT
            ).show()
        }
    )

    // --- pref listener ---
    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                UIConstants.KEY_FLOATING_NAV_BAR ->
                    floatingNavBarEnabled = p.getBoolean(UIConstants.KEY_FLOATING_NAV_BAR, UIConstants.DEFAULT_FLOATING_NAV_BAR)
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND ->
                    enableSuperIsland = p.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
                RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND ->
                    enableDynamicIsland = p.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND)
            }
        }
    }

    DisposableEffect(prefs) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val isDynamicIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND)
        if (hasListenerPermission && isDynamicIslandEnabled) {
            LiveLyricService.ensureListenerBound(context)
        }
    }

    // --- system back ---
    BackHandler(enabled = mainPagerState.selectedPage != 0) {
        mainPagerState.animateToPage(0)
    }

    // --- callbacks (remembered for reference stability) ---
    val toggleSuperIsland = remember { { isChecked: Boolean ->
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
    } }

    val toggleDynamicIsland = remember { { isChecked: Boolean ->
        if (isChecked) {
            val hasPostNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
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
    } }

    val toggleRemoveFocusWhitelist = remember { { checked: Boolean ->
        removeFocusWhitelist = checked
        prefs.edit { putBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, checked) }
        ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, checked)
    } }

    val toggleRemoveIslandWhitelist = remember { { checked: Boolean ->
        removeIslandWhitelist = checked
        prefs.edit { putBoolean(RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, checked) }
        ConfigSync.syncPreference(UIConstants.PREF_NAME, RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, checked)
    } }

    val confirmPermissionSheet = remember { {
        val hasPostNotification = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
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
    } }

    // --- about page data ---
    val aboutAppVersion: String? = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "v ${pInfo.versionName}-${PackageInfoCompat.getLongVersionCode(pInfo)}"
        } catch (_: Exception) {
            null
        }
    }
    val aboutDeviceModel = remember { getSystemProperty("ro.product.marketname") ?: Build.MODEL }
    val aboutOsVersion = remember { getSystemProperty("ro.build.version.incremental") ?: Build.DISPLAY }
    val aboutAndroidVersion = Build.VERSION.RELEASE

    // --- nav items ---
    val homeLabel = stringResource(R.string.home)
    val aboutLabel = stringResource(R.string.about)
    val navItems = remember(homeLabel, aboutLabel) {
        listOf(
            NavigationItem(homeLabel, MiuixIcons.Settings),
            NavigationItem(aboutLabel, MiuixIcons.Info)
        )
    }

    // --- outer backdrop (bottom bar blur) ---
    val outerBackdrop = rememberBlurBackdrop()
    val outerBlurActive = outerBackdrop != null
    val outerBarColor = if (outerBlurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    // --- dialogs at outer level ---
    SimpleDialog(
        show = showRestartDialog,
        title = stringResource(R.string.dialog_restart_title),
        summary = stringResource(R.string.dialog_restart_summary),
        onDismiss = { showRestartDialog = false },
        onConfirm = {
            showRestartDialog = false
            scope.launch {
                val success = ShellUtils.restartSystemUI()
                if (!success) {
                    Toast.makeText(context, msgNoRoot, Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = !floatingNavBarEnabled,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (outerBlurActive) {
                                Modifier.textureBlur(
                                    backdrop = outerBackdrop,
                                    shape = RectangleShape,
                                    blurRadius = 25f,
                                    colors = BlurColors(
                                        blendColors = listOf(
                                            BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                                        ),
                                    ),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .background(outerBarColor)
                ) {
                    NavigationBar(
                        mode = NavigationBarDisplayMode.IconWithSelectedLabel,
                        color = outerBarColor
                    ) {
                        navItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = mainPagerState.selectedPage == index,
                                onClick = { mainPagerState.animateToPage(index) },
                                icon = item.icon,
                                label = item.label
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = floatingNavBarEnabled,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                val floatingBarColor = if (outerBlurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
                val floatingBarShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
                val isDark = isSystemInDarkTheme()
                val floatingHighlight = remember(isDark) {
                    if (isDark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight
                }
                FloatingNavigationBar(
                    modifier = if (outerBlurActive) {
                        Modifier
                            .textureBlur(
                                backdrop = outerBackdrop,
                                shape = floatingBarShape,
                                blurRadius = 25f,
                                colors = BlurColors(
                                    blendColors = listOf(
                                        BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.6f)),
                                    ),
                                ),
                                highlight = floatingHighlight,
                            )
                    } else {
                        Modifier
                    },
                    color = floatingBarColor,
                    mode = FloatingNavigationBarDisplayMode.IconOnly
                ) {
                    navItems.forEachIndexed { index, item ->
                        FloatingNavigationBarItem(
                            selected = mainPagerState.selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = if (outerBackdrop != null) Modifier.layerBackdrop(outerBackdrop) else Modifier) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.imePadding(),
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
            ) { page ->
                if (page == 0) {
                    HomePage(
                        outerPadding = innerPadding,
                        randomQuote = randomQuote,
                        onQuoteClick = { randomQuote = Quotes.list.random() },
                        onQuoteLongPress = { navigator.navigate(Route.Poetry) },
                        enableSuperIsland = enableSuperIsland,
                        onSuperIslandToggle = toggleSuperIsland,
                        enableDynamicIsland = enableDynamicIsland,
                        onDynamicIslandToggle = toggleDynamicIsland,
                        onSuperIslandConfigClick = { navigator.navigate(Route.HookSettings) },
                        onDynamicIslandConfigClick = { navigator.navigate(Route.DynamicIslandNotification) },
                        onRestartClick = { showRestartDialog = true },
                        removeFocusWhitelist = removeFocusWhitelist,
                        onRemoveFocusWhitelistToggle = toggleRemoveFocusWhitelist,
                        removeIslandWhitelist = removeIslandWhitelist,
                        onRemoveIslandWhitelistToggle = toggleRemoveIslandWhitelist,
                        onAppSettingsClick = { navigator.navigate(Route.Settings) },
                    )
                } else {
                    AboutPage(
                        outerPadding = innerPadding,
                        aboutAppVersion = aboutAppVersion,
                        aboutDeviceModel = aboutDeviceModel,
                        aboutOsVersion = aboutOsVersion,
                        aboutAndroidVersion = aboutAndroidVersion,
                        onHelpClick = { navigator.navigate(Route.Help) },
                        onLicensesClick = { navigator.navigate(Route.Licenses) },
                    )
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
            IconButton(onClick = confirmPermissionSheet) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.confirm),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = { showPermissionSheet = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.title_permission_post_notification),
                    onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
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

private fun getSystemProperty(key: String): String? {
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
