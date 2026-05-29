package com.lidesheng.hyperlyric.common.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import com.lidesheng.hyperlyric.common.HyperLogger

/**
 * 媒体元数据辅助类。
 * 负责从 MediaSession 中提取歌曲信息及封面图片，提供多级兜底逻辑。
 */
object MediaMetadataHelper {

    data class MediaInfo(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val albumArt: Bitmap? = null
    )

    /**
     * 获取指定包名的当前媒体信息
     */
    fun getMediaInfo(context: Context, packageName: String, logger: HyperLogger? = null): MediaInfo {
        if (packageName.isEmpty()) return MediaInfo()

        return try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controllers = mediaSessionManager.getActiveSessions(null)
            val controller = controllers.find { it.packageName == packageName }
            
            controller?.metadata?.let { metadata ->
                MediaInfo(
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                    albumArt = metadata.extractAlbumArt()
                )
            } ?: MediaInfo()
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
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controller = msm.getActiveSessions(null).find { it.packageName == packageName }
            controller?.playbackState?.position ?: -1
        } catch (_: Exception) {
            -1
        }
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
}
