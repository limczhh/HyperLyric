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

    fun readMusicWaveMode(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_MODE,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_MODE
        ).takeIf {
            it == RootConstants.ISLAND_MUSIC_WAVE_MODE_DEFAULT ||
                    it == RootConstants.ISLAND_MUSIC_WAVE_MODE_REALTIME_SPECTRUM ||
                    it == RootConstants.ISLAND_MUSIC_WAVE_MODE_HIDDEN
        } ?: RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_MODE
    }

    fun readMusicWaveColorStyle(prefs: SharedPreferences): Int =
        when (prefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_COLOR_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_COLOR_STYLE
        )) {
            RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR ->
                RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR

            RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT ->
                RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT

            else -> RootConstants.ISLAND_MUSIC_WAVE_STYLE_DEFAULT
        }

    fun isMusicWaveVisible(mode: Int): Boolean =
        mode != RootConstants.ISLAND_MUSIC_WAVE_MODE_HIDDEN

    fun usesMusicWaveCoverColor(colorStyle: Int): Boolean =
        colorStyle == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR ||
                colorStyle == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT

    fun usesMusicWaveGradient(colorStyle: Int): Boolean =
        colorStyle == RootConstants.ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT
}
