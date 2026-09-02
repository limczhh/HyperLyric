package com.lidesheng.hyperlyric.root.source

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.session.MediaSessionManager
import com.lidesheng.hyperlyric.common.lyric.LyricInfoParser
import com.lidesheng.hyperlyric.common.lyric.LyricInfoPayload
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LyricInfoSource(private val context: Context) : LyricSource {

    private companion object {
        const val TAG = "LyricInfoSource"
    }

    override val id = "lyricinfo"
    override val displayName = "LyricInfo"

    private val manager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val trackedControllers =
        java.util.concurrent.ConcurrentHashMap<MediaController, MediaController.Callback>()
    private var sink: LyricSink? = null

    private var lastLyricPayload: String? = null
    private var hasLyrics: Boolean = false
    private var activePkg: String? = null
    private var activeController: MediaController? = null
    private var activeTrack: TrackIdentity? = null
    private var lastPayloadSessionToken: MediaSession.Token? = null

    private data class TrackIdentity(
        val mediaId: String?,
        val album: String?
    ) {
        fun isDefinitelyDifferent(other: TrackIdentity): Boolean {
            if (mediaId != null && other.mediaId != null && mediaId != other.mediaId) {
                return true
            }
            if (mediaId == null && other.mediaId == null &&
                album != null && other.album != null &&
                !album.equals(other.album, ignoreCase = true)
            ) {
                return true
            }
            return false
        }
    }

    private var positionJob: Job? = null
    private val positionJob_supervisor = SupervisorJob()
    private val positionScope = CoroutineScope(Dispatchers.Main + positionJob_supervisor)

    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            onActiveSessionsChanged(controllers)
        }

    override fun isAvailable() = true

    override fun start(sink: LyricSink) {
        this.sink = sink
        trackedControllers.clear()
        try {
            manager.addOnActiveSessionsChangedListener(sessionListener, null)
            onActiveSessionsChanged(manager.getActiveSessions(null))
            HookLogger.d(TAG, "数据源已启动")
        } catch (e: Exception) {
            HookLogger.e(TAG, "数据源启动失败", e)
        }
    }

    override fun stop() {
        stopPositionPolling()
        try {
            manager.removeOnActiveSessionsChangedListener(sessionListener)
        } catch (_: Exception) {
        }
        trackedControllers.forEach { (ctrl, cb) ->
            try {
                ctrl.unregisterCallback(cb)
            } catch (_: Exception) {
            }
        }
        trackedControllers.clear()
        clearLyrics()
        sink?.onStop(); sink = null
    }

    private fun clearLyrics() {
        hasLyrics = false
        lastLyricPayload = null
        activePkg = null
        activeController = null
        activeTrack = null
        lastPayloadSessionToken = null
        stopPositionPolling()
    }

    /**
     * Make a playing controller the owner before waiting for its first valid lyricInfo payload.
     * This prevents the previous controller's full-song state from surviving a session switch.
     */
    private fun activateController(controller: MediaController, packageName: String) {
        val sessionChanged = activeController?.sessionToken != controller.sessionToken
        if (sessionChanged && (hasLyrics || activeController != null)) {
            sink?.onStop()
        }
        hasLyrics = false
        lastLyricPayload = null
        activePkg = packageName
        activeController = controller
        activeTrack = null
        lastPayloadSessionToken = null
        stopPositionPolling()
    }

    /** Clear the old song while retaining ownership of the new session/track. */
    private fun resetForTrack(
        controller: MediaController,
        packageName: String,
        track: TrackIdentity
    ) {
        if (hasLyrics) sink?.onStop()
        hasLyrics = false
        lastLyricPayload = null
        activePkg = packageName
        activeController = controller
        activeTrack = track
        lastPayloadSessionToken = null
        stopPositionPolling()
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        if (controllers == null) return
        val currentSessions = controllers.toSet()
        trackedControllers.keys.filter { it !in currentSessions }.forEach { dead ->
            trackedControllers.remove(dead)?.let {
                try {
                    dead.unregisterCallback(it)
                } catch (_: Exception) {
                }
            }
        }
        val activeToken = activeController?.sessionToken
        if (activeToken != null && controllers.none { it.sessionToken == activeToken }) {
            sink?.onStop()
            clearLyrics()
        }
        for (ctrl in controllers) {
            if (!trackedControllers.containsKey(ctrl)) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) =
                        onMetadataUpdate(ctrl, metadataSnapshot = metadata)

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (state?.state == PlaybackState.STATE_PLAYING) {
                            onMetadataUpdate(ctrl, state)
                        } else if (isCurrentController(ctrl)) {
                            handlePlaybackState(ctrl, state)
                        }
                    }

                    override fun onSessionDestroyed() =
                        onActiveSessionsChanged(manager.getActiveSessions(null))
                }
                try {
                    ctrl.registerCallback(cb); trackedControllers[ctrl] = cb; onMetadataUpdate(ctrl)
                } catch (_: Exception) {
                }
            }
        }

    }

    /**
     * 只有当前歌词会话可以继续更新歌词；其他会话必须先进入播放状态才能接管。
     */
    private fun onMetadataUpdate(
        controller: MediaController,
        playbackStateOverride: PlaybackState? = null,
        metadataSnapshot: MediaMetadata? = controller.metadata
    ) {
        val metadata = metadataSnapshot ?: return
        val pkg = controller.packageName ?: return
        val isCurrent = isCurrentController(controller)
        val playbackState = playbackStateOverride ?: controller.playbackState

        // Opening another music app can publish its lyricInfo while it is paused. That
        // metadata must not replace the session that is currently feeding the island.
        if (!isCurrent && playbackState?.state != PlaybackState.STATE_PLAYING) return

        if (!isCurrent && playbackState?.state == PlaybackState.STATE_PLAYING) {
            activateController(controller, pkg)
        }

        val metadataTrack = readTrackIdentity(metadata)
        val previousTrack = activeTrack
        if (previousTrack == null) {
            activeTrack = metadataTrack
        } else if (metadataTrack.isDefinitelyDifferent(previousTrack)) {
            resetForTrack(controller, pkg, metadataTrack)
        } else if (activeController?.sessionToken == controller.sessionToken) {
            activeTrack = mergeTrackIdentity(previousTrack, metadataTrack)
        }

        val lyricInfoRaw = try {
            metadata.getString("lyricInfo")
        } catch (_: Exception) {
            null
        }

        if (!lyricInfoRaw.isNullOrBlank()) {
            val payload = LyricInfoParser.parsePayload(lyricInfoRaw)
            val sameSession = controller.sessionToken == activeController?.sessionToken
            if (lyricInfoRaw == lastLyricPayload && pkg == activePkg && sameSession &&
                controller.sessionToken == lastPayloadSessionToken
            ) {
                if (!isCurrent) {
                    activeController = controller
                    handlePlaybackState(controller, playbackState)
                } else if (playbackStateOverride != null) {
                    handlePlaybackState(controller, playbackState)
                }
                return
            }

            if (HookLogger.isDebugEnabled) {
                logDiagnosis(lyricInfoRaw)
            }
            val song = payload?.song
            if (payload != null && song?.lyrics?.isNullOrEmpty() == false) {
                val mediaMetadata = LyricMediaMetadata(
                    sourceId = id,
                    packageName = pkg,
                    songId = payload.songId,
                    title = payload.title,
                    artist = payload.artist,
                    album = payload.album,
                    duration = runCatching {
                        metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
                    }.getOrNull()?.takeIf { it > 0L },
                    sessionToken = controller.sessionToken,
                    mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                )
                lastLyricPayload = lyricInfoRaw
                hasLyrics = true
                activePkg = pkg
                activeController = controller
                activeTrack = mergeTrackIdentity(
                    metadataTrack,
                    TrackIdentity(mediaId = null, album = payload.album)
                )
                lastPayloadSessionToken = controller.sessionToken
                sink?.onSongChanged(song)
                sink?.onMetadata(mediaMetadata)
                handlePlaybackState(controller, playbackState)
                HookLogger.d(
                    TAG,
                    "歌词已就绪: song=${song.name.orEmpty()}, " +
                            "lines=${song.lyrics!!.size}"
                )
            }
        }
    }

    private fun readTrackIdentity(metadata: MediaMetadata): TrackIdentity {
        fun read(vararg keys: String): String? = keys.asSequence()
            .mapNotNull { key -> runCatching { metadata.getString(key) }.getOrNull() }
            .mapNotNull(::normalizeText)
            .firstOrNull()

        return TrackIdentity(
            mediaId = read(MediaMetadata.METADATA_KEY_MEDIA_ID),
            album = read(
                MediaMetadata.METADATA_KEY_ALBUM,
                MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION
            )
        )
    }

    private fun mergeTrackIdentity(
        preferred: TrackIdentity,
        fallback: TrackIdentity
    ): TrackIdentity = TrackIdentity(
        mediaId = preferred.mediaId ?: fallback.mediaId,
        album = preferred.album ?: fallback.album
    )

    private fun normalizeText(value: String?): String? = value
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun isCurrentController(controller: MediaController): Boolean =
        controller.sessionToken == activeController?.sessionToken

    private fun logDiagnosis(json: String) {
        val diagnosis = LyricInfoParser.diagnose(json) ?: return
        HookLogger.d(
            TAG,
            "songName=${diagnosis.songName} | artist=${diagnosis.artist} | " +
                    "songId=${diagnosis.songId} | " +
                    "rawLyric=${diagnosis.rawLyricLength}chars | " +
                    "lyric=${diagnosis.lyricLength}chars | " +
                    "translation=${diagnosis.translationLength}chars | " +
                    diagnosis.lyricPreview.joinToString(" | ")
        )
    }

    private fun startPositionPolling(controller: MediaController) {
        positionJob?.cancel()
        positionJob = positionScope.launch {
            while (isActive) {
                try {
                    val state = controller.playbackState
                    if (state?.state != PlaybackState.STATE_PLAYING) {
                        dispatchPosition(state)
                        break
                    }
                    val position = MediaMetadataHelper.estimatePlaybackPosition(state)
                    if (position >= 0L && activeController?.sessionToken == controller.sessionToken) {
                        sink?.onPositionChanged(position, state.playbackSpeed)
                    }
                } catch (_: Exception) {
                }
                delay(33)
            }
        }
    }

    private fun handlePlaybackState(controller: MediaController, state: PlaybackState?) {
        if (controller.sessionToken != activeController?.sessionToken) return
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        sink?.onPlaybackStateChanged(isPlaying, state?.playbackSpeed ?: Float.NaN)
        dispatchPosition(state)
        if (isPlaying) {
            startPositionPolling(controller)
        } else {
            stopPositionPolling()
        }
    }

    private fun dispatchPosition(state: PlaybackState?) {
        val position = MediaMetadataHelper.estimatePlaybackPosition(state)
        if (position >= 0L) {
            sink?.onPositionChanged(position, state?.playbackSpeed ?: Float.NaN)
        }
    }

    private fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }
}
