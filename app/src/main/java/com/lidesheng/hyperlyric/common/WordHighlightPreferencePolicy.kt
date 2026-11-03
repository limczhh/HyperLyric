package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

data class WordHighlightPreferenceState(
    val unsungOpacity: Float,
    /** 已演唱歌词不透明度；保留默认值，避免既有只构造未演唱字段的调用点编译失败。 */
    val sungOpacity: Float = RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY
)

object WordHighlightPreferencePolicy {
    fun read(prefs: SharedPreferences): WordHighlightPreferenceState = WordHighlightPreferenceState(
        unsungOpacity = normalizeUnsungOpacity(
            getFloatCompat(
                prefs,
                RootConstants.KEY_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY,
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_UNSUNG_OPACITY
            )
        ),
        sungOpacity = normalizeSungOpacity(
            getFloatCompat(
                prefs,
                RootConstants.KEY_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
                RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY
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

    fun normalizeSungOpacity(
        value: Float,
        fallback: Float = RootConstants.DEFAULT_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY
    ): Float = normalize(
        value = value,
        fallback = fallback,
        min = RootConstants.MIN_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY,
        max = RootConstants.MAX_HOOK_WORD_HIGHLIGHT_SUNG_OPACITY
    )

    private fun normalize(value: Float, fallback: Float, min: Float, max: Float): Float =
        if (value.isFinite()) value.coerceIn(min, max) else fallback
}
