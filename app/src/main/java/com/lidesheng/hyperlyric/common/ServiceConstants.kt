package com.lidesheng.hyperlyric.common

object ServiceConstants {
    // ================= NOTIFICATION KEYS =================
    const val KEY_NOTIFICATION_WHITELIST = "key_notification_whitelist_packages"
    const val KEY_NOTIFICATION_TYPE = "key_notification_type"
    const val KEY_NOTIFICATION_FOCUS_STYLE = "key_focus_notification_type"
    const val KEY_ISLAND_LEFT_ICON = "key_island_left_icon"
    const val KEY_ISLAND_LEFT_ICON_NORMAL = "key_island_left_icon_normal"
    const val KEY_ISLAND_LEFT_ICON_FOCUS = "key_island_left_icon_focus"
    const val KEY_NOTIFICATION_TITLE_STYLE = "key_normal_notification_title_style"
    const val KEY_NOTIFICATION_CLICK_ACTION = "key_notification_click_action"

    // Decoupled Island-like keys for Notification Page
    const val KEY_NOTIFICATION_SHOW_PROGRESS = "key_notification_show_progress"
    const val KEY_NOTIFICATION_PROGRESS_COLOR = "key_notification_progress_color"
    const val KEY_NOTIFICATION_ALBUM = "key_notification_album"
    const val KEY_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT = "key_notification_island_disable_lyric_split"
    const val KEY_NOTIFICATION_ISLAND_LIMIT_WIDTH = "key_notification_island_limit_width"
    const val KEY_NOTIFICATION_ISLAND_MAX_WIDTH = "key_notification_island_max_width"
    const val KEY_NOTIFICATION_HIGHLIGHT_COLOR = "key_notification_island_highlight_color"
    const val KEY_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR = "key_notification_song_info_highlight_color"
    const val KEY_BYPASS_FOCUS_NOTIFICATION_LIMIT = "key_bypass_focus_notification_limit"
    const val KEY_SERVICE_LYRIC_SOURCE = "key_service_lyric_source"
    const val KEY_NOTIFICATION_FOCUS_SHOW = "key_notification_focus_show"

    // ================= LYRIC SOURCE TYPES =================
    const val LYRIC_SOURCE_AUTO = 0
    const val LYRIC_SOURCE_ONLINE = 1
    const val LYRIC_SOURCE_LYRIC_INFO = 2
    const val LYRIC_SOURCE_LRC = 3
    const val LYRIC_SOURCE_TITLE = 4

    // ================= DEFAULTS =================
    const val DEFAULT_NOTIFICATION_TYPE = 0
    const val DEFAULT_NOTIFICATION_FOCUS_STYLE = 0
    const val DEFAULT_NOTIFICATION_FOCUS_SHOW = true
    const val DEFAULT_ISLAND_LEFT_ICON = 0 // 0=music note, 1=rounded album, 2=circular album, 3=none
    const val DEFAULT_NOTIFICATION_TITLE_STYLE = 4
    const val DEFAULT_NOTIFICATION_CLICK_ACTION = 0

    const val DEFAULT_NOTIFICATION_SHOW_PROGRESS = true
    const val DEFAULT_NOTIFICATION_PROGRESS_COLOR = true
    const val DEFAULT_NOTIFICATION_ALBUM = true
    const val DEFAULT_NOTIFICATION_ISLAND_DISABLE_LYRIC_SPLIT = false
    const val DEFAULT_NOTIFICATION_ISLAND_LIMIT_WIDTH = false
    const val DEFAULT_NOTIFICATION_ISLAND_MAX_WIDTH = 720
    const val DEFAULT_NOTIFICATION_HIGHLIGHT_COLOR = false
    const val DEFAULT_NOTIFICATION_SONG_INFO_HIGHLIGHT_COLOR = false
    const val DEFAULT_BYPASS_FOCUS_NOTIFICATION_LIMIT = false
    const val DEFAULT_SERVICE_LYRIC_SOURCE = 0
}
