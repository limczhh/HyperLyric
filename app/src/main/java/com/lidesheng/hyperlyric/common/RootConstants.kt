package com.lidesheng.hyperlyric.common

object RootConstants {
    // ================= HOOK & SUPER ISLAND KEYS =================
    const val KEY_HOOK_ENABLE_SUPER_ISLAND = "key_hook_enable_super_island"
    const val KEY_HOOK_ENABLE_DYNAMIC_ISLAND = "key_hook_enable_dynamic_island"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ENABLED = "key_hook_status_bar_lyric_enabled"
    const val KEY_HOOK_STATUS_BAR_LYRIC_CONFIG_INITIALIZED =
        "key_hook_status_bar_lyric_config_initialized"
    const val KEY_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER =
        "key_hook_status_bar_lyric_insertion_order"
    const val KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH =
        "key_hook_status_bar_lyric_portrait_dynamic_max_width"
    const val KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH =
        "key_hook_status_bar_lyric_landscape_dynamic_max_width"
    const val KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP =
        "key_hook_status_bar_lyric_padding_left_dp"
    const val KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP =
        "key_hook_status_bar_lyric_padding_right_dp"
    const val KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR =
        "key_hook_status_bar_lyric_clock_hide_behavior"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR =
        "key_hook_status_bar_lyric_island_hide_behavior"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND =
        "key_hook_status_bar_lyric_adjust_width_for_super_island"
    const val KEY_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN =
        "key_hook_status_bar_lyric_hide_on_lock_screen"
    const val KEY_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION =
        "key_hook_status_bar_lyric_double_tap_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION =
        "key_hook_status_bar_lyric_long_press_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION =
        "key_hook_status_bar_lyric_swipe_left_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION =
        "key_hook_status_bar_lyric_swipe_right_action"
    const val KEY_HOOK_LYRIC_MODE = "key_hook_lyric_mode"
    const val KEY_HOOK_LYRIC_SOURCE = "key_hook_lyric_source"
    const val DEFAULT_HOOK_LYRIC_SOURCE = "lyricon"
    const val KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX = "key_hook_lyricon_provider_delay_"


