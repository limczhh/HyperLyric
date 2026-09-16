package com.lidesheng.hyperlyric.root.lyricenhancement

import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.lyric.model.Song

internal data class LyricEnhancementRequestKey(
    val sourceSong: Song,
    val mediaIdentity: MediaIdentity?,
    val mediaInfo: LyricEnhancementMediaInfo?,
)

internal class LyricEnhancementRequestTracker {
    private var lastStarted: LyricEnhancementRequestKey? = null

    fun isDuplicate(key: LyricEnhancementRequestKey): Boolean = lastStarted == key

    fun markStarted(key: LyricEnhancementRequestKey) {
        lastStarted = key
    }

    fun reset() {
        lastStarted = null
    }
}
