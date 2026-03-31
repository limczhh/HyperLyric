package com.lidesheng.hyperlyric

object Constants {
    const val PREF_NAME = "com.lidesheng.hyperlyric_preferences"

    const val KEY_TEXT_SIZE = "key_text_size"
    const val KEY_MARQUEE_MODE = "key_marquee_mode"
    const val KEY_HIDE_NOTCH = "key_hide_notch"
    const val KEY_MAX_LEFT_WIDTH = "key_max_left_width"
    const val KEY_MARQUEE_SPEED = "marquee_speed"
    const val KEY_MARQUEE_DELAY = "marquee_delay"
    const val KEY_ANIM_MODE = "key_anim_mode"
    const val KEY_ANIM_ENABLE = "key_anim_enable"
    const val KEY_ANIM_ID = "key_anim_id"
    const val KEY_WHITELIST = "key_whitelist_packages"
    const val KEY_PERSISTENT_FOREGROUND = "key_persistent_foreground"
    const val KEY_ONLINE_LYRIC_ENABLED = "key_online_lyric_enabled"
    const val KEY_ONLINE_LYRIC_CACHE_LIMIT = "key_online_lyric_cache_limit"
    const val KEY_THEME_MODE = "key_theme_mode"
    const val KEY_SEND_NORMAL_NOTIFICATION = "key_send_normal_notification"
    const val KEY_SEND_FOCUS_NOTIFICATION = "key_send_focus_notification"
    const val KEY_ISLAND_LEFT_ALBUM = "key_island_left_album"
    const val KEY_DISABLE_LYRIC_SPLIT = "key_disable_lyric_split"
    const val KEY_MONET_COLOR = "key_monet_color"
    const val KEY_FLOATING_NAV_BAR = "key_floating_nav_bar"
    const val KEY_SETUP_COMPLETED = "key_setup_completed"
    const val KEY_WORK_MODE = "key_work_mode"
    const val KEY_EXCLUDE_FROM_RECENTS = "key_exclude_from_recents"
    const val KEY_PAUSE_LISTENING = "key_pause_listening"
    const val KEY_NOTIFICATION_CLICK_ACTION = "key_notification_click_action"
    const val KEY_PROGRESS_COLOR_ENABLED = "key_progress_color_enabled"
    const val KEY_FOCUS_NOTIFICATION_TYPE = "key_focus_notification_type"
    const val KEY_REMOVE_FOCUS_WHITELIST = "remove_focus_whitelist"
    const val KEY_REMOVE_ISLAND_WHITELIST = "remove_island_whitelist"
    const val KEY_NORMAL_NOTIFICATION_ALBUM = "key_normal_notification_album"


    const val DEFAULT_TEXT_SIZE = 13
    const val DEFAULT_MARQUEE = true
    const val DEFAULT_HIDE_NOTCH = false
    const val DEFAULT_MAX_LEFT_WIDTH = 100
    const val DEFAULT_MARQUEE_SPEED = 100
    const val DEFAULT_MARQUEE_DELAY = 1500
    const val DEFAULT_ANIM_MODE = 0
    const val DEFAULT_ANIM_ENABLE = false
    const val DEFAULT_ANIM_ID = "yoyo_default"
    const val DEFAULT_ONLINE_LYRIC_ENABLED = false
    const val DEFAULT_ONLINE_LYRIC_CACHE_LIMIT = 200
    const val DEFAULT_THEME_MODE = 0
    const val DEFAULT_SEND_NORMAL_NOTIFICATION = true
    const val DEFAULT_SEND_FOCUS_NOTIFICATION = true
    const val DEFAULT_ISLAND_LEFT_ALBUM = false
    const val DEFAULT_DISABLE_LYRIC_SPLIT = false
    const val DEFAULT_MONET_COLOR = 0
    const val DEFAULT_FLOATING_NAV_BAR = false
    const val DEFAULT_SETUP_COMPLETED = false
    const val DEFAULT_WORK_MODE = 1 // 0: Hook, 1: No-Root
    const val DEFAULT_EXCLUDE_FROM_RECENTS = false
    const val DEFAULT_PAUSE_LISTENING = false
    const val DEFAULT_NOTIFICATION_CLICK_ACTION = 0
    const val DEFAULT_PROGRESS_COLOR_ENABLED = true
    const val DEFAULT_FOCUS_NOTIFICATION_TYPE = 0 // 0: OS3样式, 1: 兼容OS2
    const val DEFAULT_REMOVE_FOCUS_WHITELIST = false
    const val DEFAULT_REMOVE_ISLAND_WHITELIST = false
    const val DEFAULT_NORMAL_NOTIFICATION_ALBUM = false

    // 歌词显示 - 新增
    const val KEY_FONT_WEIGHT = "key_font_weight"
    const val KEY_FONT_BOLD = "key_font_bold"
    const val KEY_FONT_ITALIC = "key_font_italic"
    const val KEY_FADING_EDGE_LENGTH = "key_fading_edge_length"
    const val KEY_GRADIENT_PROGRESS = "key_gradient_progress"

    // 特殊功能 - 新增
    const val KEY_MARQUEE_LOOP_DELAY = "key_marquee_loop_delay"
    const val KEY_MARQUEE_INFINITE = "key_marquee_infinite"
    const val KEY_MARQUEE_STOP_END = "key_marquee_stop_end"

    const val KEY_SYLLABLE_RELATIVE = "key_syllable_relative"
    const val KEY_SYLLABLE_HIGHLIGHT = "key_syllable_highlight"
    const val KEY_TEXT_SIZE_RATIO = "key_text_size_ratio"


    // 新增默认值
    const val DEFAULT_FONT_WEIGHT = 400
    const val DEFAULT_FONT_BOLD = false
    const val DEFAULT_FONT_ITALIC = false
    const val DEFAULT_FADING_EDGE_LENGTH = 10
    const val DEFAULT_GRADIENT_PROGRESS = true

    const val DEFAULT_MARQUEE_MODE = true
    const val DEFAULT_MARQUEE_LOOP_DELAY = 700
    const val DEFAULT_MARQUEE_INFINITE = true
    const val DEFAULT_MARQUEE_STOP_END = false
    const val DEFAULT_TEXT_SIZE_RATIO = 0.86f

    const val DEFAULT_SYLLABLE_RELATIVE = false
    const val DEFAULT_SYLLABLE_HIGHLIGHT = true

}
