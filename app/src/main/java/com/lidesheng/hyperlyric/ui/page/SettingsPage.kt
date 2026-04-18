package com.lidesheng.hyperlyric.ui.page

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private fun setExcludeFromRecents(context: Context, exclude: Boolean) {
    try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        am.appTasks?.forEach {
            it.setExcludeFromRecents(exclude)
        }
    } catch (_: Exception) { }
}

private fun buildBackupJson(context: Context): String {
    val prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    val config = JSONObject()
    prefs.all.forEach { (key, value) ->
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

    val root = JSONObject().apply {
        put("version", 1)
        put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        put("config", config)
    }
    return root.toString(2)
}

private fun restoreFromJson(context: Context, json: String): Boolean {
    val prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
    return try {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        if (version < 1) return false

        val config = root.getJSONObject("config")

        prefs.edit {
            val keys = config.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = config.get(key)

                if (key == "key_send_normal_notification" || key == "key_send_focus_notification" || key == "key_persistent_foreground") {
                    continue
                }

                if (key == Constants.KEY_WHITELIST) {
                    val raw = value.toString()
                    val set = if (raw.isBlank()) emptySet() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    putStringSet(key, set)
                    continue
                }

                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> {
                        val boundedValue = when (key) {
                            Constants.KEY_TEXT_SIZE -> value.coerceIn(8, 27)
                            Constants.KEY_MAX_LEFT_WIDTH -> value.coerceIn(40, 280)
                            Constants.KEY_MARQUEE_SPEED -> value.coerceIn(10, 500)
                            Constants.KEY_MARQUEE_DELAY -> value.coerceIn(0, 5000)
                            Constants.KEY_MARQUEE_LOOP_DELAY -> value.coerceIn(0, 5000)
                            Constants.KEY_FADING_EDGE_LENGTH -> value.coerceIn(0, 100)
                            Constants.KEY_ANIM_MODE -> value.coerceIn(0, 4)
                            Constants.KEY_ONLINE_LYRIC_CACHE_LIMIT -> value.coerceIn(1, 1000)
                            Constants.KEY_NOTIFICATION_CLICK_ACTION -> value.coerceIn(0, 2)
                            Constants.KEY_WORK_MODE -> value.coerceIn(0, 1)
                            Constants.KEY_THEME_MODE -> value.coerceIn(0, 5)
                            Constants.KEY_MONET_COLOR -> value.coerceIn(0, 7)
                            Constants.KEY_NOTIFICATION_TYPE -> value.coerceIn(0, 1)
                            Constants.KEY_FOCUS_NOTIFICATION_TYPE -> value.coerceIn(0, 1)
                            Constants.KEY_FONT_WEIGHT -> value.coerceIn(100, 900)
                            else -> value
                        }
                        putInt(key, boundedValue)
                    }
                    is Double, is Float -> putFloat(key, (value as Number).toFloat())
                    is Long -> putLong(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val jsonBytes = buildBackupJson(context).toByteArray(Charsets.UTF_8)
                val output = context.contentResolver.openOutputStream(uri)
                if (output != null) {
                    output.use { it.write(jsonBytes); it.flush() }
                    Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "备份失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                if (json.isBlank()) {
                    Toast.makeText(context, "文件内容为空", Toast.LENGTH_SHORT).show()
                    return@rememberLauncherForActivityResult
                }
                val success = restoreFromJson(context, json)
                Toast.makeText(
                    context,
                    if (success) "恢复成功，请重启应用" else "文件格式无效",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (_: Exception) {
                Toast.makeText(context, "恢复失败", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = "应用设置",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() }
                    ) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 25.dp
                    noiseFactor = 0f
                }
            )
        }
    ) { padding ->
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
                bottom = padding.calculateBottomPadding() + 16.dp
            ),
        ) {
            item {
                SmallTitle(
                    text = "个性化设置",
                    insideMargin = PaddingValues(10.dp, 4.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    val prefs = remember { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
                    var themeMode by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_THEME_MODE, Constants.DEFAULT_THEME_MODE)) }
                    val themeOptions = listOf("跟随系统", "浅色", "深色", "跟随系统（莫奈）", "浅色（莫奈）", "深色（莫奈）")

                    WindowDropdownPreference(
                        title = "主题颜色",
                        items = themeOptions,
                        selectedIndex = themeMode,
                        onSelectedIndexChange = {
                            themeMode = it
                            prefs.edit { putInt(Constants.KEY_THEME_MODE, it) }
                        }
                    )

                    if (themeMode >= 3) {
                        var monetColorIndex by remember { mutableIntStateOf(prefs.getInt(Constants.KEY_MONET_COLOR, Constants.DEFAULT_MONET_COLOR)) }
                        val monetOptions = listOf("默认", "蓝色", "绿色", "红色", "黄色", "橙色", "紫色", "粉色")

                        WindowDropdownPreference(
                            title = "强调色",
                            items = monetOptions,
                            selectedIndex = monetColorIndex,
                            onSelectedIndexChange = {
                                monetColorIndex = it
                                prefs.edit { putInt(Constants.KEY_MONET_COLOR, it) }
                            }
                        )
                    }

                    var predictiveBackGestureEnabled by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_PREDICTIVE_BACK_GESTURE, Constants.DEFAULT_PREDICTIVE_BACK_GESTURE)) }
                    val activity = androidx.activity.compose.LocalActivity.current
                    SwitchPreference(
                        title = "预测性返回手势",
                        checked = predictiveBackGestureEnabled,
                        onCheckedChange = {
                            predictiveBackGestureEnabled = it
                            prefs.edit { putBoolean(Constants.KEY_PREDICTIVE_BACK_GESTURE, it) }
                            runCatching {
                                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
                                val applicationInfoClass = android.content.pm.ApplicationInfo::class.java
                                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                                method.isAccessible = true
                                method.invoke(context.applicationInfo, it)
                            }
                            activity?.recreate()
                        }
                    )

                    var floatingNavBarEnabled by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_FLOATING_NAV_BAR, Constants.DEFAULT_FLOATING_NAV_BAR)) }
                    SwitchPreference(
                        title = "悬浮底栏",
                        checked = floatingNavBarEnabled,
                        onCheckedChange = {
                            floatingNavBarEnabled = it
                            prefs.edit { putBoolean(Constants.KEY_FLOATING_NAV_BAR, it) }
                        }
                    )

                    var excludeFromRecents by remember { mutableStateOf(prefs.getBoolean(Constants.KEY_EXCLUDE_FROM_RECENTS, Constants.DEFAULT_EXCLUDE_FROM_RECENTS)) }
                    SwitchPreference(
                        title = "隐藏后台卡片",
                        checked = excludeFromRecents,
                        onCheckedChange = {
                            excludeFromRecents = it
                            prefs.edit { putBoolean(Constants.KEY_EXCLUDE_FROM_RECENTS, it) }
                            setExcludeFromRecents(context, it)
                        }
                    )
                }
            }

            item {
                SmallTitle(
                    text = "配置管理",
                    insideMargin = PaddingValues(10.dp, 4.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ArrowPreference(
                            title = "备份",
                            onClick = {
                                val dateTime = LocalDateTime.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                                backupLauncher.launch("hyperlyric_backup_$dateTime.json")
                            }
                        )
                        ArrowPreference(
                            title = "恢复",
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }
                }

                SmallTitle(
                    text = "调试信息",
                    insideMargin = PaddingValues(10.dp, 4.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "查看模块日志",
                        onClick = {
                            navigator.navigate(Route.Log)
                        }
                    )
                }
            }
        }
    }
}
