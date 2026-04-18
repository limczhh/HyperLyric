package com.lidesheng.hyperlyric.ui.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.lidesheng.hyperlyric.Constants
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

object ThemeUtils {

    @Composable
    fun MiuixThemeWrapper(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE) }
        
        var themeMode by remember { 
            mutableIntStateOf(prefs.getInt(Constants.KEY_THEME_MODE, Constants.DEFAULT_THEME_MODE)) 
        }
        var monetColorIndex by remember { 
            mutableIntStateOf(prefs.getInt(Constants.KEY_MONET_COLOR, Constants.DEFAULT_MONET_COLOR)) 
        }

        val listener = remember { 
            SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == Constants.KEY_THEME_MODE) {
                    themeMode = p.getInt(Constants.KEY_THEME_MODE, Constants.DEFAULT_THEME_MODE)
                }
                if (key == Constants.KEY_MONET_COLOR) {
                    monetColorIndex = p.getInt(Constants.KEY_MONET_COLOR, Constants.DEFAULT_MONET_COLOR)
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
            content()
        }
    }
}
