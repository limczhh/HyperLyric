package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

data class WordMotionPreferenceState(
    val enabled: Boolean,
    val cjkLiftFactor: Float,
    val cjkWaveFactor: Float,
    val latinByCharacter: Boolean,
    val latinLiftFactor: Float,
    val latinWaveFactor: Float
)

object WordMotionPreferencePolicy {
    fun isBoundedFloatKey(key: String): Boolean = when (key) {
        RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
        RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
        RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
        RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE -> true
        else -> false
    }

    fun normalizeStoredFloat(key: String, value: Any?): Float? {
        if (!isBoundedFloatKey(key)) return null
        val raw = (value as? Number)?.toFloat() ?: value?.toString()?.toFloatOrNull()
            ?: return null
        return when (key) {
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT -> normalizeLiftFactor(raw)
            RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE -> normalizeWaveFactor(raw)
            else -> null
        }
    }

    fun read(prefs: SharedPreferences): WordMotionPreferenceState = WordMotionPreferenceState(
        enabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_WORD_MOTION_ENABLED,
            RootConstants.DEFAULT_HOOK_WORD_MOTION_ENABLED
        ),
        cjkLiftFactor = normalizeLiftFactor(
            prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_CJK_LIFT,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT
            ),
            RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_LIFT
        ),
        cjkWaveFactor = normalizeWaveFactor(
            prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_CJK_WAVE,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE
            ),
            RootConstants.DEFAULT_HOOK_WORD_MOTION_CJK_WAVE
        ),
        latinByCharacter = prefs.getBoolean(
            RootConstants.KEY_HOOK_WORD_MOTION_LATIN_BY_CHARACTER,
            RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_BY_CHARACTER
        ),
        latinLiftFactor = normalizeLiftFactor(
            prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_LATIN_LIFT,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT
            ),
            RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_LIFT
        ),
        latinWaveFactor = normalizeWaveFactor(
            prefs.getFloat(
                RootConstants.KEY_HOOK_WORD_MOTION_LATIN_WAVE,
                RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE
            ),
            RootConstants.DEFAULT_HOOK_WORD_MOTION_LATIN_WAVE
        )
    )

    fun normalizeLiftFactor(value: Float, fallback: Float = 0f): Float = normalize(
        value = value,
        fallback = fallback,
        min = RootConstants.MIN_HOOK_WORD_MOTION_LIFT,
        max = RootConstants.MAX_HOOK_WORD_MOTION_LIFT
    )

    fun normalizeWaveFactor(value: Float, fallback: Float = 0f): Float = normalize(
        value = value,
        fallback = fallback,
        min = RootConstants.MIN_HOOK_WORD_MOTION_WAVE,
        max = RootConstants.MAX_HOOK_WORD_MOTION_WAVE
    )

    private fun normalize(value: Float, fallback: Float, min: Float, max: Float): Float =
        if (value.isFinite()) value.coerceIn(min, max) else fallback
}
