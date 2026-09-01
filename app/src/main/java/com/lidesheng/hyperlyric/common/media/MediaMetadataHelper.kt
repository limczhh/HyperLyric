package com.lidesheng.hyperlyric.common.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lidesheng.hyperlyric.common.HyperLogger
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 媒体元数据辅助类。
 * 负责从 MediaSession 中提取歌曲信息及封面图片，提供多级兜底逻辑。
 */
object MediaMetadataHelper {

    private val sessionLock = Any()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val debugStates = HashMap<String, String>()

    @Volatile
    private var mediaSessionManager: MediaSessionManager? = null

    @Volatile
    private var activeControllers: List<MediaController> = emptyList()

    private val activeSessionsObservers = CopyOnWriteArraySet<() -> Unit>()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        activeControllers = controllers.orEmpty()
        activeSessionsObservers.forEach { observer -> runCatching { observer() } }
    }

    internal fun addActiveSessionsObserver(observer: () -> Unit) {
        activeSessionsObservers.add(observer)
    }

    internal fun removeActiveSessionsObserver(observer: () -> Unit) {
        activeSessionsObservers.remove(observer)
    }

    /** A media snapshot assembled from one controller. */
    data class MediaInfo(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArt: Bitmap? = null,
        val artworkSource: String = "none",
        val duration: Long = -1L,
        val identity: MediaIdentity = MediaIdentity()
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
    fun getMediaInfo(
        context: Context,
        packageName: String,
        logger: HyperLogger? = null,
        preferredSessionToken: MediaSession.Token? = null
    ): MediaInfo {
        if (packageName.isEmpty()) return MediaInfo()

        return try {
            val controller = findController(context, packageName, preferredSessionToken)
            if (controller == null) {
                debug(
                    logger,
                    packageName,
                    "no_controller"
                ) {
                    "媒体信息未读取: package=$packageName, reason=no_matching_controller"
                }
                return MediaInfo(identity = MediaIdentity(packageName = packageName))
            }
            val metadata = controller.metadata
            if (metadata == null) {
                debug(
                    logger,
                    packageName,
                    "no_metadata"
                ) {
                    "媒体信息未读取: package=$packageName, reason=controller_metadata_null"
                }
                return MediaInfo(
                    identity = MediaIdentity(
                        packageName = packageName,
                        sessionToken = controller.sessionToken
                    )
                )
            }
            val mediaInfo = metadata.toMediaInfo().copy(
                identity = MediaIdentity(
                    packageName = packageName,
                    sessionToken = controller.sessionToken,
                    mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                )
            )
            debug(
                logger,
                packageName,
                "ok|${mediaInfo.title.hashCode()}|${mediaInfo.artist.hashCode()}|" +
                        "${mediaInfo.albumArt?.width}x${mediaInfo.albumArt?.height}|${mediaInfo.artworkSource}"
            ) {
                "媒体信息已读取: package=$packageName, title=\"${debugText(mediaInfo.title)}\", " +
                        "artist=\"${debugText(mediaInfo.artist)}\", album=\"${debugText(mediaInfo.album)}\", " +
                        "albumArt=${mediaInfo.albumArt != null}, " +
                        "artworkSource=${mediaInfo.artworkSource}, " +
                        "bitmap=${mediaInfo.albumArt?.width}x${mediaInfo.albumArt?.height}"
            }
            mediaInfo
        } catch (e: Exception) {
            logger?.e("MediaMetadataHelper", "获取媒体信息失败 ($packageName)", e)
            MediaInfo(identity = MediaIdentity(packageName = packageName))
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

    /** Lightweight state used to pin one exact player session. */
    internal data class MediaSessionCandidate(
        val sessionToken: MediaSession.Token,
        val isPlaying: Boolean
    )

    fun getPlaybackProgress(context: Context, packageName: String): PlaybackProgress {
        if (packageName.isEmpty()) return PlaybackProgress()
        return try {
            val controller = findController(context, packageName)
                ?: return PlaybackProgress()
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

    /** Returns all sessions for a package without guessing from controller list order. */
    internal fun getSessionCandidates(
        context: Context,
        packageName: String
    ): List<MediaSessionCandidate> {
        val normalizedPackage = packageName.trim()
        if (normalizedPackage.isEmpty()) return emptyList()
        return try {
            ensureSessionSnapshot(context)
            refreshSessionSnapshot()
            val controllers = activeControllers.filter { it.packageName == normalizedPackage }
            controllers.mapNotNull { controller ->
                runCatching {
                    MediaSessionCandidate(
                        sessionToken = controller.sessionToken,
                        isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                    )
                }.getOrNull()
            }.distinctBy { it.sessionToken }
        } catch (_: Exception) {
            emptyList()
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

    private fun findController(
        context: Context,
        packageName: String,
        preferredSessionToken: MediaSession.Token? = null
    ): MediaController? {
        ensureSessionSnapshot(context)
        selectController(activeControllers, packageName, preferredSessionToken)?.let { return it }
        refreshSessionSnapshot()
        return selectController(activeControllers, packageName, preferredSessionToken)
    }

    private fun selectController(
        controllers: List<MediaController>,
        packageName: String,
        preferredSessionToken: MediaSession.Token? = null
    ): MediaController? {
        if (preferredSessionToken != null) {
            return controllers.firstOrNull {
                it.packageName == packageName && it.sessionToken == preferredSessionToken
            }
        }
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
    private fun MediaMetadata.extractAlbumArt(): ArtworkResult {
        return try {
            getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let {
                ArtworkResult(it, "album_art")
            } ?: getBitmap(MediaMetadata.METADATA_KEY_ART)?.let {
                ArtworkResult(it, "art")
            } ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let {
                ArtworkResult(it, "display_icon")
            } ?: ArtworkResult(null, "none")
        } catch (_: Exception) {
            ArtworkResult(null, "error")
        }
    }

    private fun MediaMetadata.toMediaInfo(): MediaInfo {
        val mediaDescription = description
        val artwork = extractAlbumArt()
        return MediaInfo(
            title = getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: mediaDescription.title?.toString().orEmpty(),
            artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: mediaDescription.subtitle?.toString().orEmpty(),
            album = getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                ?: mediaDescription.description?.toString().orEmpty(),
            albumArt = artwork.bitmap,
            artworkSource = artwork.source,
            duration = extractDuration()
        )
    }

    private data class ArtworkResult(
        val bitmap: Bitmap?,
        val source: String
    )

    private fun debug(
        logger: HyperLogger?,
        packageName: String,
        state: String,
        message: () -> String
    ) {
        if (logger == null) return
        val changed = synchronized(debugStates) {
            if (debugStates[packageName] == state) {
                false
            } else {
                debugStates[packageName] = state
                true
            }
        }
        if (changed) logger.d("MediaMetadataHelper", message())
    }

    private fun MediaMetadata.extractDuration(): Long {
        return try {
            getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0L } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun debugText(value: String): String {
        val normalized = value
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim()
        if (normalized.isEmpty()) return "<empty>"
        return normalized.take(MAX_DEBUG_TEXT_LENGTH).let {
            if (normalized.length > MAX_DEBUG_TEXT_LENGTH) "$it…" else it
        }
    }

    private const val MAX_DEBUG_TEXT_LENGTH = 80

}