    const val KEY_HOOK_ISLAND_ALBUM_COVER_STYLE = "key_hook_island_album_cover_style"
    const val KEY_HOOK_ISLAND_CONTENT_LEFT = "key_hook_island_content_left"
    const val KEY_HOOK_ISLAND_CONTENT_RIGHT = "key_hook_island_content_right"
    const val KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE =
        "key_hook_island_music_info_first_line"
    const val KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE =
        "key_hook_island_music_info_second_line"
    const val KEY_HOOK_ISLAND_MUSIC_INFO_SEPARATOR =
        "key_hook_island_music_info_separator"
    const val KEY_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS =
        "key_hook_island_music_info_hide_title_alias"
    const val KEY_HOOK_ISLAND_LEFT_PADDING_LEFT = "key_hook_island_left_padding_left"
    const val KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT = "key_hook_island_left_padding_right"
    const val KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT = "key_hook_island_right_padding_left"
    const val KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT = "key_hook_island_right_padding_right"
    const val KEY_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH = "key_hook_island_right_content_max_width"
    const val KEY_HOOK_ISLAND_WIDTH_MODE = "key_hook_island_width_mode"
    const val KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH = "key_hook_island_dynamic_min_width"
    const val KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH = "key_hook_island_dynamic_max_width"
    const val KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS = "key_hook_island_dynamic_width_basis"
    const val KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT = "key_hook_island_disable_width_limit"
    const val KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE = "key_hook_island_behavior_after_pause"
    const val KEY_HOOK_ISLAND_BEHAVIOR_AFTER_NO_LYRICS =
        "key_hook_island_behavior_after_no_lyrics"
    const val KEY_HOOK_ISLAND_LONG_PRESS_BEHAVIOR = "key_hook_island_long_press_behavior"
    const val KEY_HOOK_ISLAND_SWIPE_BEHAVIOR = "key_hook_island_swipe_behavior"
    const val KEY_HOOK_ISLAND_SWIPE_THRESHOLD_DP = "key_hook_island_swipe_threshold_dp"
    const val KEY_HOOK_ISLAND_MODIFICATION_SCOPE = "key_hook_island_modification_scope"
    const val KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE =
        "key_hook_notification_media_ambient_flow_mode"
    const val KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED =
        "key_hook_notification_media_card_switcher_enabled"
    const val KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE =
        "key_hook_notification_media_card_switcher_mode"
    const val KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT =
        "key_hook_notification_media_card_switcher_max_count"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE =
        "key_hook_island_expanded_media_ambient_flow_mode"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE =
        "key_hook_island_expanded_media_layout_style"
    const val KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME =
        "key_hook_notification_media_card_theme"
    const val KEY_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING =
        "key_hook_aod_disable_media_card_collapsing"
    const val KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE =
        "key_hook_notification_media_layout_style"
    const val KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE =
        "key_hook_notification_media_cover_style"
    const val KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE =
        "key_hook_notification_media_hide_cover_source"
    const val KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW =
        "key_hook_notification_media_hide_cover_shadow"
    const val KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH =
        "key_hook_notification_media_hide_device_switch"
    const val KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME =
        "key_hook_notification_media_hide_time"
    const val KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS =
        "key_hook_notification_media_hide_custom_actions"
    const val KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP =
        "key_hook_notification_media_disable_cover_flip"
    const val KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE =
        "key_hook_notification_media_progress_style"
    const val KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW =
        "key_hook_notification_media_progress_head_glow"
    const val KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE =
        "key_hook_notification_media_thumb_style"
    const val KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT =
        "key_hook_notification_media_action_align_left"
    const val KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER =
        "key_hook_notification_media_action_order"
    const val KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE =
        "key_hook_notification_media_background_style"
    const val KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR =
        "key_hook_notification_media_background_blur"
    const val KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION =
        "key_hook_notification_media_background_color_animation"
    const val KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT =
        "key_hook_notification_media_background_auto_invert"
    const val KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE =
        "key_hook_notification_media_soft_cover_tone"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME =
        "key_hook_island_expanded_media_card_theme"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE =
        "key_hook_island_expanded_media_cover_style"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE =
        "key_hook_island_expanded_media_hide_cover_source"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH =
        "key_hook_island_expanded_media_hide_device_switch"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME =
        "key_hook_island_expanded_media_hide_time"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS =
        "key_hook_island_expanded_media_hide_custom_actions"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP =
        "key_hook_island_expanded_media_disable_cover_flip"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE =
        "key_hook_island_expanded_media_progress_style"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW =
        "key_hook_island_expanded_media_progress_head_glow"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE =
        "key_hook_island_expanded_media_thumb_style"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT =
        "key_hook_island_expanded_media_action_align_left"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER =
        "key_hook_island_expanded_media_action_order"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE =
        "key_hook_island_expanded_media_background_style"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR =
        "key_hook_island_expanded_media_background_blur"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION =
        "key_hook_island_expanded_media_background_color_animation"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT =
        "key_hook_island_expanded_media_background_auto_invert"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE =
        "key_hook_island_expanded_media_soft_cover_tone"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_SIZE =
        "key_hook_island_expanded_media_ios_cover_size"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_TOP =
        "key_hook_island_expanded_media_ios_cover_top"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_START =
        "key_hook_island_expanded_media_ios_cover_start"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_TITLE_TOP =
        "key_hook_island_expanded_media_ios_title_top"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_INFO_START_GAP =
        "key_hook_island_expanded_media_ios_info_start_gap"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ARTIST_GAP =
        "key_hook_island_expanded_media_ios_artist_gap"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_SIZE =
        "key_hook_island_expanded_media_ios_music_wave_size"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_TOP =
        "key_hook_island_expanded_media_ios_music_wave_top"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_END =
        "key_hook_island_expanded_media_ios_music_wave_end"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_TOP_GAP =
        "key_hook_island_expanded_media_ios_progress_top_gap"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_HEIGHT =
        "key_hook_island_expanded_media_ios_progress_height"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_TIME_GAP =
        "key_hook_island_expanded_media_ios_progress_time_gap"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_TIME_OUTER_MARGIN =
        "key_hook_island_expanded_media_ios_time_outer_margin"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_WIDTH =
        "key_hook_island_expanded_media_ios_action_width"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_HEIGHT =
        "key_hook_island_expanded_media_ios_action_height"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_BOTTOM =
        "key_hook_island_expanded_media_ios_action_bottom"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_OUTER_MARGIN =
        "key_hook_island_expanded_media_ios_action_outer_margin"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_SCALE_PERCENT =
        "key_hook_island_expanded_media_ios_action_scale_percent"
    const val KEY_HOOK_ISLAND_EXPANDED_MEDIA_IOS_DEVICE_SWITCH_SCALE_PERCENT =
        "key_hook_island_expanded_media_ios_device_switch_scale_percent"

