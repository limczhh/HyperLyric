package com.lidesheng.hyperlyric.common

object UIConstants {
    const val PREF_NAME = "com.lidesheng.hyperlyric_preferences"

    // ================= APP CORE KEYS =================
    const val KEY_WORK_MODE = "key_work_mode"
    const val KEY_SETUP_COMPLETED = "key_setup_completed"
    const val KEY_THEME_MODE = "key_theme_mode"
    const val KEY_MONET_COLOR = "key_monet_color"
    const val KEY_PREDICTIVE_BACK_GESTURE = "key_predictive_back_gesture"
    const val KEY_FLOATING_NAV_BAR = "key_floating_nav_bar"
    const val KEY_EXCLUDE_FROM_RECENTS = "key_exclude_from_recents"
    const val KEY_LOG_LEVEL = "key_log_level"
    const val KEY_LAST_SEEN_VERSION = "key_last_seen_version"

    // ================= DEFAULTS =================
    const val DEFAULT_WORK_MODE = 0
    const val DEFAULT_SETUP_COMPLETED = false
    const val DEFAULT_THEME_MODE = 0
    const val DEFAULT_MONET_COLOR = 0
    const val DEFAULT_PREDICTIVE_BACK_GESTURE = false
    const val DEFAULT_FLOATING_NAV_BAR = false
    const val DEFAULT_EXCLUDE_FROM_RECENTS = false
    const val DEFAULT_LOG_LEVEL = 0 // 0=一般日志(I+W+E), 1=调试日志(D+I+W+E)
}
