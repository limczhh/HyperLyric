package com.lidesheng.hyperlyric.root.source

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lidesheng.hyperlyric.common.RootConstants
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
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementController
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCoordinator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import kotlin.math.abs

internal class RootLyricSink(
    private val renderer: IslandRenderer,
    private val context: Context,
    private val prefs: SharedPreferences? = null,
    private val lyricEnhancementCoordinator: LyricEnhancementCoordinator? = null
) : LyricSink, AutoCloseable {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lyricEnhancementController = LyricEnhancementController(
        renderer = renderer,
        mainHandler = mainHandler,
        coordinator = lyricEnhancementCoordinator,
    )
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
    private val artworkColorRefreshRunnable = Runnable {
        if (closed) return@Runnable
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
    private val positionDispatchRunnable = Runnable {
        if (closed) return@Runnable
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
        if (closed) return
        cancelArtworkColorRefresh()
        cancelPendingPositionDispatch()
        lastReceivedPosition = Long.MIN_VALUE
        lastReceivedPositionTimeMs = 0L
        lastReceivedPlaybackSpeed = Float.NaN
        lastDispatchedPosition = Long.MIN_VALUE
        lastDispatchedPlaybackSpeed = Float.NaN
        currentPlaybackSpeed = 1f
        val ownedSong = song?.deepCopy()
        val repeatedSourceEvent = lyricEnhancementController.onSongChanged(ownedSong)
        if (repeatedSourceEvent) {
            // A source snapshot may be repeated for a replay or callback refresh. Keep the Core
            // state path alive so timing/current-line state is reset, but do not throw away an
            // accepted enhancement before the following metadata event identifies the media item.
            // If that identity changes, onMetadata commits the raw Song below.
            LyriconDataBridge.refreshSongEvent()
            renderer.updateLyricLine()
            return
        }

        // A different song must never inherit the previous song's extrapolated renderer clock.
        // The first position callback will replace this zero anchor with the source sample.
        LyriconDataBridge.resetPlaybackClock(
            isPlaying = playbackActive,
            playbackSpeed = currentPlaybackSpeed
        )
        LyriconDataBridge.updateSong(
            song = ownedSong,
            placeholderFormat = prefs?.getInt(
                RootConstants.KEY_HOOK_PLACEHOLDER_FORMAT,
                RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
            ) ?: RootConstants.DEFAULT_HOOK_PLACEHOLDER_FORMAT
        )
        if (song == null) {
            lyricEnhancementController.onSongCommitted(hasSong = false)
        } else {
            lyricEnhancementController.onSongCommitted(hasSong = true)
        }
        if (song == null) {
            endColorBinding()
        }
    }

    override fun onLyricLine(line: IRichLyricLine) {
        if (closed) return
        LyriconDataBridge.updateLyricLine(line)
        renderer.updateLyricLine()
    }

    override fun onPlainText(text: String?) {
        if (closed) return
        LyriconDataBridge.updateLyric(text)
        renderer.updateLyricLine()
    }

    override fun onStop() {
        if (closed) return
        cancelArtworkColorRefresh()
        playbackActive = false
        cancelPendingPositionDispatch()
        lastReceivedPosition = Long.MIN_VALUE
        lastReceivedPositionTimeMs = 0L
        lastReceivedPlaybackSpeed = Float.NaN
        lastDispatchedPosition = Long.MIN_VALUE
        lastDispatchedPlaybackSpeed = Float.NaN
        currentPlaybackSpeed = 1f
        lyricEnhancementController.onStop()
        endColorBinding()
        renderer.clearAllViews()
        LyriconDataBridge.clearState()
    }

    override fun onMetadata(metadata: LyricMediaMetadata?) {
        if (closed) return
        val normalized = metadata?.normalized()
        normalized?.packageName?.let(LyriconDataBridge::updateLyricPackage)
        if (normalized == null) {
            LyriconDataBridge.updateMediaMetadata(null)
            lyricEnhancementController.onMetadataCleared()
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
        // enhancement may only receive the package supplied by this lyric source event, never a
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
        val mediaTransition = lyricEnhancementController.beforeResolvedMediaInfo(
            mediaIdentity = mediaInfo.identity,
            sourceId = normalized.sourceId,
        )
        mediaTransition.repeatedSourceSong?.let { repeatedSourceSong ->
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
        if (mediaTransition.mediaChanged && LyriconDataBridge.currentSong == null) {
            LyriconDataBridge.resetLyricContentForMediaChange()
            renderer.updateLyricLine()
        }
        LyriconDataBridge.currentSongName = LyriconDataBridge.currentSong?.name
            ?.takeIf { it.isNotBlank() }
            ?: mediaInfo.title.takeIf { it.isNotBlank() }
        lyricEnhancementController.afterResolvedMediaInfo(
            mediaInfo = mediaInfo,
            sourcePackageName = sourcePackageName,
            sourceId = normalized.sourceId,
        )
        handleColorBindingUpdate(colorBindingUpdate, reason = "metadata_changed")
        renderer.updateMetadata()
        scheduleArtworkColorRefresh()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float) {
        if (closed) return
        playbackActive = isPlaying
        explicitPlaybackSpeed(playbackSpeed)?.let { currentPlaybackSpeed = it }
        LyriconDataBridge.updatePlaybackState(
            isPlaying = isPlaying,
            playbackSpeed = currentPlaybackSpeed
        )
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
        if (closed) return
        val now = SystemClock.uptimeMillis()
        val resolvedSpeed = resolvePlaybackSpeed(position, playbackSpeed, now)
        if (position == lastReceivedPosition &&
            abs(resolvedSpeed - lastReceivedPlaybackSpeed) < SPEED_CHANGE_EPSILON
        ) return
        lastReceivedPosition = position
        lastReceivedPositionTimeMs = now
        lastReceivedPlaybackSpeed = resolvedSpeed
        LyriconDataBridge.updatePlaybackClock(
            positionMs = position,
            playbackSpeed = resolvedSpeed,
            isPlaying = playbackActive,
            sampledAtUptimeMs = now
        )
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
        if (closed) return
        if (sample.position == lastDispatchedPosition &&
            abs(sample.playbackSpeed - lastDispatchedPlaybackSpeed) < SPEED_CHANGE_EPSILON
        ) return
        lastPositionDispatchTimeMs = now
        lastDispatchedPosition = sample.position
        lastDispatchedPlaybackSpeed = sample.playbackSpeed
        pendingPosition = null
        val playbackClock = LyriconDataBridge.currentPlaybackClock(now)
        renderer.updatePosition(playbackClock.positionMs, playbackClock.playbackSpeed)
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
        lyricEnhancementController.close()
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

}