    const val KEY_HOOK_REMOVE_FOCUS_WHITELIST = "key_hook_remove_focus_whitelist"
    const val KEY_HOOK_REMOVE_ISLAND_WHITELIST = "key_hook_remove_island_whitelist"

    // ================= STYLE & TYPOGRAPHY KEYS =================
    const val KEY_HOOK_TEXT_SIZE = "key_hook_text_size"
    const val KEY_HOOK_TEXT_SIZE_RATIO = "key_hook_text_size_ratio"
    const val KEY_HOOK_FONT_WEIGHT = "key_hook_font_weight"
    const val KEY_HOOK_FONT_ITALIC = "key_hook_font_italic"
    const val KEY_HOOK_FADING_EDGE_LENGTH = "key_hook_fading_edge_length"
    const val KEY_HOOK_GRADIENT_PROGRESS = "key_hook_gradient_progress"
    const val KEY_HOOK_LYRIC_ALIGNMENT = "key_hook_lyric_alignment"
    const val KEY_HOOK_MUSIC_INFO_ALIGNMENT = "key_hook_music_info_alignment"
    const val KEY_HOOK_PLACEHOLDER_FORMAT = "key_hook_placeholder_format"
    const val KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE = "key_hook_island_music_wave_style"

    // ================= ANIMATION & MARQUEE KEYS =================
    const val KEY_HOOK_ANIM_ENABLE = "key_hook_anim_enable"
    const val KEY_HOOK_ANIM_ID = "key_hook_anim_id"
    const val KEY_HOOK_ANIM_SPEED_RATE = "key_hook_anim_speed_rate"
    const val KEY_HOOK_MARQUEE_MODE = "key_hook_marquee_mode"
    const val KEY_HOOK_MARQUEE_SPEED = "key_hook_marquee_speed"
    const val KEY_HOOK_MARQUEE_DELAY = "key_hook_marquee_delay"
    const val KEY_HOOK_MARQUEE_LOOP_DELAY = "key_hook_marquee_loop_delay"
    const val KEY_HOOK_MARQUEE_INFINITE = "key_hook_marquee_infinite"
    const val KEY_HOOK_MARQUEE_STOP_END = "key_hook_marquee_stop_end"
    const val KEY_HOOK_MARQUEE_METADATA_SPEED = "key_hook_marquee_metadata_speed"
    const val KEY_HOOK_MARQUEE_METADATA_MODE = "key_hook_marquee_metadata_mode"
    const val KEY_HOOK_MARQUEE_METADATA_DELAY = "key_hook_marquee_metadata_delay"
    const val KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY = "key_hook_marquee_metadata_loop_delay"
    const val KEY_HOOK_MARQUEE_METADATA_INFINITE = "key_hook_marquee_metadata_infinite"

    // ================= SYLLABLE KEYS =================
    const val KEY_HOOK_SYLLABLE_RELATIVE = "key_hook_syllable_relative"
    const val KEY_HOOK_SYLLABLE_HIGHLIGHT = "key_hook_syllable_highlight"
    const val KEY_HOOK_SYLLABLE_LINE_DISPLAY = "key_hook_syllable_line_display"

    // ================= LYRIC CONTENT KEYS =================
    const val KEY_HOOK_ONLY_SECONDARY = "key_hook_only_secondary"
    const val KEY_HOOK_SWAP_SECONDARY = "key_hook_swap_secondary"
    const val KEY_HOOK_LYRIC_SHOW_TRANSLATION = "key_hook_lyric_show_translation"
    const val KEY_HOOK_LYRIC_SHOW_ROMA = "key_hook_lyric_show_roma"
    const val KEY_HOOK_LYRIC_SHOW_NEXT_LINE = "key_hook_lyric_show_next_line"
    const val KEY_HOOK_LYRIC_SHOW_BACKGROUND_VOCAL = "key_hook_lyric_show_background_vocal"
    const val KEY_HOOK_LYRIC_SHOW_OVERLAPPING_LINE = "key_hook_lyric_show_overlapping_line"
    const val KEY_HOOK_LYRIC_SECONDARY_ORDER = "key_hook_lyric_secondary_order"
    const val KEY_HOOK_LYRIC_AUTO_DUET = "key_hook_lyric_auto_duet"

