package com.lidesheng.hyperlyric.service

object Constants {
    // ================= NOTIFICATION KEYS =================
    const val KEY_NOTIFICATION_WHITELIST = "key_notification_whitelist_packages"
    const val KEY_NOTIFICATION_TYPE = "key_notification_type"
    const val KEY_NOTIFICATION_FOCUS_STYLE = "key_focus_notification_type"
    const val KEY_NOTIFICATION_LIVE_ALBUM = "key_notification_live_album"
    const val KEY_NOTIFICATION_TITLE_STYLE = "key_normal_notification_title_style"
    const val KEY_NOTIFICATION_CLICK_ACTION = "key_notification_click_action"
    
    // Decoupled Island-like keys for Notification Page
    const val KEY_NOTIFICATION_ISLAND_LEFT_ALBUM = "key_notification_island_left_album"
    const val KEY_NOTIFICATION_SHOW_PROGRESS = "key_notification_show_progress"
    const val KEY_NOTIFICATION_PROGRESS_COLOR = "key_notification_progress_color"
    const val KEY_NOTIFICATION_ALBUM = "key_notification_album"
    const val KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT = "key_notification_island_disable_lyric_split"
    const val KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH = "key_notification_island_limit_width"
    const val KEY_NOTIFICATION_ISLAND_MAX_WIDTH = "key_notification_island_max_width"
    const val KEY_ONLINE_LYRIC_CACHE_LIMIT = "key_online_lyric_cache_limit"
    const val KEY_ONLINE_LYRIC_ENABLED = "key_online_lyric_enabled"

    // ================= DEFAULTS =================
    const val DEFAULT_NOTIFICATION_TYPE = 0
    const val DEFAULT_NOTIFICATION_FOCUS_STYLE = 0
    const val DEFAULT_NOTIFICATION_LIVE_ALBUM = false
    const val DEFAULT_NOTIFICATION_TITLE_STYLE = 4
    const val DEFAULT_NOTIFICATION_CLICK_ACTION = 0
    
    const val DEFAULT_NOTIFICATION_ISLAND_LEFT_ALBUM = false
    const val DEFAULT_NOTIFICATION_SHOW_PROGRESS = true
    const val DEFAULT_NOTIFICATION_PROGRESS_COLOR = true
    const val DEFAULT_NOTIFICATION_ALBUM = true
    const val DEFAULT_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT = false
    const val DEFAULT_NOTIFICATION_ISLAND_LIMIT_WIDTH = false
    const val DEFAULT_NOTIFICATION_ISLAND_MAX_WIDTH = 720
    const val DEFAULT_ONLINE_LYRIC_CACHE_LIMIT = 200
    const val DEFAULT_ONLINE_LYRIC_ENABLED = false
}
