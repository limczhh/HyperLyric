package com.lidesheng.hyperlyric.root.utils

object Constants {
    // ================= HOOK & SUPER ISLAND KEYS =================
    const val KEY_HOOK_ENABLE_SUPER_ISLAND = "key_hook_enable_super_island"
    const val KEY_HOOK_ENABLE_DYNAMIC_ISLAND = "key_hook_enable_dynamic_island"
    const val KEY_HOOK_LYRIC_MODE = "key_hook_lyric_mode"

    const val KEY_HOOK_ISLAND_LEFT_ALBUM = "key_hook_island_left_album"
    const val KEY_HOOK_ISLAND_CONTENT_LEFT = "key_hook_island_content_left"
    const val KEY_HOOK_ISLAND_CONTENT_RIGHT = "key_hook_island_content_right"
    const val KEY_HOOK_ISLAND_LEFT_PADDING_LEFT = "key_hook_island_left_padding_left"
    const val KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT = "key_hook_island_left_padding_right"
    const val KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT = "key_hook_island_right_padding_left"
    const val KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT = "key_hook_island_right_padding_right"
    const val KEY_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH = "key_hook_island_left_content_max_width"
    const val KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH = "key_hook_island_right_content_max_width"
    const val KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE = "key_hook_island_behavior_after_pause"
    
    const val KEY_HOOK_MAX_LEFT_WIDTH = "key_hook_max_left_width"
    const val KEY_HOOK_REMOVE_FOCUS_WHITELIST = "key_hook_remove_focus_whitelist"
    const val KEY_HOOK_REMOVE_ISLAND_WHITELIST = "key_hook_remove_island_whitelist"

    // ================= DATA & WHITELIST KEYS =================
    const val KEY_HOOK_WHITELIST = "key_hook_whitelist_packages"
    const val KEY_HOOK_ADDED_LIST = "key_hook_added_packages"

    // ================= STYLE & TYPOGRAPHY KEYS =================
    const val KEY_HOOK_TEXT_SIZE = "key_hook_text_size"
    const val KEY_HOOK_TEXT_SIZE_RATIO = "key_hook_text_size_ratio"
    const val KEY_HOOK_FONT_WEIGHT = "key_hook_font_weight"
    const val KEY_HOOK_FONT_BOLD = "key_hook_font_bold"
    const val KEY_HOOK_FONT_ITALIC = "key_hook_font_italic"
    const val KEY_HOOK_FADING_EDGE_LENGTH = "key_hook_fading_edge_length"
    const val KEY_HOOK_GRADIENT_PROGRESS = "key_hook_gradient_progress"
    const val KEY_HOOK_ISLAND_RIGHT_ICON = "key_hook_island_right_icon"

    // ================= ANIMATION & MARQUEE KEYS =================
    const val KEY_HOOK_ANIM_MODE = "key_hook_anim_mode"
    const val KEY_HOOK_ANIM_ENABLE = "key_hook_anim_enable"
    const val KEY_HOOK_ANIM_ID = "key_hook_anim_id"
    const val KEY_HOOK_MARQUEE_MODE = "key_hook_marquee_mode"
    const val KEY_HOOK_MARQUEE_SPEED = "key_hook_marquee_speed"
    const val KEY_HOOK_MARQUEE_DELAY = "key_hook_marquee_delay"
    const val KEY_HOOK_MARQUEE_LOOP_DELAY = "key_hook_marquee_loop_delay"
    const val KEY_HOOK_MARQUEE_INFINITE = "key_hook_marquee_infinite"
    const val KEY_HOOK_MARQUEE_STOP_END = "key_hook_marquee_stop_end"

    // ================= SYLLABLE KEYS =================
    const val KEY_HOOK_SYLLABLE_RELATIVE = "key_hook_syllable_relative"
    const val KEY_HOOK_SYLLABLE_HIGHLIGHT = "key_hook_syllable_highlight"

    // ================= DEFAULTS =================
    const val DEFAULT_HOOK_LYRIC_MODE = 0
    const val DEFAULT_HOOK_ENABLE_SUPER_ISLAND = false
    const val DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND = false
    const val DEFAULT_HOOK_ISLAND_LEFT_ALBUM = true
    const val DEFAULT_HOOK_MAX_LEFT_WIDTH = 100
    const val DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST = false
    const val DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST = false
    const val DEFAULT_HOOK_ISLAND_CONTENT_LEFT = 6
    const val DEFAULT_HOOK_ISLAND_CONTENT_RIGHT = 8
    const val DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT = 5
    const val DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT = 0
    const val DEFAULT_HOOK_ISLAND_LEFT_CONTENT_MAX_WIDTH = 80
    const val DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH = 100
    const val DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE = 0


    const val DEFAULT_HOOK_TEXT_SIZE = 12
    const val DEFAULT_HOOK_TEXT_SIZE_RATIO = 0.7f
    const val DEFAULT_HOOK_FONT_WEIGHT = 600
    const val DEFAULT_HOOK_FONT_BOLD = false
    const val DEFAULT_HOOK_FONT_ITALIC = false
    const val DEFAULT_HOOK_FADING_EDGE_LENGTH = 15
    const val DEFAULT_HOOK_GRADIENT_PROGRESS = true
    const val DEFAULT_HOOK_ISLAND_RIGHT_ICON = false

    const val DEFAULT_HOOK_ANIM_ENABLE = false
    const val DEFAULT_HOOK_ANIM_ID = "yoyo_default"
    const val DEFAULT_HOOK_MARQUEE_MODE = true
    const val DEFAULT_HOOK_MARQUEE_SPEED = 100
    const val DEFAULT_HOOK_MARQUEE_DELAY = 1500
    const val DEFAULT_HOOK_MARQUEE_LOOP_DELAY = 700
    const val DEFAULT_HOOK_MARQUEE_INFINITE = false
    const val DEFAULT_HOOK_MARQUEE_STOP_END = true

    const val DEFAULT_HOOK_SYLLABLE_RELATIVE = true
    const val DEFAULT_HOOK_SYLLABLE_HIGHLIGHT = false
}