    // ================= LYRIC ENHANCEMENT KEYS =================
    const val KEY_HOOK_AI_TRANS_ENABLE = "key_hook_ai_trans_enable"
    const val KEY_HOOK_AI_TRANS_SKIP_LANGUAGES = "key_hook_ai_trans_skip_languages"
    const val KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION =
        "key_hook_ai_trans_skip_existing_translation"
    const val KEY_HOOK_AI_TRANS_FORCE_OVERRIDE = "key_hook_ai_trans_force_override"
    const val KEY_HOOK_AI_TRANS_PROVIDER = "key_hook_ai_trans_provider"
    const val KEY_HOOK_AI_TRANS_API_KEY = "key_hook_ai_trans_api_key"
    const val KEY_HOOK_AI_TRANS_MODEL = "key_hook_ai_trans_model"
    const val KEY_HOOK_AI_TRANS_BASE_URL = "key_hook_ai_trans_base_url"
    const val KEY_HOOK_AI_TRANS_TARGET_LANG = "key_hook_ai_trans_target_lang"
    const val KEY_HOOK_AI_TRANS_PROMPT = "key_hook_ai_trans_prompt"
    const val KEY_HOOK_AI_TRANS_TEMPERATURE = "key_hook_ai_trans_temperature"
    const val KEY_HOOK_AI_TRANS_TOP_P = "key_hook_ai_trans_top_p"
    const val KEY_HOOK_AI_TRANS_MAX_TOKENS = "key_hook_ai_trans_max_tokens"
    const val KEY_HOOK_AMLL_TTML_ENABLE = "key_hook_amll_ttml_enable"
    const val KEY_HOOK_AMLL_TTML_PLATFORM_PROBE = "key_hook_amll_ttml_platform_probe"
    const val KEY_HOOK_AMLL_TTML_API_BASE_URL = "key_hook_amll_ttml_api_base_url"
    const val KEY_HOOK_AMLL_TTML_DUET_PERFORMANCE = "key_hook_amll_ttml_duet_performance"

    // ================= COLOR KEYS =================
    const val KEY_HOOK_TEXT_COLOR_STYLE = "key_hook_text_color_style"
    const val KEY_HOOK_ISLAND_GLOW_EXTRACT_COLOR = "key_hook_island_glow_extract_color"
    const val KEY_HOOK_ISLAND_PROGRESS_GLOW = "key_hook_island_progress_glow"
    const val KEY_HOOK_ISLAND_PROGRESS_STYLE = "key_hook_island_progress_style"
    const val KEY_HOOK_ISLAND_PROGRESS_GRADIENT = "key_hook_island_progress_gradient"

    // ================= FONT KEYS =================
    const val KEY_HOOK_CUSTOM_FONT_PATH = "key_hook_custom_font_path"
    const val KEY_HOOK_NARROW_LATIN_FONT = "key_hook_narrow_latin_font"

    // ================= WORD MOTION KEYS =================
    const val KEY_HOOK_WORD_MOTION_ENABLED = "key_hook_word_motion_enabled"
    const val KEY_HOOK_WORD_MOTION_CJK_LIFT = "key_hook_word_motion_cjk_lift"
    const val KEY_HOOK_WORD_MOTION_CJK_WAVE = "key_hook_word_motion_cjk_wave"
    const val KEY_HOOK_WORD_MOTION_LATIN_BY_CHARACTER =
        "key_hook_word_motion_latin_by_character"
    const val KEY_HOOK_WORD_MOTION_LATIN_LIFT = "key_hook_word_motion_latin_lift"
    const val KEY_HOOK_WORD_MOTION_LATIN_WAVE = "key_hook_word_motion_latin_wave"

    // ================= DEFAULTS =================
    const val MIN_HOOK_WORD_MOTION_LIFT = 0f
    const val MAX_HOOK_WORD_MOTION_LIFT = 0.20f
    const val MIN_HOOK_WORD_MOTION_WAVE = 0f
    const val MAX_HOOK_WORD_MOTION_WAVE = 2f
    const val DEFAULT_HOOK_LYRIC_MODE = 0
    const val DEFAULT_HOOK_LYRICON_PROVIDER_DELAY = 0
    const val MIN_HOOK_LYRICON_PROVIDER_DELAY = -5000
    const val MAX_HOOK_LYRICON_PROVIDER_DELAY = 5000

