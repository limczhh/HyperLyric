package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

data class WordHighlightPreferenceState(
    val unsungOpacity: Float
)

object WordHighlightPreferencePolicy {
    fun read(prefs: SharedPreferences): WordHighlightPreferenceState = WordHighlightPreferenceState(
        unsungOpacity = normalizeUnsungOpacity(
            getFloatCompat(
                prefs,
                RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY
            )
        )
    )

    fun normalizeUnsungOpacity(
        value: Float,
        fallback: Float = RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY
    ): Float = normalize(
        value = value,
        fallback = fallback,
        min = RootConstants.MIN_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
        max = RootConstants.MAX_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY
    )

    private fun normalize(value: Float, fallback: Float, min: Float, max: Float): Float =
        if (value.isFinite()) value.coerceIn(min, max) else fallback
}
