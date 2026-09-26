package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

object SuperIslandContentStylePolicy {
    fun readAlbumCoverStyle(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_ALBUM_COVER_STYLE
        ).coerceIn(
            RootConstants.ISLAND_ALBUM_COVER_STYLE_DEFAULT,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_MONOCHROME
        )
    }

    fun isAlbumCoverVisible(style: Int): Boolean =
        style != RootConstants.ISLAND_ALBUM_COVER_STYLE_HIDDEN

    fun readMusicWaveStyle(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_STYLE
        ).coerceIn(
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_DEFAULT,
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_HIDDEN
        )
    }

    fun isMusicWaveVisible(style: Int): Boolean =
        style != RootConstants.ISLAND_MUSIC_WAVE_STYLE_HIDDEN

    fun usesMusicWaveCoverColor(style: Int): Boolean =
        style == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR ||
                style == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT

    fun usesMusicWaveGradient(style: Int): Boolean =
        style == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT
}
