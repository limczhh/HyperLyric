package com.lidesheng.hyperlyric.lyric.source

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.HyperLogger

class SourceManager(
    private val sources: List<LyricSource>,
    private val prefs: SharedPreferences,
    private val sink: LyricSink,
    private val prefKey: String,
    private val defaultSourceId: String,
    private val stateResetter: StateResetter,
    private val logger: HyperLogger
) {
    private var activeSource: LyricSource? = null

    fun start() {
        val sourceId = prefs.getString(prefKey, defaultSourceId) ?: defaultSourceId
        val source = sources.find { it.id == sourceId && it.isAvailable() }
            ?: sources.firstOrNull { it.isAvailable() }

        if (source == null) {
            logger.w("SourceManager", "没有可用的歌词源")
            return
        }

        activeSource = source
        logger.i("SourceManager", "启动歌词源: ${source.displayName}")
        source.start(sink)
    }

    fun switchSource(sourceId: String) {
        val current = activeSource
        if (current?.id == sourceId) return

        current?.stop()
        stateResetter.clearState()

        val source = sources.find { it.id == sourceId && it.isAvailable() }
        if (source == null) {
            logger.w("SourceManager", "歌词源不可用: $sourceId")
            return
        }

        activeSource = source
        logger.i("SourceManager", "切换歌词源: ${source.displayName}")
        source.start(sink)
    }

    fun getActiveSource(): LyricSource? = activeSource

    fun stop() {
        activeSource?.stop()
        activeSource = null
    }
}
