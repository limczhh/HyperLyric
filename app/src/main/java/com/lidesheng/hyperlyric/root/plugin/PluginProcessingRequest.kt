package com.lidesheng.hyperlyric.root.plugin

import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.plugin.api.PluginMediaInfo
import com.lidesheng.hyperlyric.plugin.api.PluginSong

/**
 * Core-only request identity. The source snapshot is kept separately from the current enhanced
 * Song so a repeated source event cannot re-run a chain merely because an earlier plugin changed
 * the current Song. Media identity is deliberately kept outside the public plugin API.
 */
internal data class PluginProcessingRequestKey(
    val sourceSong: PluginSong,
    val mediaIdentity: MediaIdentity?,
    val mediaInfo: PluginMediaInfo?,
    val processorSetFingerprint: String = "",
)

/** Small deterministic last-request guard used by the root lyric sink. */
internal class PluginProcessingRequestTracker {
    private var lastStarted: PluginProcessingRequestKey? = null

    fun isDuplicate(key: PluginProcessingRequestKey): Boolean = lastStarted == key

    fun markStarted(key: PluginProcessingRequestKey) {
        lastStarted = key
    }

    fun reset() {
        lastStarted = null
    }
}
