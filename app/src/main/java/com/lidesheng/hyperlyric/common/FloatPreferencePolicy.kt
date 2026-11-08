package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

/** 存储类型、合法范围与默认值在 App、备份和 Hook 读取之间保持一致。 */
object FloatPreferencePolicy {
    private class Rule(val default: Float, val min: Float, val max: Float) {
        fun normalize(value: Float): Float =
            if (value.isFinite() && value >= min && value <= max) value else default

        fun normalize(value: Any?): Float {
            val number = when (value) {
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull()
                else -> null
            }
            return number?.let { normalize(it) } ?: default
        }
    }

    private val rules: Map<String, Rule> = buildMap {
        val textSizeRatio = Rule(RootConstants.DEFAULT_HOOK_TEXT_SIZE_RATIO, 0.1f, 1f)
        put(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO, textSizeRatio)
        put(StatusBarLyricPreferences.scopedKey(RootConstants.KEY_HOOK_TEXT_SIZE_RATIO), textSizeRatio)

        val multiLineScale = Rule(
            RootConstants.DEFAULT_HOOK_MULTI_LINE_SCALE,
            RootConstants.MIN_HOOK_MULTI_LINE_SCALE,
            RootConstants.MAX_HOOK_MULTI_LINE_SCALE
        )
        put(RootConstants.KEY_HOOK_MULTI_LINE_SCALE, multiLineScale)
        put(StatusBarLyricPreferences.scopedKey(RootConstants.KEY_HOOK_MULTI_LINE_SCALE), multiLineScale)

        val padding = Rule(
            RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
            RootConstants.MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
            RootConstants.MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP,
        )
        put(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP, padding)
        put(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP, padding)

        put(
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
            Rule(
                RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT,
                RootConstants.MIN_HOOK_WORD_MOTION_LIFT,
                RootConstants.MAX_HOOK_WORD_MOTION_LIFT,
            ),
        )
        put(
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
            Rule(
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT,
                RootConstants.MIN_HOOK_WORD_MOTION_LIFT,
                RootConstants.MAX_HOOK_WORD_MOTION_LIFT,
            ),
        )
        put(
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
            Rule(
                RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE,
                RootConstants.MIN_HOOK_WORD_MOTION_WAVE,
                RootConstants.MAX_HOOK_WORD_MOTION_WAVE,
            ),
        )
        put(
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE,
            Rule(
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE,
                RootConstants.MIN_HOOK_WORD_MOTION_WAVE,
                RootConstants.MAX_HOOK_WORD_MOTION_WAVE,
            ),
        )
        put(
            RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            Rule(
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
            ),
        )
        put(
            RootConstants.KEY_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            Rule(
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
                RootConstants.MIN_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
                RootConstants.MAX_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
            ),
        )
        put(
            RootConstants.KEY_HOOK_AI_TRANS_TEMPERATURE,
            Rule(RootConstants.DEFAULT_HOOK_AI_TRANS_TEMPERATURE, 0f, 2f),
        )
        put(
            RootConstants.KEY_HOOK_AI_TRANS_TOP_P,
            Rule(RootConstants.DEFAULT_HOOK_AI_TRANS_TOP_P, 0f, 1f),
        )
    }

    /** 未知 key 返回 null；已知浮点设置的非法值返回该设置默认值。 */
    fun normalizeStoredFloat(key: String, value: Any?): Float? = rules[key]?.normalize(value)

    /** 正常读取路径使用原始 Float，避免经由 Any 转换或创建偏好快照。 */
    fun normalizeReadFloat(key: String, value: Float, fallback: Float): Float =
        rules[key]?.normalize(value) ?: if (value.isFinite()) value else fallback

    /** 仅供 App 的可写本地偏好使用；缺失 key 保持缺失，已有非法值批量写回默认值。 */
    fun normalizeInPlace(prefs: SharedPreferences) {
        val values = prefs.all
        var editor: SharedPreferences.Editor? = null
        rules.forEach { (key, rule) ->
            if (!values.containsKey(key)) return@forEach
            val value = values[key]
            val normalized = rule.normalize(value)
            if (value is Float && value == normalized) return@forEach
            val pendingEditor = editor ?: prefs.edit().also { editor = it }
            pendingEditor.putFloat(key, normalized)
        }
        editor?.apply()
    }
}
