package com.lidesheng.hyperlyric.root.source

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.lidesheng.hyperlyric.common.lyric.LyricInfoParser
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.utils.HookLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LyricInfoSource(private val context: Context) : LyricSource {

    override val id = "lyricinfo"
    override val displayName = "LyricInfo"

    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val trackedControllers = java.util.concurrent.ConcurrentHashMap<MediaController, MediaController.Callback>()
    private var sink: LyricSink? = null

    private var currentSongKey: String? = null
    private var currentPkg: String? = null
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentAlbum: String = ""
    private var metadataSent: Boolean = false  // 是否已向 sink 发送过 metadata

    private val songCache = java.util.concurrent.ConcurrentHashMap<String, Song>()
    private var hasLyrics: Boolean = false

    private var positionJob: Job? = null
    private val positionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onActiveSessionsChanged(controllers)
    }

    override fun isAvailable() = true

    override fun start(sink: LyricSink) {
        this.sink = sink
        trackedControllers.clear()
        try {
            manager.addOnActiveSessionsChangedListener(sessionListener, null)
            onActiveSessionsChanged(manager.getActiveSessions(null))
            HookLogger.i("LyricInfoSource", "已启动")
        } catch (e: Exception) {
            HookLogger.e("LyricInfoSource", "启动失败", e)
        }
    }

    override fun stop() {
        positionJob?.cancel()
        try { manager.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) {}
        trackedControllers.forEach { (ctrl, cb) ->
            try { ctrl.unregisterCallback(cb) } catch (_: Exception) {}
        }
        trackedControllers.clear()
        resetState()
        sink?.onStop(); sink = null
    }

    private fun resetState() {
        currentSongKey = null; currentPkg = null
        currentTitle = ""; currentArtist = ""; currentAlbum = ""
        hasLyrics = false; metadataSent = false
        positionJob?.cancel()
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        if (controllers == null) return
        val currentSessions = controllers.toSet()
        trackedControllers.keys.filter { it !in currentSessions }.forEach { dead ->
            trackedControllers.remove(dead)?.let { try { dead.unregisterCallback(it) } catch (_: Exception) {} }
        }
        for (ctrl in controllers) {
            if (!trackedControllers.containsKey(ctrl)) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = onMetadataUpdate(ctrl)
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        sink?.onPlaybackStateChanged(state?.state == PlaybackState.STATE_PLAYING)
                        onMetadataUpdate(ctrl)
                    }
                    override fun onSessionDestroyed() = onActiveSessionsChanged(manager.getActiveSessions(null))
                }
                try { ctrl.registerCallback(cb); trackedControllers[ctrl] = cb; onMetadataUpdate(ctrl) } catch (_: Exception) {}
            }
        }
    }

    private fun onMetadataUpdate(controller: MediaController) {
        val pkg = controller.packageName ?: return
        val metadata = controller.metadata ?: return
        val fullTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.lines()?.firstOrNull { it.isNotBlank() }?.trim() ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val songKey = "$pkg-$fullTitle-$artist-$album-$duration"

        // 切歌：只记录信息，不通知 sink（等 lyricInfo 就绪后再通知）
        if (songKey != currentSongKey) {
            if (hasLyrics) {
                sink?.onStop()
                LyriconDataBridge.clearState()
            }
            resetState()
            currentSongKey = songKey
            currentPkg = pkg
            currentTitle = if (artist.contains(" - ")) artist.substringAfterLast(" - ").trim() else fullTitle
            currentArtist = if (artist.contains(" - ")) artist.substringBeforeLast(" - ").trim() else artist
            currentAlbum = album
            // 仅设置活跃包名，不发送 metadata（避免超级岛提前出现）
            LyriconDataBridge.activePackageName = pkg
            HookLogger.d("LyricInfoSource", "切歌: $currentTitle - $currentArtist")
        }

        // 尝试读取 lyricInfo
        val lyricInfoRaw = try { metadata.getString("lyricInfo") } catch (_: Exception) { null }

        if (!lyricInfoRaw.isNullOrBlank()) {
            val cachedSong = songCache[songKey]
            if (cachedSong != null) {
                // 已缓存 → 直接复用
                if (!hasLyrics) {
                    hasLyrics = true
                    LyriconDataBridge.updateSong(cachedSong)
                    sink?.onSongChanged(cachedSong)
                    sink?.onMetadata(title = currentTitle, artist = currentArtist, album = currentAlbum, publisher = pkg)
                    startPositionPolling(pkg)
                    HookLogger.d("LyricInfoSource", "歌词恢复: $currentTitle, ${cachedSong.lyrics?.size}行")
                }
            } else {
                // 首次 → 解析
                logDiagnosis(lyricInfoRaw)
                val song = LyricInfoParser.parse(lyricInfoRaw, currentTitle, currentArtist)
                if (song != null && !song.lyrics.isNullOrEmpty()) {
                    songCache[songKey] = song
                    hasLyrics = true
                    LyriconDataBridge.updateSong(song)
                    sink?.onSongChanged(song)
                    sink?.onMetadata(title = currentTitle, artist = currentArtist, album = currentAlbum, publisher = pkg)
                    startPositionPolling(pkg)
                    HookLogger.d("LyricInfoSource", "歌词就绪: $currentTitle, ${song.lyrics?.size}行")
                } else {
                    HookLogger.d("LyricInfoSource", "lyricInfo 解析为空: $currentTitle")
                }
            }
        }
        // 无 lyricInfo → 不调用 sink，超级岛不出现
    }

    private fun logDiagnosis(json: String) {
        val d = LyricInfoParser.diagnose(json) ?: return
        HookLogger.d("LyricInfoSource", buildString {
            append("  songName   : ${d.songName}\n")
            append("  artist     : ${d.artist}\n")
            append("  songId     : ${d.songId}\n")
            append("  lyric      : ${d.lyricLength}chars | ${d.lyricPreview.joinToString(" | ")}\n")
            append("  lyricWord  : ${d.lyricWordLength}chars | ${d.lyricWordPreview.joinToString(" | ")}\n")
            append("  translation: ${d.translationLength}chars | ${d.translationPreview.joinToString(" | ")}")
        })
    }

    private fun startPositionPolling(pkg: String) {
        positionJob?.cancel()
        positionJob = positionScope.launch {
            var lastKnownPos = 0L
            var lastPollTime = System.currentTimeMillis()
            while (isActive) {
                try {
                    val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                    val ctrl = msm.getActiveSessions(null).find { it.packageName == pkg }
                    val state = ctrl?.playbackState
                    if (state != null) {
                        val statePos = state.position
                        if (statePos != lastKnownPos) {
                            lastKnownPos = statePos; lastPollTime = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            lastKnownPos += now - lastPollTime; lastPollTime = now
                        }
                        sink?.onPositionChanged(lastKnownPos)
                    }
                } catch (_: Exception) {}
                delay(30)
            }
        }
    }
}
