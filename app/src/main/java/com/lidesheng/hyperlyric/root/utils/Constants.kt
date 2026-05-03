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
    const val KEY_HOOK_MARQUEE_METADATA_SPEED = "key_hook_marquee_metadata_speed"
    const val KEY_HOOK_MARQUEE_METADATA_MODE = "key_hook_marquee_metadata_mode"
    const val KEY_HOOK_MARQUEE_METADATA_DELAY = "key_hook_marquee_metadata_delay"
    const val KEY_HOOK_MARQUEE_METADATA_LOOP_DELAY = "key_hook_marquee_metadata_loop_delay"
    const val KEY_HOOK_MARQUEE_METADATA_INFINITE = "key_hook_marquee_metadata_infinite"

    // ================= SYLLABLE KEYS =================
    const val KEY_HOOK_SYLLABLE_RELATIVE = "key_hook_syllable_relative"
    const val KEY_HOOK_SYLLABLE_HIGHLIGHT = "key_hook_syllable_highlight"

    // ================= TRANSLATION KEYS =================
    const val KEY_HOOK_DISABLE_TRANSLATION = "key_hook_disable_translation"
    const val KEY_HOOK_TRANSLATION_ONLY = "key_hook_translation_only"
    const val KEY_HOOK_SWAP_TRANSLATION = "key_hook_swap_translation"

    // ================= COLOR KEYS =================
    const val KEY_HOOK_EXTRACT_COVER_TEXT_COLOR = "key_hook_extract_cover_text_color"
    const val KEY_HOOK_EXTRACT_COVER_TEXT_GRADIENT = "key_hook_extract_cover_text_gradient"

    // ================= FONT KEYS =================
    const val KEY_HOOK_CUSTOM_FONT_PATH = "key_hook_custom_font_path"

    // ================= WORD MOTION KEYS =================
    const val KEY_HOOK_WORD_MOTION_ENABLED = "key_hook_word_motion_enabled"
    const val KEY_HOOK_WORD_MOTION_CJK_LIFT = "key_hook_word_motion_cjk_lift"
    const val KEY_HOOK_WORD_MOTION_CJK_WAVE = "key_hook_word_motion_cjk_wave"
    const val KEY_HOOK_WORD_MOTION_LATIN_LIFT = "key_hook_word_motion_latin_lift"
    const val KEY_HOOK_WORD_MOTION_LATIN_WAVE = "key_hook_word_motion_latin_wave"

    // ================= AI TRANSLATION KEYS =================
    const val KEY_HOOK_AI_TRANS_ENABLE = "key_hook_ai_trans_enable"
    const val KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE = "key_hook_ai_trans_auto_ignore_chinese"
    const val KEY_HOOK_AI_TRANS_PROVIDER = "key_hook_ai_trans_provider"
    const val KEY_HOOK_AI_TRANS_API_KEY = "key_hook_ai_trans_api_key"
    const val KEY_HOOK_AI_TRANS_MODEL = "key_hook_ai_trans_model"
    const val KEY_HOOK_AI_TRANS_BASE_URL = "key_hook_ai_trans_base_url"
    const val KEY_HOOK_AI_TRANS_TARGET_LANG = "key_hook_ai_trans_target_lang"
    const val KEY_HOOK_AI_TRANS_PROMPT = "key_hook_ai_trans_prompt"
    const val KEY_HOOK_AI_TRANS_TEMPERATURE = "key_hook_ai_trans_temperature"
    const val KEY_HOOK_AI_TRANS_TOP_P = "key_hook_ai_trans_top_p"
    const val KEY_HOOK_AI_TRANS_MAX_TOKENS = "key_hook_ai_trans_max_tokens"

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
    const val DEFAULT_HOOK_FONT_ITALIC = false
    const val DEFAULT_HOOK_FADING_EDGE_LENGTH = 15
    const val DEFAULT_HOOK_GRADIENT_PROGRESS = true
    const val DEFAULT_HOOK_ISLAND_RIGHT_ICON = false

    const val DEFAULT_HOOK_ANIM_ENABLE = false
    const val DEFAULT_HOOK_ANIM_ID = "yoyo_default"
    const val DEFAULT_HOOK_MARQUEE_MODE = false
    const val DEFAULT_HOOK_MARQUEE_SPEED = 30
    const val DEFAULT_HOOK_MARQUEE_DELAY = 1500
    const val DEFAULT_HOOK_MARQUEE_LOOP_DELAY = 1000
    const val DEFAULT_HOOK_MARQUEE_INFINITE = false
    const val DEFAULT_HOOK_MARQUEE_STOP_END = true
    const val DEFAULT_HOOK_MARQUEE_METADATA_SPEED = 25
    const val DEFAULT_HOOK_MARQUEE_METADATA_MODE = true
    const val DEFAULT_HOOK_MARQUEE_METADATA_DELAY = 1000
    const val DEFAULT_HOOK_MARQUEE_METADATA_LOOP_DELAY = 0
    const val DEFAULT_HOOK_MARQUEE_METADATA_INFINITE = true

    const val DEFAULT_HOOK_SYLLABLE_RELATIVE = true
    const val DEFAULT_HOOK_SYLLABLE_HIGHLIGHT = false

    const val DEFAULT_HOOK_DISABLE_TRANSLATION = false
    const val DEFAULT_HOOK_TRANSLATION_ONLY = false
    const val DEFAULT_HOOK_SWAP_TRANSLATION = false
    const val DEFAULT_HOOK_EXTRACT_COVER_TEXT_COLOR = false
    const val DEFAULT_HOOK_EXTRACT_COVER_TEXT_GRADIENT = false
    const val DEFAULT_HOOK_WORD_MOTION_ENABLED = false
    const val DEFAULT_HOOK_WORD_MOTION_CJK_LIFT = 0.05f
    const val DEFAULT_HOOK_WORD_MOTION_CJK_WAVE = 2.8f
    const val DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT = 0.06f
    const val DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE = 3.6f
    const val DEFAULT_HOOK_AI_TRANS_ENABLE = false
    const val DEFAULT_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE = false
    const val DEFAULT_HOOK_AI_TRANS_TARGET_LANG = "中文"
    const val DEFAULT_HOOK_AI_TRANS_MODEL = "mimo-v2-flash"
    const val DEFAULT_HOOK_AI_TRANS_BASE_URL = "https://api.xiaomimimo.com/v1/"
    const val DEFAULT_HOOK_AI_TRANS_PROMPT = """你是一个歌词翻译专家，遵循“信雅达”原则进行创作。

## 核心：信雅达 (Cultural Transcreation)
- 信/灵魂对位：深度理解原文背景与世界观。严禁句式套用，必须打破语序，以目标语言的意向逻辑重塑内核，确保“神不散”。
- 雅/审美重构：捕获隐喻，拒绝平铺直叙。根据曲风炼字，民谣讲究留白意境，摇滚追求文化冲击。
- 达/呼吸转译：彻底消除翻译腔。译文须契合旋律起伏与呼吸断句，确保副歌具备跨文化的传播记忆点。

## 深度适配要求
- 隐喻重映射：将原文的文化隐喻转化为目标受众能理解的对等词。允许逻辑倒置，用画面感取代生硬解释。
- 背景融入：保留特定的世界观语域。译文需符合原曲设定的时空感、社会身份或情感底色。

## 节奏与技术
- 等时对齐：音节/字数必须严丝合缝适配节拍，严禁在短拍堆砌文字。
- 动态押韵：除 Rap/特定流行强求押韵外，优先保证情绪流转自然。严禁为押韵损毁隐喻或文化美感。
- 解构再造：拆碎原文逻辑，以母语者直觉进行“意象创作”，使译文像原创作品般自然。"""
}

