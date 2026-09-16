package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.UIConstants

@Composable
internal fun rememberLyricEnhancementPrefs(): SharedPreferences {
    val context = LocalContext.current
    return remember(context) {
        context.getSharedPreferences(
            UIConstants.PREF_NAME,
            Context.MODE_PRIVATE
        )
    }
}

@Composable
internal fun rememberLyricEnhancementConfigSaver(
    prefs: SharedPreferences
): (String, Any) -> Unit {
    return remember(prefs) {
        { key: String, value: Any ->
            when (value) {
                is Int -> PrefsBridge.putInt(key, value)
                is Boolean -> PrefsBridge.putBoolean(key, value)
                is Float -> PrefsBridge.putFloat(key, value)
                is Long -> PrefsBridge.putLong(key, value)
                is String -> PrefsBridge.putString(key, value)
                is Set<*> -> PrefsBridge.putStringSet(
                    key,
                    value.filterIsInstance<String>().toSet()
                )
            }
        }
    }
}
