package com.lidesheng.hyperlyric.root.island.renderer

import com.lidesheng.hyperlyric.root.statusbar.StatusBarLyricRenderer

/** Sends the same source events to the Super Island and status-bar lyric projections. */
internal object SystemUiLyricRenderer : LyricRenderer {
    override fun refreshActiveIsland() {
        BaseIslandRenderer.refreshActiveIsland()
        StatusBarLyricRenderer.updateLyricLine()
    }

    override fun updateMetadata() {
        BaseIslandRenderer.updateMetadata()
        StatusBarLyricRenderer.updateMetadata()
    }

    override fun updateLyricLine() {
        BaseIslandRenderer.updateLyricLine()
        StatusBarLyricRenderer.updateLyricLine()
    }

    override fun updateTextColors() {
        BaseIslandRenderer.updateTextColors()
        StatusBarLyricRenderer.updateTextColors()
    }

    override fun updatePosition(position: Long, playbackSpeed: Float) {
        BaseIslandRenderer.updatePosition(position, playbackSpeed)
        StatusBarLyricRenderer.updatePosition(position, playbackSpeed)
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        BaseIslandRenderer.onPlaybackStateChanged(isPlaying)
        StatusBarLyricRenderer.onPlaybackStateChanged(isPlaying)
    }

    override fun clearAllViews() {
        BaseIslandRenderer.clearAllViews()
        StatusBarLyricRenderer.clearAllViews()
    }
}
