package com.lidesheng.hyperlyric.lyric.source

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine

class SourceManager(
    private val sources: List<LyricSource>,
    private val prefs: SharedPreferences,
    private val sink: LyricSink,
    private val prefKey: String,
    private val defaultSourceId: String,
    private val logger: HyperLogger
) {
    private var activeSource: LyricSource? = null
    @Volatile
    private var activeSession: SourceSession? = null

    fun start() {
        if (activeSource != null) return

        val sourceId = prefs.getString(prefKey, defaultSourceId) ?: defaultSourceId
        val source = sources.find { it.id == sourceId && it.isAvailable() }
            ?: sources.firstOrNull { it.isAvailable() }

        if (source == null) {
            logger.w("SourceManager", "没有可用的歌词源")
            return
        }

        if (source.id != sourceId) {
            logger.d(
                "SourceManager",
                "歌词源配置回退: requested=$sourceId, actual=${source.id}, reason=unavailable"
            )
        }
        startSession(source)
        if (activeSource === source) {
            logger.i("SourceManager", "启动歌词源: ${source.displayName}")
        }
    }

    fun switchSource(sourceId: String) {
        val current = activeSource
        if (current?.id == sourceId) return

        stopActiveSource()

        val source = sources.find { it.id == sourceId && it.isAvailable() }
        if (source == null) {
            logger.w("SourceManager", "歌词源不可用: $sourceId")
            return
        }

        startSession(source)
    }

    fun getActiveSource(): LyricSource? = activeSource

    fun stop() {
        stopActiveSource()
    }

    private fun startSession(source: LyricSource) {
        val session = SourceSession(
            downstream = sink
        )
        activeSource = source
        activeSession = session
        try {
            source.start(session)
        } catch (e: Exception) {
            session.invalidate()
            activeSession = null
            activeSource = null
            runCatching { source.stop() }
            sink.onStop()
            logger.e("SourceManager", "歌词源启动失败: source=${source.id}", e)
        }
    }

    private fun stopActiveSource() {
        val source = activeSource
        val session = activeSession
        if (source == null && session == null) return
        activeSource = null
        activeSession = null
        session?.invalidate()

        try {
            source?.stop()
        } catch (e: Exception) {
            logger.e("SourceManager", "歌词源停止失败: source=${source?.id}", e)
        } finally {
            // The source callback is intentionally invalidated before stop(). This is the
            // single cleanup edge for the downstream state, even if a source emits a late stop.
            sink.onStop()
        }
    }

    private class SourceSession(
        private val downstream: LyricSink
    ) : LyricSink {
        private val dispatchLock = Any()

        @Volatile
        private var accepting = true

        fun invalidate() {
            synchronized(dispatchLock) {
                accepting = false
            }
        }

        private fun dispatch(block: (LyricSink) -> Unit) {
            synchronized(dispatchLock) {
                if (accepting) block(downstream)
            }
        }

        override fun onSongChanged(song: Song?) = dispatch { it.onSongChanged(song) }

        override fun onLyricLine(line: IRichLyricLine) = dispatch { it.onLyricLine(line) }

        override fun onPlainText(text: String?) = dispatch { it.onPlainText(text) }

        override fun onStop() = dispatch { it.onStop() }

        override fun onMetadata(metadata: LyricMediaMetadata?) =
            dispatch { it.onMetadata(metadata) }

        override fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float) =
            dispatch { it.onPlaybackStateChanged(isPlaying, playbackSpeed) }

        override fun onPositionChanged(position: Long, playbackSpeed: Float) =
            dispatch { it.onPositionChanged(position, playbackSpeed) }
    }
}
