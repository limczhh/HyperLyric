package com.lidesheng.hyperlyric.root.presentation

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.content.IslandMetadataContentAssembler

/** One display decision shared by island/status-bar hosts, content routing and geometry. */
internal object LyricPresentationScheduler {
    data class Presentation(
        val lyricMode: Int,
        val hasSourceLyrics: Boolean,
        val hasPlaceholder: Boolean = false,
    ) {
        val hasLyrics: Boolean
            get() = lyricMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC && hasSourceLyrics
        val hasMusicInfo: Boolean
            get() = lyricMode == RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
        val hasManagedContent: Boolean
            get() = hasLyrics || hasMusicInfo || hasPlaceholder

        fun resolveContentMode(configuredMode: Int): Int =
            if (configuredMode == RootConstants.ISLAND_CONTENT_MODE_LYRIC) lyricMode else configuredMode
    }

    fun snapshot(prefs: SharedPreferences? = HookEntry.instance?.prefs): Presentation {
        val hasLyrics = LyriconDataBridge.hasSourceLyrics()
        val behavior = when {
            LyriconDataBridge.isInTailTimeout -> RootConstants.normalizeTailTimeoutBehavior(
                prefs?.getInt(RootConstants.KEY_HOOK_TAIL_TIMEOUT_BEHAVIOR,
                    RootConstants.DEFAULT_HOOK_TAIL_TIMEOUT_BEHAVIOR)
                    ?: RootConstants.DEFAULT_HOOK_TAIL_TIMEOUT_BEHAVIOR
            )
            LyriconDataBridge.currentSongIsPureMusic || !hasLyrics ->
                RootConstants.normalizeNoLyricsLyricBehavior(
                    prefs?.getInt(RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR,
                        RootConstants.DEFAULT_HOOK_NO_LYRICS_LYRIC_BEHAVIOR)
                        ?: RootConstants.DEFAULT_HOOK_NO_LYRICS_LYRIC_BEHAVIOR
                )
            else -> RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_KEEP
        }
        // Both behavior preferences use KEEP / MUSIC_INFO / RESTORE_DEFAULT values.
        return when (behavior) {
            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_RESTORE_DEFAULT ->
                Presentation(RootConstants.ISLAND_CONTENT_MODE_NONE, hasLyrics)
            RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO -> Presentation(
                if (prefs != null && hasConfiguredMusicInfo(prefs)) {
                    RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
                } else {
                    RootConstants.ISLAND_CONTENT_MODE_LYRIC
                },
                hasLyrics,
            )
            else -> Presentation(
                RootConstants.ISLAND_CONTENT_MODE_LYRIC,
                hasLyrics,
                hasPlaceholder = !hasLyrics && LyriconDataBridge.noLyricsPlaceholderLine() != null,
            )
        }
    }

    private fun hasConfiguredMusicInfo(prefs: SharedPreferences): Boolean {
        val mediaInfo = LyriconDataBridge.resolveMediaInfoForDisplay() ?: return false
        val lines = IslandMetadataContentAssembler.buildConfiguredMusicInfoLines(
            prefs, mediaInfo,
            playbackDuration = LyriconDataBridge.currentSong?.duration?.takeIf { it > 0L }
                ?: mediaInfo.duration,
        )
        return lines.firstLine.isNotBlank() || lines.secondLine.isNotBlank()
    }
}
