package com.lidesheng.hyperlyric.ui.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lidesheng.hyperlyric.common.UIConstants
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalAppDarkTheme = staticCompositionLocalOf { false }
val LocalGlassStyle = staticCompositionLocalOf { false }
val LocalUiStyle = staticCompositionLocalOf { UIConstants.DEFAULT_UI_STYLE }

object ThemeUtils {

    @Composable
    fun MiuixThemeWrapper(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val prefs =
            remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }

        var themeMode by remember {
            mutableIntStateOf(
                prefs.getInt(
                    UIConstants.KEY_THEME_MODE,
                    UIConstants.DEFAULT_THEME_MODE
                )
            )
        }
        var monetColorIndex by remember {
            mutableIntStateOf(
                prefs.getInt(
                    UIConstants.KEY_MONET_COLOR,
                    UIConstants.DEFAULT_MONET_COLOR
                )
            )
        }

        var uiStyle by remember {
            mutableIntStateOf(prefs.getInt(UIConstants.KEY_UI_STYLE, UIConstants.DEFAULT_UI_STYLE))
        }
        val systemIsOs4 = remember {
            val version = runCatching {
                Runtime.getRuntime().exec("getprop ro.build.version.incremental")
                    .inputStream.bufferedReader().use { it.readLine() }
            }.getOrNull() ?: android.os.Build.DISPLAY
            version.startsWith("OS4", ignoreCase = true)
        }
        val listener = remember {
            SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == UIConstants.KEY_UI_STYLE) {
                    uiStyle = p.getInt(UIConstants.KEY_UI_STYLE, UIConstants.DEFAULT_UI_STYLE)
                }
                if (key == UIConstants.KEY_THEME_MODE) {
                    themeMode = p.getInt(UIConstants.KEY_THEME_MODE, UIConstants.DEFAULT_THEME_MODE)
                }
                if (key == UIConstants.KEY_MONET_COLOR) {
                    monetColorIndex =
                        p.getInt(UIConstants.KEY_MONET_COLOR, UIConstants.DEFAULT_MONET_COLOR)
                }
            }
        }

        DisposableEffect(prefs) {
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        val themeController = remember(themeMode, monetColorIndex) {
            val mode = when (themeMode) {
                0 -> ColorSchemeMode.System
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                3 -> ColorSchemeMode.MonetSystem
                4 -> ColorSchemeMode.MonetLight
                5 -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.System
            }
            val keyColor = when (monetColorIndex) {
                0 -> null // 默认
                1 -> androidx.compose.ui.graphics.Color(0xFF2196F3) // 蓝色
                2 -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // 绿色
                3 -> androidx.compose.ui.graphics.Color(0xFFF44336) // 红色
                4 -> androidx.compose.ui.graphics.Color(0xFFFFEB3B) // 黄色
                5 -> androidx.compose.ui.graphics.Color(0xFFFF9800) // 橙色
                6 -> androidx.compose.ui.graphics.Color(0xFF9C27B0) // 紫色
                7 -> androidx.compose.ui.graphics.Color(0xFFE91E63) // 粉色
                else -> null
            }
            ThemeController(colorSchemeMode = mode, keyColor = keyColor)
        }

        val view = LocalView.current
        val isSystemDark = isSystemInDarkTheme()

        val isDark = when (themeMode) {
            1, 4 -> false
            2, 5 -> true
            else -> isSystemDark
        }

        LaunchedEffect(isDark, view) {
            if (!view.isInEditMode) {
                var currentContext = context
                while (currentContext is android.content.ContextWrapper) {
                    if (currentContext is Activity) break
                    currentContext = currentContext.baseContext
                }

                val window = (currentContext as? Activity)?.window
                if (window != null) {
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }
        }

        MiuixTheme(controller = themeController) {
            val glassStyle = when (uiStyle) {
                UIConstants.UI_STYLE_OS4 -> true
                UIConstants.UI_STYLE_OS3 -> false
                else -> systemIsOs4
            }
            CompositionLocalProvider(
                LocalAppDarkTheme provides isDark,
                LocalGlassStyle provides glassStyle,
                LocalUiStyle provides uiStyle.coerceIn(UIConstants.UI_STYLE_FOLLOW_SYSTEM, UIConstants.UI_STYLE_OS4),
            ) { content() }
        }
    }
}
