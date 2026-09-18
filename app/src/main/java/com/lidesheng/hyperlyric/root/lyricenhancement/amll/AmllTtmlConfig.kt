package com.lidesheng.hyperlyric.root.lyricenhancement.amll

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.RootConstants

internal data class AmllTtmlConfig(
    val enabled: Boolean,
    val platformProbe: Boolean,
    val apiBaseUrl: String,
    val duetPerformance: Boolean,
) {
    companion object {
        val PREFERENCE_KEYS = setOf(
            RootConstants.KEY_HOOK_AMLL_TTML_ENABLE,
            RootConstants.KEY_HOOK_AMLL_TTML_PLATFORM_PROBE,
            RootConstants.KEY_HOOK_AMLL_TTML_API_BASE_URL,
            RootConstants.KEY_HOOK_AMLL_TTML_DUET_PERFORMANCE,
        )

        fun from(preferences: SharedPreferences): AmllTtmlConfig = AmllTtmlConfig(
            enabled = preferences.getBoolean(
                RootConstants.KEY_HOOK_AMLL_TTML_ENABLE,
                RootConstants.DEFAULT_HOOK_AMLL_TTML_ENABLE
            ),
            platformProbe = preferences.getBoolean(
                RootConstants.KEY_HOOK_AMLL_TTML_PLATFORM_PROBE,
                RootConstants.DEFAULT_HOOK_AMLL_TTML_PLATFORM_PROBE
            ),
            apiBaseUrl = preferences.getString(
                RootConstants.KEY_HOOK_AMLL_TTML_API_BASE_URL,
                RootConstants.DEFAULT_HOOK_AMLL_TTML_API_BASE_URL
            ) ?: RootConstants.DEFAULT_HOOK_AMLL_TTML_API_BASE_URL,
            duetPerformance = preferences.getBoolean(
                RootConstants.KEY_HOOK_AMLL_TTML_DUET_PERFORMANCE,
                RootConstants.DEFAULT_HOOK_AMLL_TTML_DUET_PERFORMANCE
            ),
        )
    }
}
