@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.root.RootApplication
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.service.LiveLyricService
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.page.main.AboutPage
import com.lidesheng.hyperlyric.ui.page.main.HomePage
import com.lidesheng.hyperlyric.ui.page.main.rememberMainPagerState
import com.lidesheng.hyperlyric.ui.utils.QuotesData
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import com.lidesheng.hyperlyric.utils.MigrationData
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate

@Composable
fun MainPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- pager ---
    val pagerState = rememberPagerState(pageCount = { 2 })
    val mainPagerState = rememberMainPagerState(pagerState)
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    // --- quote ---
    var randomQuote by rememberSaveable { mutableStateOf(QuotesData.list.random()) }
    val dateSpecificQuote = remember { QuotesData.quoteFor(LocalDate.now()) }

    // --- toast messages ---
    val msgNoRoot = stringResource(R.string.toast_no_root)
    val msgXposedNotActive = stringResource(R.string.toast_xposed_module_not_active)

    // --- prefs & state ---
    val prefs =
        remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    var floatingNavBarEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                UIConstants.KEY_FLOATING_NAV_BAR,
                UIConstants.DEFAULT_FLOATING_NAV_BAR
            )
        )
    }
    var removeFocusWhitelist by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST,
                RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST
            )
        )
    }
    // --- dialogs ---
    var showRestartDialog by remember { mutableStateOf(false) }

    // --- pref listener ---
    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                UIConstants.KEY_FLOATING_NAV_BAR ->
                    floatingNavBarEnabled = p.getBoolean(
                        UIConstants.KEY_FLOATING_NAV_BAR,
                        UIConstants.DEFAULT_FLOATING_NAV_BAR
                    )

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
        val hasListenerPermission = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
        val isDynamicIslandEnabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_ENABLE_DYNAMIC_ISLAND,
            RootConstants.DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND
        )
        if (hasListenerPermission && isDynamicIslandEnabled) {
            LiveLyricService.ensureListenerBound(context)
        }
    }

    // --- system back ---
    BackHandler(enabled = mainPagerState.selectedPage != 0) {
        mainPagerState.animateToPage(0)
    }

    // --- callbacks (remembered for reference stability) ---
    val toggleRemoveFocusWhitelist: (Boolean) -> Unit = remember {
        { checked ->
            if (checked) {
                if (RootApplication.xposedService != null) {
                    removeFocusWhitelist = true
                    prefs.edit { putBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, true) }
                    PrefsBridge.putBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, true)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = msgXposedNotActive,
                            duration = SnackbarDuration.Custom(2000L)
                        )
                    }
                }
            } else {
                removeFocusWhitelist = false
                prefs.edit { putBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, false) }
                PrefsBridge.putBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, false)
            }
        }
    }

    // --- migration check ---
    var migrationNotes by remember {
        mutableStateOf<List<com.lidesheng.hyperlyric.utils.MigrationNote>>(
            emptyList()
        )
    }
    val migrationTitle = stringResource(R.string.migration_dialog_title)
    LaunchedEffect(Unit) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = PackageInfoCompat.getLongVersionCode(pInfo)
            val lastSeen = prefs.getLong(UIConstants.KEY_LAST_SEEN_VERSION, 0)
            if (currentVersion != lastSeen) {
                val matched =
                    MigrationData.notes.filter { it.versionCode.toLong() == currentVersion }
                if (matched.isNotEmpty()) {
                    migrationNotes = matched
                }
            }
        } catch (_: Exception) {
        }
    }

    // --- about page data ---
    val aboutAppVersion: String? = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = pInfo.versionName ?: return@remember null
            "${PackageInfoCompat.getLongVersionCode(pInfo)}-$versionName"
        } catch (_: Exception) {
            null
        }
    }
    val aboutDeviceModel = remember { getSystemProperty("ro.product.marketname") ?: Build.MODEL }
    val aboutOsVersion =
        remember { getSystemProperty("ro.build.version.incremental") ?: Build.DISPLAY }
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
                    snackbarHostState.showSnackbar(
                        message = msgNoRoot,
                        duration = SnackbarDuration.Custom(2000L)
                    )
                }
            }
        }
    )

    // --- migration dialog ---
    if (migrationNotes.isNotEmpty()) {
        WindowDialog(
            title = migrationTitle,
            show = migrationNotes.isNotEmpty(),
            onDismissRequest = {}
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        migrationNotes.flatMap { it.items }.forEach { item ->
                            if (item.url != null) {
                                BasicComponent(
                                    title = item.text,
                                    summary = item.summary,
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                item.url.toUri()
                                            )
                                        )
                                    }
                                )
                            } else {
                                BasicComponent(title = item.text, summary = item.summary)
                            }
                        }
                    }
                }
                TextButton(
                    text = stringResource(R.string.confirm),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val currentVersion = PackageInfoCompat.getLongVersionCode(pInfo)
                        prefs.edit { putLong(UIConstants.KEY_LAST_SEEN_VERSION, currentVersion) }
                        migrationNotes = emptyList()
                    }
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
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
                                            BlendColorEntry(
                                                color = MiuixTheme.colorScheme.surface.copy(
                                                    0.8f
                                                )
                                            ),
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
                val floatingBarColor =
                    if (outerBlurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
                val floatingBarShape = RoundedCornerShape(FloatingToolbarDefaults.CornerRadius)
                val isDark = isSystemInDarkTheme()
                val floatingHighlight = remember(isDark) {
                    if (isDark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight
                }
                FloatingNavigationBar(
                    modifier = (if (outerBlurActive) {
                        Modifier
                            .textureBlur(
                                backdrop = outerBackdrop,
                                shape = floatingBarShape,
                                blurRadius = 25f,
                                colors = BlurColors(
                                    blendColors = listOf(
                                        BlendColorEntry(
                                            color = MiuixTheme.colorScheme.surfaceContainer.copy(
                                                0.6f
                                            )
                                        ),
                                    ),
                                ),
                                highlight = floatingHighlight,
                            )
                    } else {
                        Modifier
                    }).padding(horizontal = 12.dp),
                    color = floatingBarColor,
                ) {
                    navItems.forEachIndexed { index, item ->
                        FloatingNavigationBarItem(
                            selected = mainPagerState.selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = item.icon,
                            label = item.label
                        )
                        if (index < navItems.size - 1) {
                            Spacer(Modifier.width(12.dp))
                        }
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
                        quote = dateSpecificQuote ?: randomQuote,
                        isDateSpecificQuote = dateSpecificQuote != null,
                        onQuoteClick = { randomQuote = QuotesData.list.random() },
                        onQuoteLongPress = { navigator.navigate(Route.Poetry) },
                        onSuperIslandConfigClick = { navigator.navigate(Route.HookSettings) },
                        onMediaCardConfigClick = { navigator.navigate(Route.MediaCardSettings) },
                        onDynamicIslandConfigClick = { navigator.navigate(Route.DynamicIslandNotification) },
                        onRestartClick = { showRestartDialog = true },
                        removeFocusWhitelist = removeFocusWhitelist,
                        onRemoveFocusWhitelistToggle = toggleRemoveFocusWhitelist,
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
                        onChangelogClick = { navigator.navigate(Route.Changelog) },
                        onContributorsClick = { navigator.navigate(Route.Contributors) },
                    )
                }
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
