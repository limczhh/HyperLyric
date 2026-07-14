package com.lidesheng.hyperlyric.common.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lidesheng.hyperlyric.common.HyperLogger

/**
 * 媒体元数据辅助类。
 * 负责从 MediaSession 中提取歌曲信息及封面图片，提供多级兜底逻辑。
 */
object MediaMetadataHelper {

    private val sessionLock = Any()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var mediaSessionManager: MediaSessionManager? = null

    @Volatile
    private var activeControllers: List<MediaController> = emptyList()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        activeControllers = controllers.orEmpty()
    }

    data class MediaInfo(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArt: Bitmap? = null,
        val duration: Long = -1L
    )

    data class PlaybackProgress(
        val position: Long = -1L,
        val duration: Long = -1L,
        val isPlaying: Boolean = false,
        val playbackSpeed: Float = 0f
    ) {
        val fraction: Float
            get() = if (position >= 0L && duration > 0L) {
                (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                -1f
            }
    }

    /**
     * 获取指定包名的当前媒体信息
     */
    fun getMediaInfo(context: Context, packageName: String, logger: HyperLogger? = null): MediaInfo {
        if (packageName.isEmpty()) return MediaInfo()

        return try {
            findController(context, packageName)?.metadata?.toMediaInfo() ?: MediaInfo()
        } catch (e: Exception) {
            logger?.e("MediaMetadataHelper", "获取媒体信息失败 ($packageName)", e)
            MediaInfo()
        }
    }

    /**
     * 获取指定包名的当前播放位置（毫秒）。未播放或无控制器时返回 -1。
     */
    fun getPlaybackPosition(context: Context, packageName: String): Long {
        if (packageName.isEmpty()) return -1
        return try {
            estimatePlaybackPosition(findController(context, packageName)?.playbackState)
        } catch (_: Exception) {
            -1
        }
    }

    fun getPlaybackProgress(context: Context, packageName: String): PlaybackProgress {
        if (packageName.isEmpty()) return PlaybackProgress()
        return try {
            val controller = findController(context, packageName) ?: return PlaybackProgress()
            val state = controller.playbackState
            val duration = controller.metadata?.extractDuration() ?: -1L
            PlaybackProgress(
                position = estimatePlaybackPosition(state),
                duration = duration,
                isPlaying = state?.state == PlaybackState.STATE_PLAYING,
                playbackSpeed = state?.playbackSpeed ?: 0f
            )
        } catch (_: Exception) {
            PlaybackProgress()
        }
    }

    /**
     * 通过系统 MediaSession 判断指定包名是否正在播放。
     */
    fun isPackagePlaying(context: Context, packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return try {
            ensureSessionSnapshot(context)
            activeControllers.any {
                it.packageName == packageName && it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
        } catch (_: Exception) {
            false
        }
    }

    fun estimatePlaybackPosition(state: PlaybackState?): Long {
        state ?: return -1L
        val basePosition = state.position
        if (basePosition < 0L) return -1L
        if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0L) {
            return basePosition
        }
        val elapsed = (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
        return (basePosition + elapsed * state.playbackSpeed).toLong().coerceAtLeast(0L)
    }

    private fun findController(context: Context, packageName: String): MediaController? {
        ensureSessionSnapshot(context)
        selectController(activeControllers, packageName)?.let { return it }
        refreshSessionSnapshot()
        return selectController(activeControllers, packageName)
    }

    private fun selectController(
        controllers: List<MediaController>,
        packageName: String
    ): MediaController? {
        var latestController: MediaController? = null
        var latestUpdateTime = Long.MIN_VALUE
        controllers.forEach { controller ->
            if (controller.packageName != packageName) return@forEach
            val state = controller.playbackState
            if (state?.state == PlaybackState.STATE_PLAYING) return controller
            val updateTime = state?.lastPositionUpdateTime ?: 0L
            if (latestController == null || updateTime > latestUpdateTime) {
                latestController = controller
                latestUpdateTime = updateTime
            }
        }
        return latestController
    }

    private fun ensureSessionSnapshot(context: Context) {
        if (mediaSessionManager != null) return
        synchronized(sessionLock) {
            if (mediaSessionManager != null) return
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager = manager
            activeControllers = runCatching { manager.getActiveSessions(null) }.getOrDefault(emptyList())
            val registerListener: () -> Unit = {
                runCatching {
                    manager.addOnActiveSessionsChangedListener(activeSessionsListener, null)
                }
                Unit
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                registerListener()
            } else {
                mainHandler.post(registerListener)
            }
        }
    }

    private fun refreshSessionSnapshot() {
        val manager = mediaSessionManager ?: return
        activeControllers = runCatching { manager.getActiveSessions(null) }.getOrDefault(activeControllers)
    }

    /**
     * 扩展方法：多级兜底提取封面
     * 优先级：ALBUM_ART > ART > DISPLAY_ICON
     */
    private fun MediaMetadata.extractAlbumArt(): Bitmap? {
        return try {
            getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        } catch (_: Exception) {
            null
        }
    }

    private fun MediaMetadata.toMediaInfo(): MediaInfo {
        val mediaDescription = description
        return MediaInfo(
            title = mediaDescription.title?.toString().orEmpty(),
            artist = mediaDescription.subtitle?.toString().orEmpty(),
            album = mediaDescription.description?.toString().orEmpty(),
            albumArt = extractAlbumArt(),
            duration = extractDuration()
        )
    }

    private fun MediaMetadata.extractDuration(): Long {
        return try {
            getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0L } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

}
