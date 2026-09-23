package com.lidesheng.hyperlyric.root.island.renderer

/** Receives lyric-source updates and fans them out to one or more SystemUI lyric surfaces. */
internal interface LyricRenderer {
    fun refreshActiveIsland()
    fun updateMetadata()
    fun updateLyricLine()
    fun updateTextColors()
    fun updatePosition(position: Long, playbackSpeed: Float = 1f)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun clearAllViews()
}
