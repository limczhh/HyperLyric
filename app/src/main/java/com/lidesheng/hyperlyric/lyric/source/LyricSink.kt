package com.lidesheng.hyperlyric.lyric.source

import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.common.media.MediaIdentity

interface LyricSink {
    /** Sources must explicitly report ownership; song, metadata and text events do not imply it. */
    fun onSourceStateChanged(sourceAvailable: Boolean, mediaIdentity: MediaIdentity? = null)
    fun onSongChanged(song: Song?)
    fun onLyricLine(line: IRichLyricLine)
    fun onPlainText(text: String?)
    fun onStop()
    fun onMetadata(metadata: LyricMediaMetadata?)
    /** [isActive] also includes loading/skipping; [isPlaying] controls clock advancement. */
    fun onPlaybackStateChanged(
        isPlaying: Boolean,
        playbackSpeed: Float = Float.NaN,
        isActive: Boolean = isPlaying
    )
    fun onPositionChanged(position: Long, playbackSpeed: Float = Float.NaN)
}
