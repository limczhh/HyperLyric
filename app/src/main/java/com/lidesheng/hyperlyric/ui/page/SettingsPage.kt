@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun setExcludeFromRecents(context: Context, exclude: Boolean) {
    try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.appTasks?.forEach { it.setExcludeFromRecents(exclude) }
    } catch (_: Exception) { }
}

private fun buildBackupJson(context: Context): String {
    val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
    val config = JSONObject()
    prefs.all.forEach { (key, value) ->
        if (key == RootConstants.KEY_HOOK_AI_TRANS_API_KEY) return@forEach
        when (value) {
            is Boolean -> config.put(key, value)
            is Int -> config.put(key, value)
            is Float -> config.put(key, value.toDouble())
            is Long -> config.put(key, value)
            is String -> config.put(key, value)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                config.put(key, (value as Set<String>).joinToString(","))
            }
        }
    }
    return JSONObject().apply {
        put("version", 1)
        put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        put("config", config)
    }.toString(2)
}

private fun restoreFromJson(context: Context, json: String): Boolean {
    val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
    return try {
        val root = JSONObject(json)
        if (root.optInt("version", -1) < 1) return false
        val config = root.getJSONObject("config")
        prefs.edit {
            val keys = config.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = config.get(key)
                if (key == "key_send_normal_notification" || key == "key_send_focus_notification" || key == "key_persistent_foreground"
                    || key == RootConstants.KEY_HOOK_AI_TRANS_API_KEY) continue
                if (key == com.lidesheng.hyperlyric.service.Constants.KEY_NOTIFICATION_WHITELIST
                    || key == RootConstants.KEY_HOOK_WHITELIST || key == RootConstants.KEY_HOOK_ADDED_LIST) {
                    val raw = value.toString()
                    val set = if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    putStringSet(key, set)
                    continue
                }
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Double, is Float -> putFloat(key, (value as Number).toFloat())
                    is Long -> putLong(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        true
    } catch (_: Exception) { false }
}

@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current

    val msgBackupSuccess = stringResource(R.string.toast_backup_success)
    val fmtBackupFailed = stringResource(R.string.toast_backup_failed)
    val msgRestoreEmpty = stringResource(R.string.toast_restore_empty)
    val msgRestoreSuccess = stringResource(R.string.toast_restore_success)
    val msgRestoreInvalid = stringResource(R.string.toast_restore_invalid)
    val msgRestoreFailed = stringResource(R.string.toast_restore_failed)

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val jsonBytes = buildBackupJson(context).toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(uri)?.use { it.write(jsonBytes); it.flush() }
                Toast.makeText(context, msgBackupSuccess, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, fmtBackupFailed.format(e.message), Toast.LENGTH_SHORT).show()
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: ""
                if (json.isBlank()) { Toast.makeText(context, msgRestoreEmpty, Toast.LENGTH_SHORT).show(); return@rememberLauncherForActivityResult }
                val success = restoreFromJson(context, json)
                Toast.makeText(context, if (success) msgRestoreSuccess else msgRestoreInvalid, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { Toast.makeText(context, msgRestoreFailed, Toast.LENGTH_SHORT).show() }
        }
    )

    Scaffold(
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
                modifier = Modifier.pageScrollModifiers(true, true, topAppBarScrollBehavior),
                contentPadding = contentPadding,
            ) {
                settingsSections(backupLauncher, restoreLauncher)
            }
            VerticalScrollBar(adapter = rememberScrollBarAdapter(lazyListState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), trackPadding = contentPadding)
        }
    }
}

private fun LazyListScope.settingsSections(
    backupLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    restoreLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
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
                ArrowPreference(title = stringResource(R.string.title_backup), onClick = { val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")); backupLauncher.launch("hyperlyric_backup_$dateTime.json") })
                ArrowPreference(title = stringResource(R.string.title_restore), onClick = { restoreLauncher.launch(arrayOf("application/json")) })
            }
        }
    }
    item(key = "debug_info_title") {
        SmallTitle(text = stringResource(R.string.title_debug_info))
    }
    item(key = "debug_info_content") {
        val navigator = LocalNavigator.current
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            ArrowPreference(title = stringResource(R.string.title_view_logs), onClick = { navigator.navigate(Route.Log) })
        }
    }
}
