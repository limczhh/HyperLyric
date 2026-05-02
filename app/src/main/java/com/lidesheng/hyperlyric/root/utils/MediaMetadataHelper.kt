package com.lidesheng.hyperlyric.root.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager

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
    fun getMediaInfo(context: Context, packageName: String): MediaInfo {
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
            xLogError("Failed to get media info for $packageName", e)
            MediaInfo()
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
        } catch (e: Exception) {
            null
        }
    }
}
