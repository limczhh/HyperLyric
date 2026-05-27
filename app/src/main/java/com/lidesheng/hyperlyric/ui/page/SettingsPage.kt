package com.lidesheng.hyperlyric.ui.page

import android.content.Context

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.lidesheng.hyperlyric.root.utils.ConfigSync
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun setExcludeFromRecents(context: Context, exclude: Boolean) {
    try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.appTasks?.forEach { it.setExcludeFromRecents(exclude) }
    } catch (_: Exception) { }
}

@Composable
fun SettingsPage() {
    val navigator = LocalNavigator.current

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val backupRestoreHelper = com.lidesheng.hyperlyric.utils.rememberBackupRestoreHelper(snackbarHostState)

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_settings_page),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
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
            PaddingValues(top = top, start = 0.dp, end = 0.dp, bottom = bottom + 16.dp)
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding,
            ) {
                settingsSections(backupRestoreHelper)
            }
        }
    }
}

private fun LazyListScope.settingsSections(
    backupRestoreHelper: com.lidesheng.hyperlyric.utils.BackupRestoreHelper
) {
    item(key = "personalization_title") {
        SmallTitle(text = stringResource(R.string.title_personalization))
    }
    item(key = "personalization_content") {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
        var themeMode by remember { mutableIntStateOf(prefs.getInt(UIConstants.KEY_THEME_MODE, UIConstants.DEFAULT_THEME_MODE)) }
        val themeOptions = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_system_monet), stringResource(R.string.theme_light_monet), stringResource(R.string.theme_dark_monet))

        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                WindowDropdownPreference(title = stringResource(R.string.title_theme), items = themeOptions, selectedIndex = themeMode, onSelectedIndexChange = { themeMode = it; prefs.edit { putInt(UIConstants.KEY_THEME_MODE, it) } })
                if (themeMode >= 3) {
                    var monetColorIndex by remember { mutableIntStateOf(prefs.getInt(UIConstants.KEY_MONET_COLOR, UIConstants.DEFAULT_MONET_COLOR)) }
                    val monetOptions = listOf(stringResource(R.string.monet_default), stringResource(R.string.monet_blue), stringResource(R.string.monet_green), stringResource(R.string.monet_red), stringResource(R.string.monet_yellow), stringResource(R.string.monet_orange), stringResource(R.string.monet_purple), stringResource(R.string.monet_pink))
                    WindowDropdownPreference(title = stringResource(R.string.title_monet), items = monetOptions, selectedIndex = monetColorIndex, onSelectedIndexChange = { monetColorIndex = it; prefs.edit { putInt(UIConstants.KEY_MONET_COLOR, it) } })
                }
                var predictiveBackGestureEnabled by remember { mutableStateOf(prefs.getBoolean(UIConstants.KEY_PREDICTIVE_BACK_GESTURE, UIConstants.DEFAULT_PREDICTIVE_BACK_GESTURE)) }
                val activity = androidx.activity.compose.LocalActivity.current
                SwitchPreference(title = stringResource(R.string.title_predictive_back), checked = predictiveBackGestureEnabled, onCheckedChange = {
                    predictiveBackGestureEnabled = it; prefs.edit { putBoolean(UIConstants.KEY_PREDICTIVE_BACK_GESTURE, it) }
                    runCatching { org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"); val m = android.content.pm.ApplicationInfo::class.java.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType); m.isAccessible = true; m.invoke(context.applicationInfo, it) }
                    activity?.recreate()
                })
                var floatingNavBarEnabled by remember { mutableStateOf(prefs.getBoolean(UIConstants.KEY_FLOATING_NAV_BAR, UIConstants.DEFAULT_FLOATING_NAV_BAR)) }
                SwitchPreference(title = stringResource(R.string.title_floating_nav), checked = floatingNavBarEnabled, onCheckedChange = { floatingNavBarEnabled = it; prefs.edit { putBoolean(UIConstants.KEY_FLOATING_NAV_BAR, it) } })
                var excludeFromRecents by remember { mutableStateOf(prefs.getBoolean(UIConstants.KEY_EXCLUDE_FROM_RECENTS, UIConstants.DEFAULT_EXCLUDE_FROM_RECENTS)) }
                SwitchPreference(title = stringResource(R.string.title_exclude_from_recents), checked = excludeFromRecents, onCheckedChange = { excludeFromRecents = it; prefs.edit { putBoolean(UIConstants.KEY_EXCLUDE_FROM_RECENTS, it) }; setExcludeFromRecents(context, it) })
            }
        }
    }
    item(key = "config_management_title") {
        SmallTitle(text = stringResource(R.string.title_config_management))
    }
    item(key = "config_management_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                ArrowPreference(title = stringResource(R.string.title_backup), onClick = { backupRestoreHelper.launchBackup() })
                ArrowPreference(title = stringResource(R.string.title_restore), onClick = { backupRestoreHelper.launchRestore() })
            }
        }
    }
    item(key = "debug_info_title") {
        SmallTitle(text = stringResource(R.string.title_debug_info))
    }
    item(key = "debug_info_content") {
        val navigator = LocalNavigator.current
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
        var logLevel by remember { mutableIntStateOf(prefs.getInt(UIConstants.KEY_LOG_LEVEL, UIConstants.DEFAULT_LOG_LEVEL)) }
        val logLevelOptions = listOf(stringResource(R.string.log_level_normal), stringResource(R.string.log_level_verbose))
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                WindowDropdownPreference(
                    title = stringResource(R.string.title_log_level), 
                    items = logLevelOptions, 
                    selectedIndex = logLevel, 
                    onSelectedIndexChange = { 
                        logLevel = it; 
                        prefs.edit { putInt(UIConstants.KEY_LOG_LEVEL, it) }; 
                        ConfigSync.syncPreference(UIConstants.PREF_NAME, UIConstants.KEY_LOG_LEVEL, it) 
                        }
                )
                ArrowPreference(
                    title = stringResource(R.string.title_view_logs), 
                    onClick = { 
                        navigator.navigate(Route.Log) 
                    }
                )
            }
        }
    }
}
