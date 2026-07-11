package com.lidesheng.hyperlyric.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.ui.navigation.AppNavigation
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.ThemeUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        com.lidesheng.hyperlyric.ui.utils.AppUtils.initPredictiveBackGesture(application)
        com.lidesheng.hyperlyric.ui.utils.LocaleUtils.applyStoredLanguage(this)
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences(UIConstants.PREF_NAME, MODE_PRIVATE)
        val setupCompleted = prefs.getBoolean(UIConstants.KEY_SETUP_COMPLETED, UIConstants.DEFAULT_SETUP_COMPLETED)
        
        val excludeFromRecents = prefs.getBoolean(UIConstants.KEY_EXCLUDE_FROM_RECENTS, UIConstants.DEFAULT_EXCLUDE_FROM_RECENTS)
        if (excludeFromRecents) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                am.appTasks?.forEach { it.setExcludeFromRecents(true) }
            } catch (_: Exception) { }
        }

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                AppNavigation(startRoute = if (setupCompleted) Route.Main else Route.Setup)
            }
        }
    }
}
