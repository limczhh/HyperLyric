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

    private var lastLyricHash: Int = 0
    private var hasLyrics: Boolean = false
    private var activePkg: String? = null

    private var positionJob: Job? = null
    private val positionJob_supervisor = SupervisorJob()
    private val positionScope = CoroutineScope(Dispatchers.Main + positionJob_supervisor)

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
        positionJob_supervisor.cancel()
        try { manager.removeOnActiveSessionsChangedListener(sessionListener) } catch (_: Exception) {}
        trackedControllers.forEach { (ctrl, cb) ->
            try { ctrl.unregisterCallback(cb) } catch (_: Exception) {}
        }
        trackedControllers.clear()
        clearLyrics()
        sink?.onStop(); sink = null
    }

    private fun clearLyrics() {
        hasLyrics = false
        lastLyricHash = 0
        activePkg = null
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
                    }
                    override fun onSessionDestroyed() = onActiveSessionsChanged(manager.getActiveSessions(null))
                }
                try { ctrl.registerCallback(cb); trackedControllers[ctrl] = cb; onMetadataUpdate(ctrl) } catch (_: Exception) {}
            }
        }
    }

    /**
     * 纯靠 lyricInfo 判断：有就注入，没有就清理。
     */
    /**
     * 纯靠 lyricInfo 判断：有就注入，没有就清理。
     * 只处理有歌词的包，不同包的 MediaSession 互不干扰。
     */
    private fun onMetadataUpdate(controller: MediaController) {
        val metadata = controller.metadata ?: return
        val pkg = controller.packageName ?: return

        val lyricInfoRaw = try { metadata.getString("lyricInfo") } catch (_: Exception) { null }
        val currentHash = lyricInfoRaw?.hashCode() ?: 0

        if (!lyricInfoRaw.isNullOrBlank() && currentHash != 0) {
            // 有 lyricInfo → 注入（不同包的歌词互相覆盖，以最后更新的为准）
            if (currentHash == lastLyricHash && pkg == activePkg) return

            val songName = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""

            logDiagnosis(lyricInfoRaw)
            val song = LyricInfoParser.parse(lyricInfoRaw, songName, artist)
            if (song != null && !song.lyrics.isNullOrEmpty()) {
                lastLyricHash = currentHash
                hasLyrics = true
                activePkg = pkg
                LyriconDataBridge.activePackageName = pkg
                sink?.onPlaybackStateChanged(true)
                LyriconDataBridge.updateSong(song)
                sink?.onSongChanged(song)
                sink?.onMetadata(title = songName, artist = artist, album = "", publisher = pkg)
                startPositionPolling(pkg)
                HookLogger.d("LyricInfoSource", "歌词就绪: $songName | ${song.lyrics!!.size}行")
            }
        } else if (hasLyrics && pkg == activePkg) {
            // 只清理当前有歌词的包，不影响其他包
            sink?.onStop()
            LyriconDataBridge.clearState()
            clearLyrics()
            HookLogger.d("LyricInfoSource", "歌词消失: $pkg")
        }
    }

    private fun logDiagnosis(json: String) {
        val d = LyricInfoParser.diagnose(json) ?: return
        HookLogger.d("LyricInfoSource", buildString {
            append("  songName   : ${d.songName}\n")
            append("  artist     : ${d.artist}\n")
            append("  songId     : ${d.songId}\n")
            append("  format     : ${d.format}\n")
            append("  translation: ${d.translationFormat}\n")
            append("  lyric      : ${d.lyricLength}chars | ${d.lyricPreview.joinToString(" | ")}")
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
