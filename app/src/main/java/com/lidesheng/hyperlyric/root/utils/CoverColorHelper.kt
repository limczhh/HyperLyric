package com.lidesheng.hyperlyric.root.utils

import android.graphics.Bitmap
import android.media.session.MediaSession
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import kotlin.math.abs

object CoverColorHelper {
    private const val TAG = "CoverColorHelper"

    /** A source-owned lease authorizing artwork from one exact MediaSession. */
    class ColorSession internal constructor(
        val revision: Long,
        internal val packageName: String,
        internal val sessionToken: MediaSession.Token
    ) {
        /** Stable diagnostic token for consumers that need to invalidate animations. */
        val mediaKey: String
            get() = "$packageName\u001Fsession\u001F${sessionToken.hashCode()}"
    }

    class ArtworkRequest internal constructor(
        val colorSession: ColorSession,
        val revision: Long,
        internal val cacheKey: ArtworkCacheKey,
        internal val fingerprint: ArtworkFingerprint
    )

    private data class CacheEntry(
        val artworkFingerprint: ArtworkFingerprint,
        val colors: Pair<IntArray, IntArray>
    )

    internal data class ArtworkCacheKey(
        val packageName: String,
        val sessionToken: MediaSession.Token,
        val songId: String?,
        val mediaId: String?
    )

    internal class ArtworkFingerprint(
        val pixels: IntArray
    ) {
        fun isSimilarTo(other: ArtworkFingerprint): Boolean {
            if (pixels.size != other.pixels.size || pixels.isEmpty()) return false
            var totalDelta = 0L
            pixels.indices.forEach { index ->
                val first = pixels[index]
                val second = other.pixels[index]
                totalDelta += abs((first ushr 16 and 0xFF) - (second ushr 16 and 0xFF))
                totalDelta += abs((first ushr 8 and 0xFF) - (second ushr 8 and 0xFF))
                totalDelta += abs((first and 0xFF) - (second and 0xFF))
            }
            return totalDelta <= pixels.size * RGB_CHANNEL_COUNT * MAX_AVERAGE_CHANNEL_DELTA
        }
    }

    private var sessionRevision = 0L
    private var artworkRevision = 0L
    private var activeSession: ColorSession? = null
    private var activeArtworkRequest: ArtworkRequest? = null
    private val keyedCache = LinkedHashMap<ArtworkCacheKey, CacheEntry>()

    /** Only the lyric-source binding coordinator can advance this lease. */
    @Synchronized
    internal fun activateSession(
        packageName: String,
        sessionToken: MediaSession.Token
    ): ColorSession {
        val normalizedPackage = packageName.trim()
        require(normalizedPackage.isNotEmpty())
        val current = activeSession
        if (current?.packageName == normalizedPackage && current.sessionToken == sessionToken) {
            return current
        }

        return ColorSession(
            revision = ++sessionRevision,
            packageName = normalizedPackage,
            sessionToken = sessionToken
        ).also {
            activeSession = it
            activeArtworkRequest = null
        }
    }

    @Synchronized
    fun endSession(): Boolean {
        if (activeSession == null) return false
        val revision = activeSession?.revision
        activeSession = null
        activeArtworkRequest = null
        HookLogger.dState(
            stateId = "CoverColorHelper.session",
            tag = TAG,
            state = "ended|$revision"
        ) {
            "颜色会话结束: previousRevision=$revision"
        }
        return true
    }

    @Synchronized
    fun currentSession(packageName: String? = null): ColorSession? {
        val current = activeSession ?: return null
        if (packageName == null) return current
        return current.takeIf {
            it.packageName == packageName.trim()
        }
    }

    @Synchronized
    fun isCurrentSession(session: ColorSession): Boolean {
        return isCurrentSessionLocked(session)
    }

    @Synchronized
    fun currentSession(mediaInfo: MediaMetadataHelper.MediaInfo): ColorSession? =
        activeSession?.takeIf { matchesSessionLocked(it, mediaInfo) }

