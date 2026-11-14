package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

/** Resolves whether the status-bar Clock yields to the lyric projection. */
object StatusBarLyricClockHidePolicy {
    data class State(
        val lyricTypeEnabled: Boolean,
        val temporarilyRevealed: Boolean,
        val playbackActive: Boolean,
        val ownsStatusBarContent: Boolean,
        val lyricTemporarilyHidden: Boolean,
        val islandPresent: Boolean,
        val mediaSessionPresent: Boolean,
        val lockScreenSuppressed: Boolean,
    )

    fun read(prefs: SharedPreferences): Int = prefs.getInt(
        RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
        RootConstants.DEFAULT_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR,
    ).coerceIn(
        RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_NONE,
        RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT,
    )

    fun shouldHide(behavior: Int, state: State): Boolean {
        if (state.temporarilyRevealed || !state.lyricTypeEnabled) return false
        return when (behavior) {
            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING ->
                state.playbackActive && state.ownsStatusBarContent &&
                        !state.lyricTemporarilyHidden

            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT ->
                state.playbackActive && state.islandPresent

            RootConstants.STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_MEDIA_SESSION_PRESENT ->
                state.mediaSessionPresent && !state.lockScreenSuppressed

            else -> false
        }
    }
}
