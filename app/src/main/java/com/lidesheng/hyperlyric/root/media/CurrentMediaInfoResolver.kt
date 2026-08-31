package com.lidesheng.hyperlyric.root.media

import android.content.Context
import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.root.LyriconDataBridge

/**
 * Resolves the media snapshot consumed by root-side surfaces.
 *
 * Source-provided fields are preferred individually. MediaSession remains the fallback for
 * fields a lyric source does not expose only after the source and controller are confirmed to
 * describe the same current media item.
 */
internal object CurrentMediaInfoResolver {

    fun getMediaInfo(
        context: Context,
        packageName: String,
        logger: HyperLogger? = null,
        sourceMetadata: LyricMediaMetadata? = null
    ): MediaMetadataHelper.MediaInfo {
        val normalizedPackageName = packageName.trim()
        val sourceInfo = (sourceMetadata ?: LyriconDataBridge.currentLyricMediaMetadata)
            ?.normalized()
        val sourcePackage = sourceInfo?.packageName
        val sourcePackageMatches = sourcePackage.isNullOrEmpty() ||
                sourcePackage == normalizedPackageName
        val sessionInfo = MediaMetadataHelper.getMediaInfo(
            context = context,
            packageName = normalizedPackageName,
            logger = logger,
            preferredSessionToken = sourceInfo?.sessionToken
        )

        if (sourceInfo == null) return normalize(sessionInfo)

        // Keep a mismatched source snapshot authoritative for its own event. Do not let the
        // requested package's MediaSession leak into it merely because the source package was
        // different.
        if (!sourcePackageMatches) {
            return MediaMetadataHelper.MediaInfo(
                title = sourceInfo.title.orEmpty(),
                artist = sourceInfo.artist.orEmpty(),
                album = sourceInfo.album.orEmpty(),
                duration = sourceInfo.duration ?: -1L,
                identity = sourceInfo.toIdentity()
            )
        }

        val sessionMatches = isCurrentSession(
            source = sourceInfo,
            sessionInfo = sessionInfo,
            packageName = normalizedPackageName
        )
        val fallback = if (sessionMatches) {
            sessionInfo
        } else {
            MediaMetadataHelper.MediaInfo(
                identity = sourceInfo.toIdentity(normalizedPackageName)
            )
        }

        return fallback.copy(
            title = sourceInfo.title ?: normalizeText(fallback.title),
            artist = sourceInfo.artist ?: normalizeText(fallback.artist),
            album = sourceInfo.album ?: normalizeText(fallback.album),
            duration = sourceInfo.duration ?: fallback.duration,
            identity = if (sessionMatches) {
                sourceInfo.toIdentity(normalizedPackageName)
                    .fillMissingFrom(sessionInfo.identity)
            } else {
                sourceInfo.toIdentity(normalizedPackageName)
            }
        )
    }

    /**
     * A package name alone is not proof that the MediaSession belongs to the lyric event. Prefer
     * an exact token or media id; otherwise require the stable album fallback to match.
     * Title and artist are display metadata and must not participate in ownership matching because
     * players may change them for translations, car Bluetooth displays, or other presentation
     * modes — except when the lyric source provides no album at all (e.g. 酷狗 via Lyricon):
     * in that case the strongest remaining evidence is title+artist, and matching on them is
     * still safer than dropping the MediaSession album art entirely.
     */
    private fun isCurrentSession(
        source: LyricMediaMetadata,
        sessionInfo: MediaMetadataHelper.MediaInfo,
        packageName: String
    ): Boolean {
        val sessionIdentity = sessionInfo.identity.normalized()
        if (sessionIdentity.packageName != packageName || packageName.isEmpty()) return false

        source.sessionToken?.let { token ->
            if (sessionIdentity.sessionToken != token) return false
            source.mediaId?.let { mediaId ->
                sessionIdentity.mediaId?.let { currentMediaId ->
                    return currentMediaId == mediaId
                }
            }
            return true
        }
        source.mediaId?.let { mediaId ->
            if (sessionIdentity.mediaId != null) {
                return sessionIdentity.mediaId == mediaId
            }
        }

        val sourceAlbum = source.album?.let(::normalizeText)?.takeIf { it.isNotEmpty() }
        if (sourceAlbum != null) {
            val sessionAlbum = normalizeText(sessionInfo.album).takeIf { it.isNotEmpty() }
                ?: return false
            return sourceAlbum.equals(sessionAlbum, ignoreCase = true)
        }

        // The lyric source exposes no album: fall back to matching both title and artist so the
        // MediaSession album art can still be merged in. Both fields must agree; a partial or
        // empty match is treated as unknown.
        val sourceTitle = source.title?.let(::normalizeText)?.takeIf { it.isNotEmpty() }
            ?: return false
        val sourceArtist = source.artist?.let(::normalizeText)?.takeIf { it.isNotEmpty() }
            ?: return false
        val sessionTitle = normalizeText(sessionInfo.title).takeIf { it.isNotEmpty() }
            ?: return false
        val sessionArtist = normalizeText(sessionInfo.artist).takeIf { it.isNotEmpty() }
            ?: return false
        return sourceTitle.equals(sessionTitle, ignoreCase = true) &&
                sourceArtist.equals(sessionArtist, ignoreCase = true)
    }

    private fun normalize(info: MediaMetadataHelper.MediaInfo): MediaMetadataHelper.MediaInfo =
        info.copy(
            title = normalizeText(info.title),
            artist = normalizeText(info.artist),
            album = normalizeText(info.album),
            identity = info.identity.normalized()
        )

    private fun normalizeText(value: String): String = value
        .replace(WHITESPACE, " ")
        .trim()

    private val WHITESPACE = Regex("\\s+")
}
