package com.lidesheng.hyperlyric.root.utils

import android.graphics.Bitmap
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.common.media.MediaIdentity
import com.lidesheng.hyperlyric.common.media.MediaMetadataHelper
import kotlin.math.abs

object CoverColorHelper {
    private const val TAG = "CoverColorHelper"

    /**
     * 歌词源确认的颜色会话。身份和缓存键只在这里解释，渲染层不再拼接媒体字段。
     */
    class ColorSession internal constructor(
        val revision: Long,
        internal val cacheKey: ColorCacheKey,
        internal val title: String,
        internal val artist: String,
        internal val album: String
    ) {
        /** Stable diagnostic token for consumers that need to invalidate animations. */
        val mediaKey: String
            get() = cacheKey.debugKey()
    }

    class ArtworkRequest internal constructor(
        val colorSession: ColorSession,
        val revision: Long,
        internal val fingerprint: ArtworkFingerprint
    )

    private data class CacheEntry(
        val artworkFingerprint: ArtworkFingerprint,
        val colors: Pair<IntArray, IntArray>
    )

    internal data class ColorCacheKey(
        val identity: MediaIdentity,
        val fallbackSessionKey: String?,
    ) {
        fun debugKey(): String {
            val trackKey = when {
                identity.songId != null && identity.mediaId != null ->
                    "song:${identity.songId}|media:${identity.mediaId}"

                identity.songId != null -> "song:${identity.songId}"
                identity.mediaId != null -> "media:${identity.mediaId}"
                else -> ""
            }
            val key = if (identity.sessionToken != null) {
                listOf("session", identity.sessionToken.hashCode(), trackKey)
            } else {
                listOf("fallback", trackKey, fallbackSessionKey.orEmpty())
            }
            return "${identity.packageName}\u001F${key.joinToString("\u001F")}"
        }
    }

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
    private val keyedCache = LinkedHashMap<ColorCacheKey, CacheEntry>()