    /** Authorizes artwork only when it comes from the lease's exact MediaSession. */
    private fun resolveArtworkRequest(
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): ArtworkRequest? {
        val bitmap = mediaInfo.albumArt ?: return null
        if (bitmap.isRecycled) {
            HookLogger.dState(
                stateId = "CoverColorHelper.artwork",
                tag = TAG,
                state = "recycled"
            ) {
                "封面取色跳过: reason=bitmap_recycled"
            }
            return null
        }
        val fingerprint = bitmapFingerprint(bitmap)
        return synchronized(this) {
            val current = activeSession ?: run {
                HookLogger.dState(
                    stateId = "CoverColorHelper.artwork",
                    tag = TAG,
                    state = "no_session"
                ) {
                    "封面取色跳过: reason=no_active_color_session"
                }
                return@synchronized null
            }
            val incomingIdentity = mediaInfo.identity.normalized()
            if (!matchesSessionLocked(current, mediaInfo)) {
                HookLogger.dState(
                    stateId = "CoverColorHelper.artwork",
                    tag = TAG,
                    state = "session_mismatch|${current.revision}|" +
                            "${incomingIdentity.packageName}|" +
                            "${incomingIdentity.sessionToken?.hashCode()}"
                ) {
                    "封面取色跳过: reason=session_mismatch, " +
                            "sessionRevision=${current.revision}, " +
                            "boundPackage=${current.packageName}, " +
                            "boundTokenHash=${current.sessionToken.hashCode()}, " +
                            "mediaPackage=${incomingIdentity.packageName.ifEmpty { "<empty>" }}, " +
                            "mediaTokenHash=${incomingIdentity.sessionToken?.hashCode()}"
                }
                return@synchronized null
            }
            val cacheKey = ArtworkCacheKey(
                packageName = current.packageName,
                sessionToken = current.sessionToken,
                songId = incomingIdentity.songId,
                mediaId = incomingIdentity.mediaId
            )

            val activeRequest = activeArtworkRequest
            if (activeRequest != null &&
                isCurrentSessionLocked(activeRequest.colorSession) &&
                activeRequest.fingerprint.isSimilarTo(fingerprint)
            ) {
                return@synchronized activeRequest
            }

            ArtworkRequest(
                colorSession = current,
                revision = ++artworkRevision,
                cacheKey = cacheKey,
                fingerprint = fingerprint
            ).also { activeArtworkRequest = it }
        }
    }

    /**
     * Resolve the current MediaSession artwork and populate its shared full palette once.
     * Renderers and individual color consumers must reuse this cache instead of treating
     * component callbacks (such as MusicWave) as artwork sources.
     */
    fun ensureArtworkColors(mediaInfo: MediaMetadataHelper.MediaInfo): ArtworkRequest? {
        val bitmap = mediaInfo.albumArt?.takeUnless { it.isRecycled } ?: run {
            invalidateArtworkIfOwned(
                mediaInfo = mediaInfo,
                reason = if (mediaInfo.albumArt == null) "artwork_missing" else "artwork_recycled"
            )
            return null
        }
        val request = resolveArtworkRequest(mediaInfo) ?: return null
        val cachedColors = getCachedColors(
            useGradient = true,
            request = request
        )
        val colors = cachedColors ?: extractColors(
            bitmap = bitmap,
            useGradient = true,
            request = request
        )
        val current = request.takeIf(::isCurrentArtwork)
        val paletteState = when {
            current == null -> "stale_request|${request.revision}"
            colors == null -> "no_palette|${request.revision}"
            colors.first.isEmpty() || colors.second.isEmpty() ->
                "empty_palette|${request.revision}"

            cachedColors != null -> "cache_hit|${request.revision}|${colors.first.size}|${colors.second.size}"
            else -> "extracted|${request.revision}|${colors.first.size}|${colors.second.size}"
        }
        HookLogger.dState(
            stateId = "CoverColorHelper.palette",
            tag = TAG,
            state = paletteState
        ) {
            "封面调色板结果: sessionRevision=${request.colorSession.revision}, " +
                    "artworkRevision=${request.revision}, source=${paletteState.substringBefore('|')}, " +
                    "onWhite=${colors?.first?.size ?: 0}, onBlack=${colors?.second?.size ?: 0}"
        }
        return current
    }

