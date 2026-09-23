package com.lidesheng.hyperlyric.root.lyricenhancement

import android.os.Handler
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.renderer.LyricRenderer
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns the per-song lifecycle around the two built-in lyric enhancements.
 *
 * [RootLyricSink] remains responsible for receiving source/media events and rendering them. This
 * class keeps the enhancement request identity, coalescing and stale-result checks together so
 * those details do not become another concern of the source sink.
 */
internal class LyricEnhancementController(
    private val renderer: LyricRenderer,
    private val mainHandler: Handler,
    private val coordinator: LyricEnhancementCoordinator?,
) : AutoCloseable {
    @Volatile
    private var closed = false
    @Volatile
    private var latestMediaInfo: LyricEnhancementMediaInfo? = null
    private var sourceSong: Song? = null
    private var pendingRepeatedSourceSong: Song? = null
    private var activeMediaIdentity: MediaIdentity? = null
    private var activeMediaSourceId: String? = null
    private val requestTracker = LyricEnhancementRequestTracker()
    private var activeRequestKey: LyricEnhancementRequestKey? = null
    private var startScheduled = false
    private val requestGeneration = AtomicLong(0L)
    private val startRunnable = Runnable {
        startScheduled = false
        startProcessing()
    }

    init {
        coordinator?.setConfigChangedListener {
            mainHandler.post {
                if (closed) return@post
                invalidateRequest(reason = "configuration_changed")
            }
        }
    }

    /** Returns true when the source repeated its current snapshot instead of changing songs. */
    fun onSongChanged(song: Song?): Boolean {
        if (closed) return false
        val repeatedSourceEvent = song != null &&
                song == sourceSong &&
                LyriconDataBridge.currentSong != null &&
                activeMediaIdentity != null
        if (repeatedSourceEvent) {
            pendingRepeatedSourceSong = song
            if (coordinator?.isEnabled() == true) {
                HookLogger.d(
                    TAG,
                    "enhancement_request event=request_deduplicated " +
                            "reason=repeated_source_event fingerprint=" +
                            song.hashCode()
                )
            }
            return true
        }

        pendingRepeatedSourceSong = null
        cancelPendingStart()
        invalidateRequest(
            reason = if (song == null) "song_cleared" else "song_changed"
        )
        activeMediaIdentity = null
        activeMediaSourceId = null
        latestMediaInfo = null
        sourceSong = song
        return false
    }

    fun onSongCommitted(hasSong: Boolean) {
        if (closed) return
        if (hasSong) {
            scheduleProcessing()
        } else {
            sourceSong = null
        }
    }

    fun onMetadataCleared() {
        if (closed) return
        latestMediaInfo = null
        activeMediaIdentity = null
        activeMediaSourceId = null
        pendingRepeatedSourceSong = null
        cancelPendingStart()
        invalidateRequest(reason = "metadata_cleared")
    }

    /**
     * Updates request identity before Core applies the resolved media fields. If a repeated source
     * snapshot now proves to be a different media item, the raw snapshot must be restored first.
     */
    fun beforeResolvedMediaInfo(
        mediaIdentity: MediaIdentity,
        sourceId: String,
    ): LyricEnhancementMediaTransition {
        if (closed) return LyricEnhancementMediaTransition()
        val mediaChanged = activeMediaIdentity?.isCompatibleWith(mediaIdentity) == false
        val sourceChanged = activeMediaSourceId != null && activeMediaSourceId != sourceId
        val repeatedSourceSong = pendingRepeatedSourceSong
        pendingRepeatedSourceSong = null
        if (repeatedSourceSong != null && (mediaChanged || sourceChanged)) {
            HookLogger.d(
                TAG,
                "enhancement_request event=request_cancelled " +
                        "reason=source_media_identity_changed"
            )
            cancelPendingStart()
            invalidateRequest(reason = "source_media_identity_changed")
            activeMediaIdentity = null
            activeMediaSourceId = null
            latestMediaInfo = null
        }
        return LyricEnhancementMediaTransition(
            mediaChanged = mediaChanged,
            repeatedSourceSong = repeatedSourceSong
                ?.takeIf { mediaChanged || sourceChanged },
        )
    }

    fun afterResolvedMediaInfo(
        mediaInfo: MediaMetadataHelper.MediaInfo,
        sourcePackageName: String?,
        sourceId: String,
    ) {
        if (closed) return
        latestMediaInfo = mediaInfo.toLyricEnhancementMediaInfo(sourcePackageName)
        activeMediaIdentity = mediaInfo.identity
        activeMediaSourceId = sourceId
        invalidateIfInputChanged()
        scheduleProcessing()
    }

    fun onStop() {
        if (closed) return
        latestMediaInfo = null
        sourceSong = null
        pendingRepeatedSourceSong = null
        activeMediaIdentity = null
        activeMediaSourceId = null
        cancelPendingStart()
        invalidateRequest(reason = "stopped")
    }

    override fun close() {
        if (closed) return
        closed = true
        coordinator?.setConfigChangedListener(null)
        cancelPendingStart()
        invalidateRequest(reason = "closed")
    }

    private fun scheduleProcessing() {
        val currentCoordinator = coordinator ?: return
        if (closed || !currentCoordinator.isEnabled() || startScheduled) return
        startScheduled = true
        mainHandler.post(startRunnable)
    }

    /** Starts one pass with the current media fields and source lyric snapshot. */
    private fun startProcessing() {
        val currentCoordinator = coordinator ?: return
        if (closed || !currentCoordinator.isEnabled()) return
        val baseSong = LyriconDataBridge.currentSong ?: return
        val expectedVersion = LyriconDataBridge.versionCounter.get()
        val requestKey = currentRequestKey() ?: return
        if (activeRequestKey != null && activeRequestKey != requestKey) {
            invalidateRequest(reason = "effective_input_changed")
        }
        if (requestTracker.isDuplicate(requestKey)) {
            HookLogger.d(
                TAG,
                "enhancement_request event=request_deduplicated " +
                        "fingerprint=" + requestKey.hashCode()
            )
            return
        }
        requestTracker.markStarted(requestKey)
        activeRequestKey = requestKey
        val expectedRequest = requestGeneration.incrementAndGet()
        val enhancementSnapshot = sourceSong?.deepCopy()?.let { source ->
            baseSong.deepCopy().copy(lyrics = source.lyrics)
        } ?: baseSong.deepCopy()
        val processingMediaInfo = latestMediaInfo
        HookLogger.d(
            TAG,
            "enhancement_request event=request_started " +
                    "fingerprint=" + requestKey.hashCode() +
                    " generation=" + expectedRequest
        )
        currentCoordinator.enhance(
            song = enhancementSnapshot,
            input = LyricEnhancementInput(mediaInfo = processingMediaInfo),
        ) { processedSong: Song? ->
            mainHandler.post {
                if (closed) return@post
                if (requestGeneration.get() != expectedRequest) {
                    logStaleResult(requestKey, "generation_changed")
                    return@post
                }
                if (LyriconDataBridge.versionCounter.get() != expectedVersion ||
                    LyriconDataBridge.currentSong !== baseSong
                ) {
                    logStaleResult(requestKey, "song_snapshot_changed")
                    if (activeRequestKey == requestKey) {
                        activeRequestKey = null
                    }
                    return@post
                }
                if (processedSong == null) {
                    if (activeRequestKey == requestKey) {
                        activeRequestKey = null
                    }
                    HookLogger.d(
                        TAG,
                        "enhancement_request event=request_completed status=no_result " +
                                "fingerprint=" + requestKey.hashCode()
                    )
                    return@post
                }
                if (LyriconDataBridge.applyLyricEnhancement(
                        enhancedSong = processedSong,
                        expectedVersion = expectedVersion,
                        expectedBaseSong = baseSong,
                    )
                ) {
                    renderer.updateLyricLine()
                    if (activeRequestKey == requestKey) {
                        activeRequestKey = null
                    }
                    HookLogger.d(
                        TAG,
                        "enhancement_request event=request_completed status=applied " +
                                "fingerprint=" + requestKey.hashCode()
                    )
                } else {
                    logStaleResult(requestKey, "writeback_rejected")
                    if (activeRequestKey == requestKey) {
                        activeRequestKey = null
                    }
                }
            }
        }
    }

    private fun currentRequestKey(): LyricEnhancementRequestKey? {
        val song = sourceSong?.deepCopy()
            ?: LyriconDataBridge.currentSong?.deepCopy()
            ?: return null
        return LyricEnhancementRequestKey(
            sourceSong = song,
            mediaIdentity = activeMediaIdentity?.normalized(),
            mediaInfo = latestMediaInfo,
        )
    }

    private fun invalidateIfInputChanged() {
        val active = activeRequestKey ?: return
        val current = currentRequestKey() ?: return
        if (active != current) {
            invalidateRequest(reason = "effective_input_changed")
        }
    }

    private fun invalidateRequest(reason: String) {
        val active = activeRequestKey
        if (active != null) {
            HookLogger.d(
                TAG,
                "enhancement_request event=request_cancelled reason=$reason " +
                        "fingerprint=${active.hashCode()}"
            )
        }
        activeRequestKey = null
        requestTracker.reset()
        requestGeneration.incrementAndGet()
        coordinator?.cancelActiveProcessing()
    }

    private fun cancelPendingStart() {
        mainHandler.removeCallbacks(startRunnable)
        startScheduled = false
    }

    private fun logStaleResult(
        requestKey: LyricEnhancementRequestKey,
        reason: String,
    ) {
        HookLogger.d(
            TAG,
            "enhancement_request event=stale_result_ignored reason=$reason " +
                    "fingerprint=${requestKey.hashCode()}"
        )
    }

    private companion object {
        const val TAG = "LyricEnhancement"
    }
}

internal data class LyricEnhancementMediaTransition(
    val mediaChanged: Boolean = false,
    val repeatedSourceSong: Song? = null,
)
