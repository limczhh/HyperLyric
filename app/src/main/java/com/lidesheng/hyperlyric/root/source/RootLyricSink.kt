package com.lidesheng.hyperlyric.root.source

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.content.IslandSlotContentFacade
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.renderer.IslandRenderer
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
import com.lidesheng.hyperlyric.root.media.LyricColorBindingCoordinator
import com.lidesheng.hyperlyric.root.media.LyricColorBindingUpdate
import com.lidesheng.hyperlyric.root.plugin.PluginProcessingResult
import com.lidesheng.hyperlyric.root.plugin.PluginProcessingRequestKey
import com.lidesheng.hyperlyric.root.plugin.PluginProcessingRequestTracker
import com.lidesheng.hyperlyric.root.plugin.PluginRuntime
import com.lidesheng.hyperlyric.root.plugin.PluginSongMapper
import com.lidesheng.hyperlyric.plugin.api.PluginMediaInfo
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.root.utils.HookLogger
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicLong

class RootLyricSink(
    private val renderer: IslandRenderer,
    private val context: Context,
    private val prefs: SharedPreferences? = null,
    private val pluginRuntime: PluginRuntime? = null
) : LyricSink, AutoCloseable {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var closed = false
    private var lastPositionDispatchTimeMs = 0L
    private var pendingPosition: PositionSample? = null
    private var positionDispatchScheduled = false
    private var playbackActive = false
    private var lastReceivedPosition = Long.MIN_VALUE
    private var lastReceivedPositionTimeMs = 0L
    private var lastReceivedPlaybackSpeed = Float.NaN
    private var lastDispatchedPosition = Long.MIN_VALUE
    private var lastDispatchedPlaybackSpeed = Float.NaN
    private var currentPlaybackSpeed = 1f
    private var activeMediaIdentity: MediaIdentity? = null
    private var activeMediaSourceId: String? = null
    @Volatile
    private var latestPluginMediaInfo: PluginMediaInfo? = null
    private var sourcePluginSong: PluginSong? = null
    private var pendingRepeatedSourceSong: Song? = null
    private val pluginRequestTracker = PluginProcessingRequestTracker()
    private var activePluginRequestKey: PluginProcessingRequestKey? = null
    private var pluginStartScheduled = false
    private val pluginRequestGeneration = AtomicLong(0L)
    private val artworkColorRefreshRunnable = Runnable {
        handleColorBindingUpdate(
            LyricColorBindingCoordinator.retry(context, playbackActive),
            reason = "delayed_retry"
        )
        renderer.updateTextColors()
    }
    private val sessionBindingRefreshRunnable = Runnable {
        if (closed) return@Runnable
        handleColorBindingUpdate(
            LyricColorBindingCoordinator.retry(context, playbackActive),
            reason = "active_sessions_changed"
        )
    }
    private val activeSessionsObserver: () -> Unit = {
        if (!closed) {
            mainHandler.removeCallbacks(sessionBindingRefreshRunnable)
            mainHandler.post(sessionBindingRefreshRunnable)
        }
    }
    private val pluginStartRunnable = Runnable {
        pluginStartScheduled = false
        startPluginProcessing()
    }
    private val positionDispatchRunnable = Runnable {
        positionDispatchScheduled = false
        val latest = pendingPosition ?: return@Runnable
        pendingPosition = null
        dispatchPosition(latest)
    }

    private companion object {
        const val TAG = "RootLyricSink"
        const val MIN_POSITION_DISPATCH_INTERVAL_MS = 33L
        const val MIN_VALID_PLAYBACK_SPEED = 0.1f
        const val MAX_VALID_PLAYBACK_SPEED = 4f
        const val SPEED_CHANGE_EPSILON = 0.01f
        const val INFERRED_SPEED_BLEND = 0.75f
        const val ARTWORK_COLOR_REFRESH_DELAY_MS = 100L
    }

    private data class PositionSample(val position: Long, val playbackSpeed: Float)

    init {
        MediaMetadataHelper.addActiveSessionsObserver(activeSessionsObserver)
    }

    override fun onSongChanged(song: Song?) {
        cancelArtworkColorRefresh()
        cancelPendingPositionDispatch()
        lastReceivedPosition = Long.MIN_VALUE
        lastReceivedPositionTimeMs = 0L
        lastReceivedPlaybackSpeed = Float.NaN
        lastDispatchedPosition = Long.MIN_VALUE
        lastDispatchedPlaybackSpeed = Float.NaN
        currentPlaybackSpeed = 1f
        val ownedSong = song?.deepCopy()
        val incomingPluginSong = ownedSong?.let(PluginSongMapper::toPluginSong)
        val repeatedSourceEvent = incomingPluginSong != null &&
                incomingPluginSong == sourcePluginSong &&
                LyriconDataBridge.currentSong != null &&
                activeMediaIdentity != null
        if (repeatedSourceEvent) {
            // The DTO may be repeated for a replay or a source callback refresh. Keep the Core
            // state path alive so timing/current-line state is reset, but do not throw away an
            // accepted plugin enhancement before the following metadata event identifies the
            // media item. If that identity changes, onMetadata commits the raw Song below.
            pendingRepeatedSourceSong = ownedSong
            LyriconDataBridge.refreshSongEvent()
            renderer.updateLyricLine()
            HookLogger.i(
                TAG,
                "plugin_request event=request_deduplicated reason=repeated_source_event " +
                        "fingerprint=${incomingPluginSong.hashCode()}"
            )
            return
        }

        pendingRepeatedSourceSong = null
        cancelPendingPluginStart()
        invalidatePluginRequest(reason = if (song == null) "song_cleared" else "song_changed")
        activeMediaIdentity = null
        activeMediaSourceId = null
        latestPluginMediaInfo = null
        sourcePluginSong = incomingPluginSong
        LyriconDataBridge.updateSong(
            song = ownedSong,
            placeholderFormat = prefs?.getInt(
                RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT,
                RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
            ) ?: RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
        )
        if (song == null) {
            sourcePluginSong = null
        } else {
            schedulePluginProcessing()
        }
        if (song == null) {
            endColorBinding()
        }
    }

    override fun onLyricLine(line: IRichLyricLine) {
        LyriconDataBridge.updateLyricLine(line)
        renderer.updateLyricLine()
    }

    override fun onPlainText(text: String?) {

        LyriconDataBridge.updateLyric(text)
        renderer.updateLyricLine()
    }

    override fun onStop() {
        cancelArtworkColorRefresh()
        playbackActive = false
        cancelPendingPositionDispatch()
        lastReceivedPosition = Long.MIN_VALUE
        lastReceivedPositionTimeMs = 0L
        lastReceivedPlaybackSpeed = Float.NaN
        lastDispatchedPosition = Long.MIN_VALUE
        lastDispatchedPlaybackSpeed = Float.NaN
        currentPlaybackSpeed = 1f
        activeMediaIdentity = null
        activeMediaSourceId = null
        latestPluginMediaInfo = null
        sourcePluginSong = null
        pendingRepeatedSourceSong = null
        cancelPendingPluginStart()
        invalidatePluginRequest(reason = "stopped")
        endColorBinding()
        renderer.clearAllViews()
        LyriconDataBridge.clearState()
    }

    override fun onMetadata(metadata: LyricMediaMetadata?) {
        val normalized = metadata?.normalized()
        normalized?.packageName?.let(LyriconDataBridge::updateLyricPackage)
        if (normalized == null) {
            LyriconDataBridge.updateMediaMetadata(null)
            latestPluginMediaInfo = null
            activeMediaIdentity = null
            activeMediaSourceId = null
            pendingRepeatedSourceSong = null
            cancelPendingPluginStart()
            invalidatePluginRequest(reason = "metadata_cleared")
            endColorBinding()
            renderer.updateMetadata()
            return
        }
        val colorBindingUpdate = LyricColorBindingCoordinator.updateSource(
            context = context,
            metadata = normalized
        )
        if (normalized.isPackageOnlySnapshot()) {
            HookLogger.dState(
                stateId = "RootLyricSink.metadata",
                tag = TAG,
                state = "package_only|${normalized.sourceId}|${normalized.packageName}|" +
                        colorBindingUpdate.reason
            ) {
                "媒体展示字段暂不可用: source=${normalized.sourceId}, " +
                        "package=${normalized.packageName ?: "<empty>"}, " +
                        "colorBinding=${colorBindingUpdate.reason}"
            }
            handleColorBindingUpdate(colorBindingUpdate, reason = "package_only_metadata")
            renderer.updateMetadata()
            scheduleArtworkColorRefresh()
            return
        }
        LyriconDataBridge.updateMediaMetadata(normalized)
        // This is deliberately captured before Core supplements the internal media state. A
        // plugin may only receive the package supplied by this lyric source event, never a
        // MediaSession/identity package or the previous lyric package retained by the bridge.
        val sourcePackageName = normalized.packageName?.takeIf { it.isNotBlank() }
        val packageName = normalized.packageName
            ?: LyriconDataBridge.currentLyricPackageName
            ?: ""
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(
            context = context,
            packageName = packageName,
            logger = HookLogger,
            sourceMetadata = normalized
        )
        val mediaChanged = activeMediaIdentity?.isCompatibleWith(mediaInfo.identity) == false
        val sourceChanged = activeMediaSourceId != null &&
                activeMediaSourceId != normalized.sourceId
        val repeatedSourceSong = pendingRepeatedSourceSong
        pendingRepeatedSourceSong = null
        if (repeatedSourceSong != null && (mediaChanged || sourceChanged)) {
            HookLogger.i(
                TAG,
                "plugin_request event=request_cancelled reason=source_media_identity_changed"
            )
            cancelPendingPluginStart()
            invalidatePluginRequest(reason = "source_media_identity_changed")
            activeMediaIdentity = null
            activeMediaSourceId = null
            latestPluginMediaInfo = null
            LyriconDataBridge.updateSong(
                song = repeatedSourceSong,
                placeholderFormat = prefs?.getInt(
                    RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT,
                    RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
                ) ?: RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
            )
            renderer.updateLyricLine()
        }
        LyriconDataBridge.applyResolvedMediaInfo(mediaInfo)
        latestPluginMediaInfo = mediaInfo.toPluginMediaInfo(sourcePackageName)
        if (mediaChanged && LyriconDataBridge.currentSong == null) {
            LyriconDataBridge.resetLyricContentForMediaChange()
            renderer.updateLyricLine()
        }
        activeMediaIdentity = mediaInfo.identity
        activeMediaSourceId = normalized.sourceId
        LyriconDataBridge.currentSongName = LyriconDataBridge.currentSong?.name
            ?.takeIf { it.isNotBlank() }
            ?: mediaInfo.title.takeIf { it.isNotBlank() }
        invalidateIfPluginInputChanged()
        schedulePluginProcessing()
        handleColorBindingUpdate(colorBindingUpdate, reason = "metadata_changed")
        renderer.updateMetadata()
        scheduleArtworkColorRefresh()
    }

    /** Coalesces the synchronous onSongChanged -> onMetadata event chain on the main handler. */
    private fun schedulePluginProcessing() {
        if (pluginRuntime == null || pluginStartScheduled) return
        pluginStartScheduled = true
        mainHandler.post(pluginStartRunnable)
    }

    /** Starts one plugin pass for the current Core-owned Song snapshot. */
    private fun startPluginProcessing() {
        val baseSong = LyriconDataBridge.currentSong ?: return
        val expectedVersion = LyriconDataBridge.versionCounter.get()
        val requestKey = currentPluginRequestKey() ?: return
        if (activePluginRequestKey != null && activePluginRequestKey != requestKey) {
            invalidatePluginRequest(reason = "effective_input_changed")
        }
        if (pluginRequestTracker.isDuplicate(requestKey)) {
            HookLogger.i(
                TAG,
                "plugin_request event=request_deduplicated " +
                        "fingerprint=${requestKey.hashCode()}"
            )
            return
        }
        pluginRequestTracker.markStarted(requestKey)
        activePluginRequestKey = requestKey
        val expectedRequest = pluginRequestGeneration.incrementAndGet()
        val pluginSnapshot = PluginSongMapper.toPluginSong(baseSong.deepCopy())
        val processingMediaInfo = latestPluginMediaInfo
        HookLogger.i(
            TAG,
            "plugin_request event=request_started " +
                    "fingerprint=${requestKey.hashCode()} " +
                    "generation=${expectedRequest}"
        )
        pluginRuntime?.processSong(
            song = pluginSnapshot,
            processingContext = PluginProcessingContext(mediaInfo = processingMediaInfo)
        ) { processingResult: PluginProcessingResult? ->
            mainHandler.post {
                if (pluginRequestGeneration.get() != expectedRequest) {
                    logStalePluginResult(requestKey, "generation_changed")
                    return@post
                }
                if (LyriconDataBridge.versionCounter.get() != expectedVersion ||
                    LyriconDataBridge.currentSong !== baseSong
                ) {
                    logStalePluginResult(requestKey, "song_snapshot_changed")
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    return@post
                }
                if (processingResult == null) {
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    HookLogger.i(
                        TAG,
                        "plugin_request event=request_completed status=no_result " +
                                "fingerprint=${requestKey.hashCode()}"
                    )
                    return@post
                }
                val enhancedSong = PluginSongMapper.toInternalSong(
                    base = baseSong,
                    result = processingResult.result
                ) ?: run {
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    HookLogger.i(
                        TAG,
                        "plugin_request event=request_completed status=invalid_result " +
                                "fingerprint=${requestKey.hashCode()}"
                    )
                    return@post
                }
                if (LyriconDataBridge.applyPluginEnhancement(
                        enhancedSong = enhancedSong,
                        expectedVersion = expectedVersion,
                        expectedBaseSong = baseSong,
                        changedFields = processingResult.result.changedFields
                    )
                ) {
                    val changedFields = processingResult.result.changedFields
                    if (changedFields.any { it in MEDIA_SONG_FIELDS }) {
                        renderer.updateMetadata()
                    }
                    if (changedFields.any { it in LYRIC_RENDER_FIELDS }) {
                        renderer.updateLyricLine()
                    }
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                    HookLogger.i(
                        TAG,
                        "plugin_request event=request_completed status=applied " +
                                "fingerprint=${requestKey.hashCode()}"
                    )
                } else {
                    logStalePluginResult(requestKey, "writeback_rejected")
                    if (activePluginRequestKey == requestKey) activePluginRequestKey = null
                }
            }
        }
    }

    private fun currentPluginRequestKey(): PluginProcessingRequestKey? {
        val song = sourcePluginSong ?: LyriconDataBridge.currentSong
            ?.deepCopy()
            ?.let(PluginSongMapper::toPluginSong)
            ?: return null
        return PluginProcessingRequestKey(
            sourceSong = song,
            mediaIdentity = activeMediaIdentity?.normalized(),
            mediaInfo = latestPluginMediaInfo,
            processorSetFingerprint = pluginRuntime?.processingSetFingerprint().orEmpty()
        )
    }

    private fun invalidateIfPluginInputChanged() {
        val active = activePluginRequestKey ?: return
        val current = currentPluginRequestKey() ?: return
        if (active != current) {
            invalidatePluginRequest(reason = "effective_input_changed")
        }
    }

    private fun invalidatePluginRequest(reason: String) {
        val active = activePluginRequestKey
        if (active != null) {
            HookLogger.i(
                TAG,
                "plugin_request event=request_cancelled reason=${reason} " +
                        "fingerprint=${active.hashCode()}"
            )
        }
        activePluginRequestKey = null
        pluginRequestTracker.reset()
        pluginRequestGeneration.incrementAndGet()
        pluginRuntime?.cancelActiveProcessing()
    }

    private fun cancelPendingPluginStart() {
        mainHandler.removeCallbacks(pluginStartRunnable)
        pluginStartScheduled = false
    }

    private fun logStalePluginResult(
        requestKey: PluginProcessingRequestKey,
        reason: String
    ) {
        HookLogger.i(
            TAG,
            "plugin_request event=stale_result_ignored reason=${reason} " +
                    "fingerprint=${requestKey.hashCode()}"
        )
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float) {
        playbackActive = isPlaying
        explicitPlaybackSpeed(playbackSpeed)?.let { currentPlaybackSpeed = it }
        if (!isPlaying) cancelPendingPositionDispatch()
        if (isPlaying) {
            handleColorBindingUpdate(
                update = LyricColorBindingCoordinator.onPlaybackStarted(context),
                reason = "playback_started"
            )
            scheduleArtworkColorRefresh()
        }
        renderer.onPlaybackStateChanged(isPlaying)
    }

    override fun onPositionChanged(position: Long, playbackSpeed: Float) {
        val now = SystemClock.uptimeMillis()
        val resolvedSpeed = resolvePlaybackSpeed(position, playbackSpeed, now)
        if (position == lastReceivedPosition &&
            abs(resolvedSpeed - lastReceivedPlaybackSpeed) < SPEED_CHANGE_EPSILON
        ) return
        lastReceivedPosition = position
        lastReceivedPositionTimeMs = now
        lastReceivedPlaybackSpeed = resolvedSpeed
        val lyricChanged = LyriconDataBridge.updatePosition(position)
        if (lyricChanged) {
            renderer.updateLyricLine()
        }
        val sample = PositionSample(position, resolvedSpeed)
        if (playbackActive) {
            dispatchPositionThrottled(sample, now)
        } else {
            dispatchPosition(sample, now)
        }
    }

    private fun dispatchPositionThrottled(position: PositionSample, now: Long) {
        val elapsed = now - lastPositionDispatchTimeMs
        if (elapsed >= MIN_POSITION_DISPATCH_INTERVAL_MS) {
            dispatchPosition(position, now)
            return
        }

        pendingPosition = position
        if (positionDispatchScheduled) return

        positionDispatchScheduled = true
        mainHandler.postDelayed(
            positionDispatchRunnable,
            MIN_POSITION_DISPATCH_INTERVAL_MS - elapsed
        )
    }

    private fun dispatchPosition(
        sample: PositionSample,
        now: Long = SystemClock.uptimeMillis()
    ) {
        if (sample.position == lastDispatchedPosition &&
            abs(sample.playbackSpeed - lastDispatchedPlaybackSpeed) < SPEED_CHANGE_EPSILON
        ) return
        lastPositionDispatchTimeMs = now
        lastDispatchedPosition = sample.position
        lastDispatchedPlaybackSpeed = sample.playbackSpeed
        pendingPosition = null
        renderer.updatePosition(sample.position, sample.playbackSpeed)
    }

    private fun cancelPendingPositionDispatch() {
        mainHandler.removeCallbacks(positionDispatchRunnable)
        pendingPosition = null
        positionDispatchScheduled = false
    }

    private fun resolvePlaybackSpeed(position: Long, reportedSpeed: Float, now: Long): Float {
        explicitPlaybackSpeed(reportedSpeed)?.let {
            currentPlaybackSpeed = it
            return it
        }

        if (playbackActive && lastReceivedPosition != Long.MIN_VALUE &&
            lastReceivedPositionTimeMs > 0L && now > lastReceivedPositionTimeMs &&
            position >= lastReceivedPosition
        ) {
            val elapsedMs = now - lastReceivedPositionTimeMs
            val inferred = (position - lastReceivedPosition).toFloat() / elapsedMs.toFloat()
            if (inferred in MIN_VALID_PLAYBACK_SPEED..MAX_VALID_PLAYBACK_SPEED) {
                currentPlaybackSpeed = currentPlaybackSpeed * (1f - INFERRED_SPEED_BLEND) +
                        inferred * INFERRED_SPEED_BLEND
            }
        }
        return currentPlaybackSpeed
    }

    private fun explicitPlaybackSpeed(speed: Float): Float? =
        speed.takeIf { it.isFinite() && it in MIN_VALID_PLAYBACK_SPEED..MAX_VALID_PLAYBACK_SPEED }

    private fun handleColorBindingUpdate(
        update: LyricColorBindingUpdate,
        reason: String
    ) {
        val colorSession = update.colorSession
        if (colorSession == null) {
            HookLogger.dState(
                stateId = "RootLyricSink.colorBinding",
                tag = TAG,
                state = "pending|$reason|${update.reason}"
            ) {
                "颜色绑定未就绪: trigger=$reason, reason=${update.reason}"
            }
            if (update.stateChanged) {
                IslandSlotContentFacade.invalidate()
                IslandMusicWaveColorHooker.refresh()
                renderer.updateTextColors()
            }
            return
        }
        HookLogger.dState(
            stateId = "RootLyricSink.colorBinding",
            tag = TAG,
            state = "ready|${colorSession.revision}|${update.reason}"
        ) {
            "颜色绑定已同步: trigger=$reason, selection=${update.reason}, " +
                    "sessionRevision=${colorSession.revision}, " +
                    "package=${colorSession.packageName}, " +
                    "tokenHash=${colorSession.sessionToken.hashCode()}"
        }
        if (update.stateChanged) {
            IslandSlotContentFacade.invalidate()
            IslandMusicWaveColorHooker.refresh()
            renderer.updateTextColors()
        }
    }

    private fun endColorBinding() {
        cancelArtworkColorRefresh()
        if (LyricColorBindingCoordinator.clear()) {
            IslandSlotContentFacade.invalidate()
            IslandMusicWaveColorHooker.refresh()
        }
    }

    private fun scheduleArtworkColorRefresh() {
        mainHandler.removeCallbacks(artworkColorRefreshRunnable)
        mainHandler.postDelayed(
            artworkColorRefreshRunnable,
            ARTWORK_COLOR_REFRESH_DELAY_MS
        )
    }

    private fun cancelArtworkColorRefresh() {
        mainHandler.removeCallbacks(artworkColorRefreshRunnable)
    }

    override fun close() {
        closed = true
        MediaMetadataHelper.removeActiveSessionsObserver(activeSessionsObserver)
        mainHandler.removeCallbacks(sessionBindingRefreshRunnable)
        cancelArtworkColorRefresh()
        cancelPendingPositionDispatch()
        LyricColorBindingCoordinator.clear()
    }

    private fun LyricMediaMetadata.isPackageOnlySnapshot(): Boolean =
        songId == null &&
                title == null &&
                artist == null &&
                album == null &&
                duration == null &&
                sessionToken == null &&
                mediaId == null

    private val MEDIA_SONG_FIELDS = setOf(
        PluginSongField.ID,
        PluginSongField.NAME,
        PluginSongField.ARTIST,
        PluginSongField.ALBUM,
        PluginSongField.DURATION,
        PluginSongField.METADATA
    )

    private val LYRIC_RENDER_FIELDS = setOf(
        PluginSongField.NAME,
        PluginSongField.ARTIST,
        PluginSongField.DURATION,
        PluginSongField.METADATA,
        PluginSongField.LYRICS
    )

}

/**
 * Builds the plugin DTO from resolved Core media fields while retaining the lyric source's package
 * boundary. [sourcePackageName] is intentionally supplied separately so a MediaIdentity package
 * cannot leak into plugins when the source did not provide one.
 */
internal fun MediaMetadataHelper.MediaInfo.toPluginMediaInfo(
    sourcePackageName: String?
): PluginMediaInfo? = PluginMediaInfo(
    title = title.takeIf { it.isNotBlank() },
    artist = artist.takeIf { it.isNotBlank() },
    album = album.takeIf { it.isNotBlank() },
    duration = duration.takeIf { it > 0L },
    sourcePackageName = sourcePackageName?.takeIf { it.isNotBlank() }
).takeIf { info ->
    info.title != null ||
            info.artist != null ||
            info.album != null ||
            info.duration != null ||
            info.sourcePackageName != null
}