    const val DEFAULT_HOOK_ENABLE_SUPER_ISLAND = false
    const val DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND = false
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ENABLED = true
    const val STATUS_BAR_LYRIC_INSERTION_BEFORE_CLOCK = 0
    const val STATUS_BAR_LYRIC_INSERTION_AFTER_CLOCK = 1
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER =
        STATUS_BAR_LYRIC_INSERTION_AFTER_CLOCK
    const val STATUS_BAR_LYRIC_PORTRAIT_MAX_WIDTH_DP = 200
    const val STATUS_BAR_LYRIC_LANDSCAPE_MAX_WIDTH_DP = 400
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH = 150
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH = 200
    const val DEFAULT_STATUS_BAR_LYRIC_TEXT_SIZE_SP = 13
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP = 0f
    const val MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP = -32f
    const val MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP = 32f
    const val STATUS_BAR_LYRIC_CLOCK_HIDE_NONE = 0
    const val STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING = 1
    const val STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR =
        STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING
    const val STATUS_BAR_LYRIC_ISLAND_HIDE_NONE = 0
    const val STATUS_BAR_LYRIC_ISLAND_HIDE_WHILE_PLAYING = 1
    const val STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR =
        STATUS_BAR_LYRIC_ISLAND_HIDE_NONE
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND = true
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN = true
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_NONE = 0
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK = 1
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_CLOCK = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION =
        STATUS_BAR_LYRIC_GESTURE_ACTION_NONE
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION =
        STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE = 0
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS = 1
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION =
        STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION =
        STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_THRESHOLD_DP = 50
    const val ISLAND_ALBUM_COVER_STYLE_DEFAULT = 0
    const val ISLAND_ALBUM_COVER_STYLE_CIRCLE = 1
    const val ISLAND_ALBUM_COVER_STYLE_APP_ICON = 2
    const val ISLAND_ALBUM_COVER_STYLE_ROTATING_CIRCLE = 3
    const val ISLAND_ALBUM_COVER_STYLE_HIDDEN = 4
    const val DEFAULT_HOOK_ISLAND_ALBUM_COVER_STYLE = ISLAND_ALBUM_COVER_STYLE_DEFAULT
    const val DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST = false
    const val DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST = false
    const val ISLAND_CONTENT_MODE_NONE = 0
    const val ISLAND_CONTENT_MODE_LYRIC = 7
    const val ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO = 8
    const val DEFAULT_HOOK_ISLAND_CONTENT_LEFT = ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
    const val DEFAULT_HOOK_ISLAND_CONTENT_RIGHT = ISLAND_CONTENT_MODE_LYRIC
    const val DEFAULT_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE = "title"
    const val DEFAULT_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE = "artist"
    const val DEFAULT_HOOK_ISLAND_MUSIC_INFO_SEPARATOR = "hyphen"
    const val DEFAULT_HOOK_ISLAND_MUSIC_INFO_HIDE_TITLE_ALIAS = false
    const val DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT = 2
    const val DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH = 72
    const val ISLAND_WIDTH_MODE_FIXED = 0
    const val ISLAND_WIDTH_MODE_DYNAMIC = 1
    const val DEFAULT_HOOK_ISLAND_WIDTH_MODE = ISLAND_WIDTH_MODE_FIXED
    const val DEFAULT_HOOK_ISLAND_DYNAMIC_MIN_WIDTH = 22
    const val DEFAULT_HOOK_ISLAND_DYNAMIC_MAX_WIDTH = DEFAULT_HOOK_ISLAND_RIGHT_CONTENT_MAX_WIDTH
    const val ISLAND_DYNAMIC_WIDTH_BASIS_ALL = 0
    const val ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY = 1
    const val DEFAULT_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS = ISLAND_DYNAMIC_WIDTH_BASIS_ALL
    const val DEFAULT_HOOK_ISLAND_DISABLE_WIDTH_LIMIT = false
    const val DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE = 0
    const val ISLAND_NO_LYRICS_BEHAVIOR_DEFAULT = 0
    const val ISLAND_NO_LYRICS_BEHAVIOR_MUSIC_INFO = 1
    const val DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_NO_LYRICS =
        ISLAND_NO_LYRICS_BEHAVIOR_DEFAULT
    const val ISLAND_LONG_PRESS_BEHAVIOR_LYRIC_SHARE = 0
    const val ISLAND_LONG_PRESS_BEHAVIOR_TOGGLE_PLAYBACK = 1
    const val ISLAND_LONG_PRESS_BEHAVIOR_DEFAULT = 2
    const val DEFAULT_HOOK_ISLAND_LONG_PRESS_BEHAVIOR =
        ISLAND_LONG_PRESS_BEHAVIOR_DEFAULT
    const val ISLAND_SWIPE_BEHAVIOR_DEFAULT = 0
    const val ISLAND_SWIPE_BEHAVIOR_TRACK_SWITCH = 1
    const val ISLAND_SWIPE_BEHAVIOR_TRACK_SWITCH_REVERSED = 2
    const val DEFAULT_HOOK_ISLAND_SWIPE_BEHAVIOR = ISLAND_SWIPE_BEHAVIOR_DEFAULT
    const val MIN_HOOK_ISLAND_SWIPE_THRESHOLD_DP = 50
    const val MAX_HOOK_ISLAND_SWIPE_THRESHOLD_DP = 150
    const val STEP_HOOK_ISLAND_SWIPE_THRESHOLD_DP = 5
    const val DEFAULT_HOOK_ISLAND_SWIPE_THRESHOLD_DP = 50
    const val ISLAND_MODIFICATION_SCOPE_ALL_MEDIA = 0
    const val ISLAND_MODIFICATION_SCOPE_INJECTED_LYRIC = 1
    const val DEFAULT_HOOK_ISLAND_MODIFICATION_SCOPE = ISLAND_MODIFICATION_SCOPE_ALL_MEDIA
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED = 0
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC = 1
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR = 2
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL = 3
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE =
        NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED = false
    const val NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_SINGLE = 0
    const val NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI = 1
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE =
        NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI
    const val MIN_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT = 2
    const val MAX_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT = 6
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT = 3
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED = 1
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR = 2
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL = 3
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE =
        ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT
    const val MEDIA_CARD_THEME_FOLLOW_SYSTEM = 0
    const val MEDIA_CARD_THEME_ALWAYS_LIGHT = 1
    const val MEDIA_CARD_THEME_ALWAYS_DARK = 2
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME = MEDIA_CARD_THEME_FOLLOW_SYSTEM
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME = MEDIA_CARD_THEME_ALWAYS_DARK
    const val DEFAULT_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING = false
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM = 0
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS = 1
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS = 2
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI = 3
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI = 4
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL = 5
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE =
        NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_SYSTEM = 0
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_IOS = 1
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS = 2
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI = 3
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_MIUI = 4
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_PIXEL = 5
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE =
        ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_SYSTEM
    const val NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_COVER_STYLE_CIRCLE = 1
    const val NOTIFICATION_MEDIA_COVER_STYLE_ROTATING_CIRCLE = 2
    const val NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN = 3
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE =
        NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_TIME = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP = false
    const val NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE = 1
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE = NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW = false
    const val NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_THUMB_STYLE_VERTICAL = 1
    const val NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN = 2
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE = NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT = false
    const val NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT = 0
    const val NOTIFICATION_MEDIA_ACTION_ORDER_CUSTOM_RIGHT = 1
    const val NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT = 2
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER = NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT
    const val MEDIA_SOFT_COVER_TONE_LIGHT = 0
    const val MEDIA_SOFT_COVER_TONE_DARK = 1
    const val MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM = 2

    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_COVER_ART = 1
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_BLURRED_COVER = 2
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_RADIAL_GRADIENT = 3
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT = 4
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER = 5
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE =
        NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR = 10
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE = MEDIA_SOFT_COVER_TONE_DARK
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_CIRCLE = 1
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_ROTATING_CIRCLE = 2
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN = 3
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE =
        ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP = false
    const val ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE = 1
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE = ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW = true
    const val ISLAND_EXPANDED_MEDIA_THUMB_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_THUMB_STYLE_VERTICAL = 1
    const val ISLAND_EXPANDED_MEDIA_THUMB_STYLE_HIDDEN = 2
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE = ISLAND_EXPANDED_MEDIA_THUMB_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT = false
    const val ISLAND_EXPANDED_MEDIA_ACTION_ORDER_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_ACTION_ORDER_CUSTOM_RIGHT = 1
    const val ISLAND_EXPANDED_MEDIA_ACTION_ORDER_PLAY_LEFT = 2
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER = ISLAND_EXPANDED_MEDIA_ACTION_ORDER_DEFAULT
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_COVER_ART = 1
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_BLURRED_COVER = 2
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_RADIAL_GRADIENT = 3
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT = 4
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_SOFT_COVER = 5
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE =
        ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR = 10
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE = MEDIA_SOFT_COVER_TONE_DARK
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_SIZE = 50f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_TOP = 15f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_START = 15f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_TITLE_TOP = 19f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_INFO_START_GAP = 12f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ARTIST_GAP = 3f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_SIZE = 24f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_TOP = 24f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_END = 24f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_TOP_GAP = 5f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_HEIGHT = 38f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_TIME_GAP = 0f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_TIME_OUTER_MARGIN = 12f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_WIDTH = 60f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_HEIGHT = 50f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_BOTTOM = 12f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_OUTER_MARGIN = 6f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_SCALE_PERCENT = 100f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_DEVICE_SWITCH_SCALE_PERCENT = 90f


