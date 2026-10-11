package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

/**
 * 容错读取 float 偏好。
 *
 * SharedPreferencesImpl.getFloat 在键的存储类型不符(如被系统换机迁移、第三方备份恢复工具或
 * 手动改写成 Int/Long/String)时会直接抛出 ClassCastException 导致页面闪退;这里按
 * Number/String 逐级兼容取值,并在发现脏数据时回写为 Float 完成自愈。
 */
fun getFloatCompat(prefs: SharedPreferences, key: String, defValue: Float): Float {
    if (!prefs.contains(key)) return defValue
    return when (val raw = prefs.all[key]) {
        null -> defValue
        is Float -> raw
        is Number -> raw.toFloat().also { normalized ->
            runCatching { prefs.edit().putFloat(key, normalized).apply() }
        }
        is String -> raw.toFloatOrNull() ?: defValue
        else -> defValue
    }
}