    private fun invalidateArtworkIfOwned(
        mediaInfo: MediaMetadataHelper.MediaInfo,
        reason: String
    ) {
        synchronized(this) {
            val current = activeSession ?: return
            val incomingIdentity = mediaInfo.identity.normalized()
            if (incomingIdentity.packageName != current.packageName ||
                incomingIdentity.sessionToken != current.sessionToken ||
                activeArtworkRequest == null
            ) {
                return
            }
            val previousRevision = activeArtworkRequest?.revision
            activeArtworkRequest = null
            artworkRevision++
            HookLogger.dState(
                stateId = "CoverColorHelper.artwork",
                tag = TAG,
                state = "invalidated|${current.revision}|$previousRevision|$reason"
            ) {
                "封面颜色授权已失效: reason=$reason, sessionRevision=${current.revision}, " +
                        "previousArtworkRevision=$previousRevision"
            }
        }
    }

    @Synchronized
    fun isCurrentArtwork(request: ArtworkRequest): Boolean {
        return isCurrentArtworkLocked(request)
    }

    @Synchronized
    fun currentArtworkRequest(): ArtworkRequest? {
        return activeArtworkRequest?.takeIf(::isCurrentArtworkLocked)
    }

    /**
     * One authorized artwork is extracted once. A normalized content
     * fingerprint allows pause/resume bitmap instances to share colors while still replacing a
     * transitional image when the session publishes its real artwork.
     */
    private fun extractColors(
        bitmap: Bitmap,
        useGradient: Boolean,
        request: ArtworkRequest
    ): Pair<IntArray, IntArray>? {
        if (bitmap.isRecycled) return null
        val cachedColors = synchronized(this) {
            if (!isCurrentArtworkLocked(request)) return@synchronized null
            keyedCache[request.cacheKey]
                ?.takeIf {
                    it.artworkFingerprint.isSimilarTo(request.fingerprint)
                }
                ?.colors
        }
        if (cachedColors != null) {
            return cachedColors.forGradient(useGradient)
        }

        val readableBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: run {
                HookLogger.dState(
                    stateId = "CoverColorHelper.extract",
                    tag = TAG,
                    state = "hardware_copy_failed"
                ) {
                    "封面调色板提取跳过: reason=hardware_bitmap_copy_failed"
                }
                return null
            }
        } else {
            bitmap
        }
        val result = try {
            ColorExtractor.extractThemePalette(readableBitmap, MAX_PALETTE_COLORS)
        } finally {
            if (readableBitmap !== bitmap) readableBitmap.recycle()
        }
        val extractedColors = Pair(
            result.onWhiteBackground.toIntArray(),
            result.onBlackBackground.toIntArray()
        )
        val colors = synchronized(this) {
            if (!isCurrentArtworkLocked(request)) return@synchronized null
            val latest = keyedCache[request.cacheKey]
            if (latest?.artworkFingerprint?.isSimilarTo(request.fingerprint) == true) {
                latest.colors
            } else {
                extractedColors.also {
                    keyedCache[request.cacheKey] = CacheEntry(
                        request.fingerprint,
                        it
                    )
                    trimCache()
                }
            }
        }
        if (colors == null) {
            HookLogger.dState(
                stateId = "CoverColorHelper.extract",
                tag = TAG,
                state = "discarded|${request.colorSession.revision}|${request.revision}"
            ) {
                "封面调色板未写入缓存: reason=stale_artwork_request, " +
                        "sessionRevision=${request.colorSession.revision}, artworkRevision=${request.revision}"
            }
        }
        return colors?.forGradient(useGradient)
    }

    @Synchronized
    fun getCachedColors(
        useGradient: Boolean,
        session: ColorSession
    ): Pair<IntArray, IntArray>? {
        if (!isCurrentSessionLocked(session)) return null
        val request = activeArtworkRequest
            ?.takeIf { it.colorSession.revision == session.revision }
            ?.takeIf(::isCurrentArtworkLocked)
            ?: return null
        return keyedCache[request.cacheKey]
            ?.takeIf { it.artworkFingerprint.isSimilarTo(request.fingerprint) }
            ?.colors
            ?.forGradient(useGradient)
    }

    @Synchronized
    fun getCachedColors(
        useGradient: Boolean,
        request: ArtworkRequest
    ): Pair<IntArray, IntArray>? {
        if (!isCurrentArtworkLocked(request)) return null
        return keyedCache[request.cacheKey]
            ?.takeIf {
                it.artworkFingerprint.isSimilarTo(request.fingerprint)
            }
            ?.colors
            ?.forGradient(useGradient)
    }

    @Synchronized
    fun clearCache() {
        activeSession = null
        activeArtworkRequest = null
        keyedCache.clear()
    }

    private fun isCurrentSessionLocked(session: ColorSession): Boolean {
        val current = activeSession ?: return false
        return current.revision == session.revision &&
                current.packageName == session.packageName &&
                current.sessionToken == session.sessionToken
    }

    private fun matchesSessionLocked(
        session: ColorSession,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): Boolean {
        val incomingIdentity = mediaInfo.identity.normalized()
        return incomingIdentity.packageName == session.packageName &&
                incomingIdentity.sessionToken == session.sessionToken
    }

    private fun isCurrentArtworkLocked(request: ArtworkRequest): Boolean {
        val current = activeArtworkRequest ?: return false
        return isCurrentSessionLocked(request.colorSession) &&
                current.revision == request.revision &&
                current.cacheKey == request.cacheKey
    }

    private fun Pair<IntArray, IntArray>.forGradient(
        useGradient: Boolean
    ): Pair<IntArray, IntArray> {
        if (useGradient) return copyColors()
        val light = first.firstOrNull() ?: return copyColors()
        val dark = second.firstOrNull() ?: return copyColors()
        return Pair(
            intArrayOf(light),
            intArrayOf(dark)
        )
    }

    private fun Pair<IntArray, IntArray>.copyColors(): Pair<IntArray, IntArray> {
        return Pair(first.copyOf(), second.copyOf())
    }

    /**
     * 将封面缩放成固定网格后比较平均像素差，忽略同一封面在尺寸、压缩上的轻微变化。
     */
    private fun bitmapFingerprint(bitmap: Bitmap): ArtworkFingerprint {
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            FINGERPRINT_GRID_SIZE,
            FINGERPRINT_GRID_SIZE,
            true
        )
        val readable = if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, false) ?: scaled
        } else {
            scaled
        }
        return try {
            val pixels = IntArray(FINGERPRINT_GRID_SIZE * FINGERPRINT_GRID_SIZE)
            readable.getPixels(
                pixels,
                0,
                FINGERPRINT_GRID_SIZE,
                0,
                0,
                FINGERPRINT_GRID_SIZE,
                FINGERPRINT_GRID_SIZE
            )
            ArtworkFingerprint(pixels)
        } finally {
            if (readable !== scaled && readable !== bitmap) readable.recycle()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun trimCache() {
        while (keyedCache.size > MAX_CACHED_ARTWORKS) {
            val firstKey = keyedCache.keys.firstOrNull() ?: return
            keyedCache.remove(firstKey)
        }
    }

    private const val MAX_PALETTE_COLORS = 4
    private const val MAX_CACHED_ARTWORKS = 16
    private const val FINGERPRINT_GRID_SIZE = 8
    private const val RGB_CHANNEL_COUNT = 3
    private const val MAX_AVERAGE_CHANNEL_DELTA = 12L
}