    const val DEFAULT_HOOK_TEXT_SIZE = 12
    const val DEFAULT_HOOK_TEXT_SIZE_RATIO = 0.7f
    const val DEFAULT_HOOK_FONT_WEIGHT = 600
    const val DEFAULT_HOOK_FONT_ITALIC = false
    const val DEFAULT_HOOK_NARROW_LATIN_FONT = false
    const val DEFAULT_HOOK_FADING_EDGE_LENGTH = 15
    const val DEFAULT_HOOK_GRADIENT_PROGRESS = true
    const val CONTENT_ALIGNMENT_LEFT = 0
    const val CONTENT_ALIGNMENT_CENTER = 1
    const val CONTENT_ALIGNMENT_RIGHT = 2
    const val DEFAULT_HOOK_LYRIC_ALIGNMENT = CONTENT_ALIGNMENT_LEFT
    const val DEFAULT_HOOK_MUSIC_INFO_ALIGNMENT = CONTENT_ALIGNMENT_LEFT

    fun normalizeHookContentAlignment(value: Int): Int {
        return value.takeIf {
            it in CONTENT_ALIGNMENT_LEFT..CONTENT_ALIGNMENT_RIGHT
        } ?: CONTENT_ALIGNMENT_LEFT
    }

    const val PLACEHOLDER_FORMAT_NONE = 0
    const val PLACEHOLDER_FORMAT_TITLE_ARTIST = 1
    const val PLACEHOLDER_FORMAT_TITLE = 2
    const val PLACEHOLDER_FORMAT_COUNTDOWN = 3
    const val DEFAULT_HOOK_PLACEHOLDER_FORMAT = PLACEHOLDER_FORMAT_COUNTDOWN
    const val ISLAND_MUSIC_WAVE_STYLE_DEFAULT = 0
    const val ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR = 1
    const val ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT = 2
    const val ISLAND_MUSIC_WAVE_STYLE_HIDDEN = 3
    const val DEFAULT_HOOK_ISLAND_MUSIC_WAVE_STYLE = ISLAND_MUSIC_WAVE_STYLE_DEFAULT
    const val DEFAULT_HOOK_ANIM_ENABLE = false
    const val DEFAULT_HOOK_ANIM_ID = "default"
    const val DEFAULT_HOOK_ANIM_SPEED_RATE = 100
    val HOOK_ANIM_SPEED_RATES = listOf(75, 100, 125, 150, 175, 200, 300)

