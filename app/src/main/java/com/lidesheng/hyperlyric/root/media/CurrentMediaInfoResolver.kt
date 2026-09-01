package com.lidesheng.hyperlyric.root.media

import android.content.Context
import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper

/**
 * Resolves the media snapshot consumed by root-side surfaces.
 *
 * Source-provided display fields are preferred individually. A source-owned color session pins
 * MediaSession reads without letting this resolver create or change that ownership.
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
        val colorSession = CoverColorHelper.currentSession(normalizedPackageName)
        val sourcePackage = sourceInfo?.packageName
        val sourcePackageMatches = sourcePackage.isNullOrEmpty() ||
                sourcePackage == normalizedPackageName
        val sessionInfo = MediaMetadataHelper.getMediaInfo(
            context = context,
            packageName = normalizedPackageName,
            logger = logger,
            preferredSessionToken = sourceInfo?.sessionToken ?: colorSession?.sessionToken
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
            packageName = normalizedPackageName,
            confirmedSessionToken = colorSession?.sessionToken
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
     * the source-owned pinned token or an identity supplied by the source; otherwise retain the
     * conservative album fallback for non-color metadata merging.
     * Title and artist are display metadata and must not participate in ownership matching because
     * players may change them for translations, car Bluetooth displays, or other presentation
     * modes.
     */
    private fun isCurrentSession(
        source: LyricMediaMetadata,
        sessionInfo: MediaMetadataHelper.MediaInfo,
        packageName: String,
        confirmedSessionToken: android.media.session.MediaSession.Token?
    ): Boolean {
        val sessionIdentity = sessionInfo.identity.normalized()
        if (sessionIdentity.packageName != packageName || packageName.isEmpty()) return false

        confirmedSessionToken?.let { token ->
            return sessionIdentity.sessionToken == token
        }

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
            ?: return false
        val sessionAlbum = normalizeText(sessionInfo.album).takeIf { it.isNotEmpty() }
            ?: return false
        return sourceAlbum.equals(sessionAlbum, ignoreCase = true)
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
