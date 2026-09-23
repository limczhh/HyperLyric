package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

/** Keeps the Super Island and status-bar lyric output switches mutually exclusive. */
object LyricOutputTargetPreferencePolicy {
    fun isTargetKey(key: String): Boolean =
        key == RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND ||
                key == StatusBarLyricPreferences.KEY_ENABLED

    fun updatesFor(key: String, enabled: Boolean): Map<String, Boolean>? {
        if (!isTargetKey(key)) return null
        if (!enabled) return mapOf(key to false)

        val otherKey = if (key == RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND) {
            StatusBarLyricPreferences.KEY_ENABLED
        } else {
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND
        }
        return linkedMapOf(key to true, otherKey to false)
    }

    /**
     * Repairs old or restored preference sets. If only one target was explicitly saved,
     * preserve it; when both were saved as enabled, retain the established Super Island choice.
     */
    fun normalizeInPlace(
        prefs: SharedPreferences,
        preferredEnabledKey: String? = null,
    ) {
        val islandEnabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
            RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND,
        )
        val statusBarEnabled = prefs.getBoolean(
            StatusBarLyricPreferences.KEY_ENABLED,
            StatusBarLyricPreferences.DEFAULT_ENABLED,
        )
        if (!islandEnabled || !statusBarEnabled) return

        val keepIsland = when {
            preferredEnabledKey == StatusBarLyricPreferences.KEY_ENABLED -> false
            preferredEnabledKey == RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND -> true
            prefs.contains(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND) &&
                    !prefs.contains(StatusBarLyricPreferences.KEY_ENABLED) -> true

            prefs.contains(StatusBarLyricPreferences.KEY_ENABLED) &&
                    !prefs.contains(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND) -> false

            else -> true
        }
        val disabledKey = if (keepIsland) {
            StatusBarLyricPreferences.KEY_ENABLED
        } else {
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND
        }
        prefs.edit().putBoolean(disabledKey, false).apply()
    }

    fun read(prefs: SharedPreferences): Map<String, Boolean> = mapOf(
        RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND to prefs.getBoolean(
            RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
            RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND,
        ),
        StatusBarLyricPreferences.KEY_ENABLED to prefs.getBoolean(
            StatusBarLyricPreferences.KEY_ENABLED,
            StatusBarLyricPreferences.DEFAULT_ENABLED,
        ),
    )
}