    fun normalizeHookAnimSpeedRate(value: Int): Int {
        return value.takeIf { it in HOOK_ANIM_SPEED_RATES } ?: DEFAULT_HOOK_ANIM_SPEED_RATE
    }

    const val DEFAULT_HOOK_MARQUEE_MODE = false
    const val DEFAULT_HOOK_MARQUEE_SPEED = 30
    const val DEFAULT_HOOK_MARQUEE_DELAY = 1500
    const val DEFAULT_HOOK_MARQUEE_LOOP_DELAY = 1000
    const val DEFAULT_HOOK_MARQUEE_INFINITE = false
    const val DEFAULT_HOOK_MARQUEE_STOP_END = true
    const val DEFAULT_HOOK_MARQUEE_METADATA_SPEED = 10
    const val DEFAULT_HOOK_MARQUEE_METADATA_MODE = true
    const val DEFAULT_HOOK_MARQUEE_METADATA_DELAY = 4000
    const val DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY = 5000
    const val DEFAULT_HOOK_MARQUEE_METADATA_INFINITE = true

    const val DEFAULT_HOOK_SYLLABLE_RELATIVE = true
    const val DEFAULT_HOOK_SYLLABLE_HIGHLIGHT = false
    const val DEFAULT_HOOK_SYLLABLE_LINE_DISPLAY = false