    /**
     * 只有歌词源生命周期可以推进当前颜色会话。SystemUI 的封面、进度和动画回调
     * 都只能读取此状态，避免迟到的上一首歌回调把活动歌曲切回去。
     */
    @Synchronized
    fun activateSession(mediaInfo: MediaMetadataHelper.MediaInfo): ColorSession? {
        val identity = mediaInfo.identity.normalized()
        val normalizedTitle = mediaInfo.title.normalizeMediaText()
        val normalizedArtist = mediaInfo.artist.normalizeMediaText()
        val normalizedAlbum = mediaInfo.album.normalizeMediaText()
        val trackKey = buildTrackKey(identity.songId, identity.mediaId)
        if (identity.packageName.isEmpty() ||
            (identity.sessionToken == null && trackKey == null &&
                    normalizedAlbum.isEmpty())
        ) {
            return null
        }

        val fallbackSessionKey = if (identity.sessionToken == null) {
            listOf(identity.packageName, normalizedAlbum)
                .joinToString("\u001F")
        } else {
            null
        }
        val cacheKey = ColorCacheKey(
            identity = identity,
            fallbackSessionKey = fallbackSessionKey,
        )
        val current = activeSession
        if (current?.cacheKey == cacheKey) {
            val updated = ColorSession(
                revision = current.revision,
                cacheKey = current.cacheKey,
                title = normalizedTitle.ifEmpty { current.title },
                artist = normalizedArtist.ifEmpty { current.artist },
                album = normalizedAlbum.ifEmpty { current.album }
            )
            activeSession = updated
            return updated
        }

        return ColorSession(
            revision = ++sessionRevision,
            cacheKey = cacheKey,
            title = normalizedTitle,
            artist = normalizedArtist,
            album = normalizedAlbum
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
        sessionRevision++
        HookLogger.dState(
            stateId = "CoverColorHelper.session",
            tag = TAG,
            state = "ended|$revision|$sessionRevision"
        ) {
            "颜色会话结束: previousRevision=$revision, nextRevision=$sessionRevision"
        }
        return true
    }

    @Synchronized
    fun currentSession(packageName: String? = null): ColorSession? {
        val current = activeSession ?: return null
        if (packageName == null) return current
        return current.takeIf {
            it.cacheKey.identity.packageName == packageName.normalizeMediaText()
        }
    }

    @Synchronized
    fun isCurrentSession(session: ColorSession): Boolean {
        return isCurrentSessionLocked(session)
    }

    /**
     * 将系统媒体元数据与歌词源确认的歌曲进行匹配。会话 Token 或稳定媒体 ID
     * 可以直接确认归属；没有这些身份时，只使用包名和专辑进行保守兜底，
     * 不让可变的标题影响颜色会话。
     */
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
            if (!matchesArtworkMetadataLocked(current, mediaInfo)) {
                HookLogger.dState(
                    stateId = "CoverColorHelper.artwork",
                    tag = TAG,
                    state = "metadata_mismatch|${current.revision}|" +
                            "${mediaInfo.identity.packageName}|${mediaInfo.title}|${mediaInfo.artist}"
                ) {
                    "封面取色跳过: reason=metadata_mismatch, sessionRevision=${current.revision}, " +
                        "sessionTitle=\"${debugText(current.title)}\", " +
                        "sessionArtist=\"${debugText(current.artist)}\", " +
                        "mediaTitle=\"${debugText(mediaInfo.title.normalizeMediaText())}\", " +
                        "mediaArtist=\"${debugText(mediaInfo.artist.normalizeMediaText())}\", " +
                        "packageMatches=${current.cacheKey.identity.packageName == mediaInfo.identity.packageName}, " +
                        "mediaPackage=${mediaInfo.identity.packageName.ifEmpty { "<empty>" }}"
                }
                return@synchronized null
            }

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
        val bitmap = mediaInfo.albumArt ?: return null
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

    @Synchronized
    fun isCurrentArtwork(request: ArtworkRequest): Boolean {
        return isCurrentArtworkLocked(request)
    }

    @Synchronized
    fun currentArtworkRequest(): ArtworkRequest? {
        return activeArtworkRequest?.takeIf(::isCurrentArtworkLocked)
    }

    private fun matchesArtworkMetadataLocked(
        current: ColorSession,
        mediaInfo: MediaMetadataHelper.MediaInfo
    ): Boolean {
        val currentIdentity = current.cacheKey.identity
        val incomingIdentity = mediaInfo.identity.normalized()
        val sessionMatch = currentIdentity.sameSessionAs(incomingIdentity)
        if (sessionMatch == false) return false

        val currentMediaId = currentIdentity.mediaId
        val incomingMediaId = incomingIdentity.mediaId
        if (currentMediaId != null && incomingMediaId != null &&
            currentMediaId != incomingMediaId
        ) {
            return false
        }
        val currentSongId = currentIdentity.songId
        val incomingSongId = incomingIdentity.songId
        if (currentSongId != null && incomingSongId != null &&
            currentSongId != incomingSongId
        ) {
            return false
        }

        // A MediaSession token or a stable item ID is stronger than mutable display metadata.
        // Once either one matches, a player may freely change title/artist presentation fields.
        val mediaIdMatches = currentMediaId != null && currentMediaId == incomingMediaId
        val songIdMatches = currentSongId != null && currentSongId == incomingSongId
        if (sessionMatch == true || mediaIdMatches || songIdMatches) return true

        val mediaAlbum = mediaInfo.album.normalizeMediaText()
        if (current.album.isEmpty() || mediaAlbum.isEmpty()) {
            return false
        }
        return isCompatibleAlbum(current.album, mediaAlbum)
    }

    /**
     * 同一歌曲与同一封面只生成一次调色板。使用归一化内容指纹而不是 Bitmap
     * 实例或 generationId，暂停/恢复得到新 Bitmap 时仍可复用；若过渡期封面
     * 随后被真实封面替换，则允许纠正该歌曲的缓存。
     */
    private fun extractColors(
        bitmap: Bitmap,
        useGradient: Boolean,
        request: ArtworkRequest
    ): Pair<IntArray, IntArray>? {
        if (bitmap.isRecycled) return null
        val cachedColors = synchronized(this) {
            if (!isCurrentArtworkLocked(request)) return@synchronized null
            keyedCache[request.colorSession.cacheKey]
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
            val latest = keyedCache[request.colorSession.cacheKey]
            if (latest?.artworkFingerprint?.isSimilarTo(request.fingerprint) == true) {
                latest.colors
            } else {
                extractedColors.also {
                    keyedCache[request.colorSession.cacheKey] = CacheEntry(
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
        return keyedCache[session.cacheKey]?.colors?.forGradient(useGradient)
    }

    @Synchronized
    fun getCachedColors(
        useGradient: Boolean,
        request: ArtworkRequest
    ): Pair<IntArray, IntArray>? {
        if (!isCurrentArtworkLocked(request)) return null
        return keyedCache[request.colorSession.cacheKey]
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
        sessionRevision++
        keyedCache.clear()
    }

    private fun isCurrentSessionLocked(session: ColorSession): Boolean {
        val current = activeSession ?: return false
        return current.revision == session.revision && current.cacheKey == session.cacheKey
    }

    private fun isCurrentArtworkLocked(request: ArtworkRequest): Boolean {
        val current = activeArtworkRequest ?: return false
        return isCurrentSessionLocked(request.colorSession) &&
                current.revision == request.revision &&
                current.colorSession.revision == request.colorSession.revision &&
                current.colorSession.cacheKey == request.colorSession.cacheKey
    }

    private fun buildTrackKey(songId: String?, mediaId: String?): String? {
        return when {
            songId != null && mediaId != null -> "song:$songId|media:$mediaId"
            songId != null -> "song:$songId"
            mediaId != null -> "media:$mediaId"
            else -> null
        }
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

    private fun String.normalizeMediaText(): String {
        return trim().lowercase().replace(WHITESPACE_REGEX, " ")
    }

    private fun debugText(value: String): String {
        if (value.isEmpty()) return "<empty>"
        return value.take(MAX_DEBUG_TEXT_LENGTH).let {
            if (value.length > MAX_DEBUG_TEXT_LENGTH) "$it…" else it
        }
    }

    private fun isCompatibleAlbum(first: String, second: String): Boolean {
        return first == second
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
        while (keyedCache.size > MAX_CACHED_SONGS) {
            val firstKey = keyedCache.keys.firstOrNull() ?: return
            keyedCache.remove(firstKey)
        }
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
    private const val MAX_PALETTE_COLORS = 4
    private const val MAX_CACHED_SONGS = 16
    private const val FINGERPRINT_GRID_SIZE = 8
    private const val RGB_CHANNEL_COUNT = 3
    private const val MAX_AVERAGE_CHANNEL_DELTA = 12L
    private const val MAX_DEBUG_TEXT_LENGTH = 80
}