    const val DEFAULT_HOOK_ONLY_SECONDARY = false
    const val DEFAULT_HOOK_SWAP_SECONDARY = false
    const val DEFAULT_HOOK_LYRIC_SHOW_TRANSLATION = true
    const val DEFAULT_HOOK_LYRIC_SHOW_ROMA = false
    const val DEFAULT_HOOK_LYRIC_SHOW_NEXT_LINE = false
    const val DEFAULT_HOOK_LYRIC_SHOW_BACKGROUND_VOCAL = true
    const val DEFAULT_HOOK_LYRIC_SHOW_OVERLAPPING_LINE = true
    const val DEFAULT_HOOK_LYRIC_SECONDARY_ORDER =
        "background_vocal,overlapping_line,translation,next_line,roma"
    const val DEFAULT_HOOK_LYRIC_AUTO_DUET = true
    const val DEFAULT_HOOK_AI_TRANS_ENABLE = false
    val DEFAULT_HOOK_AI_TRANS_SKIP_LANGUAGES: Set<String> = emptySet()
    const val DEFAULT_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION = false
    const val DEFAULT_HOOK_AI_TRANS_FORCE_OVERRIDE = false
    const val DEFAULT_HOOK_AI_TRANS_PROVIDER = "OPENAI"
    const val DEFAULT_HOOK_AI_TRANS_MODEL = "mimo-v2.5"
    const val DEFAULT_HOOK_AI_TRANS_BASE_URL = "https://api.xiaomimimo.com/v1/"
    const val DEFAULT_HOOK_AI_TRANS_TARGET_LANG = "中文"
    const val DEFAULT_HOOK_AI_TRANS_PROMPT =
        "你是一名专业的歌词翻译者。请忠实传达原意，使用自然、优美、符合目标语言习惯的表达，保留歌曲的情绪、意象与节奏感，避免生硬直译。"
    const val DEFAULT_HOOK_AI_TRANS_TEMPERATURE = 1f
    const val DEFAULT_HOOK_AI_TRANS_TOP_P = 1f
    const val DEFAULT_HOOK_AI_TRANS_MAX_TOKENS = 0L
    const val DEFAULT_HOOK_AMLL_TTML_ENABLE = false
    const val DEFAULT_HOOK_AMLL_TTML_PLATFORM_PROBE = true
    const val DEFAULT_HOOK_AMLL_TTML_API_BASE_URL = "https://api.amll.dev/"
    const val DEFAULT_HOOK_AMLL_TTML_DUET_PERFORMANCE = true
    const val TEXT_COLOR_STYLE_DEFAULT = 0
    const val TEXT_COLOR_STYLE_COVER_COLOR = 1
    const val TEXT_COLOR_STYLE_COVER_GRADIENT = 2
    const val TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR = 3
    const val DEFAULT_HOOK_TEXT_COLOR_STYLE = TEXT_COLOR_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR = false
    const val DEFAULT_HOOK_ISLAND_PROGRESS_GLOW = false
    const val DEFAULT_HOOK_ISLAND_PROGRESS_GRADIENT = false
    const val ISLAND_PROGRESS_STYLE_TOP_CLOCKWISE = 0
    const val ISLAND_PROGRESS_STYLE_RIGHT_CLOCKWISE = 1
    const val ISLAND_PROGRESS_STYLE_BOTTOM_CLOCKWISE = 2
    const val ISLAND_PROGRESS_STYLE_LEFT_CLOCKWISE = 3
    const val ISLAND_PROGRESS_STYLE_LEFT_BIDIRECTIONAL = 4
    const val ISLAND_PROGRESS_STYLE_TOP_BIDIRECTIONAL = 5
    const val ISLAND_PROGRESS_STYLE_BOTTOM_BIDIRECTIONAL = 6
    const val DEFAULT_HOOK_ISLAND_PROGRESS_STYLE = ISLAND_PROGRESS_STYLE_TOP_CLOCKWISE
    const val DEFAULT_HOOK_WORD_MOTION_ENABLED = false
    const val DEFAULT_HOOK_WORD_MOTION_CJK_LIFT = 0.12f
    const val DEFAULT_HOOK_WORD_MOTION_CJK_WAVE = 0.9f
    const val DEFAULT_HOOK_WORD_MOTION_LATIN_BY_CHARACTER = false
    const val DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT = 0.12f
    const val DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE = 0.8f
}
