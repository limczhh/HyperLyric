package com.lidesheng.hyperlyric.root.utils

import android.graphics.Bitmap
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.root.LyriconDataBridge

object CoverColorHelper {

    private data class CacheEntry(
        val colors: Pair<IntArray, IntArray>
    )

    private var activeMediaKey: String? = null
    private val keyedCache = LinkedHashMap<String, CacheEntry>()

    @Synchronized
    fun updateMediaSession(
        packageName: String,
        title: String,
        artist: String,
        album: String,
        duration: Long = -1L
    ): String {
        val lyricSong = LyriconDataBridge.currentSong?.takeIf {
            val lyricPackage = LyriconDataBridge.currentLyricPackageName
            (lyricPackage.isNullOrBlank() ||
                lyricPackage.normalizeMediaText() == packageName.normalizeMediaText()) &&
                isCompatibleMediaText(it.name, title)
        }
        val songId = lyricSong?.id
            ?.normalizeMediaText()
            ?.takeIf { it.isNotEmpty() && it != "0" }
        val resolvedTitle = lyricSong?.name
            ?.takeIf { it.isNotBlank() }
            ?: title
        val resolvedArtist = lyricSong?.artist
            ?.takeIf { it.isNotBlank() }
            ?: artist
        val resolvedDuration = lyricSong?.duration
            ?.takeIf { it > 0L }
            ?: duration.takeIf { it > 0L }
        val identity = songId?.let { "id:$it" } ?: listOf(
            "meta",
            resolvedTitle.normalizeMediaText(),
            resolvedArtist.normalizeMediaText(),
            resolvedDuration?.toString().orEmpty(),
            album.normalizeMediaText().takeIf {
                resolvedTitle.isBlank() && resolvedArtist.isBlank()
            }.orEmpty()
        ).joinToString("\u001F")
        val mediaKey = "${packageName.normalizeMediaText()}\u001F$identity"
        activeMediaKey = mediaKey
        return mediaKey
    }

    @Synchronized
    fun currentMediaKey(): String? = activeMediaKey

    /**
     * 每首歌只生成一次完整调色板。调用方按需选择单色或渐变，
     * Bitmap 实例/generationId 的变化不会使同一首歌重新取色。
     */
    @Synchronized
    fun extractColors(bitmap: Bitmap, useGradient: Boolean, songKey: String? = null): Pair<IntArray, IntArray> {
        val key = songKey ?: activeMediaKey ?: bitmapFallbackKey(bitmap)
        val colors = keyedCache[key]?.colors ?: run {
            val result = ColorExtractor.extractThemePalette(bitmap, MAX_PALETTE_COLORS)
            Pair(
                result.onWhiteBackground.toIntArray(),
                result.onBlackBackground.toIntArray()
            ).also {
                keyedCache[key] = CacheEntry(it)
                trimCache()
            }
        }
        return colors.forGradient(useGradient)
    }

    @Synchronized
    fun getCachedColors(): Pair<IntArray, IntArray>? {
        val key = activeMediaKey ?: return null
        return keyedCache[key]?.colors?.copyColors()
    }

    @Synchronized
    fun getCachedColors(useGradient: Boolean, songKey: String? = null): Pair<IntArray, IntArray>? {
        val key = songKey ?: activeMediaKey ?: return null
        return keyedCache[key]?.colors?.forGradient(useGradient)
    }

    @Synchronized
    fun clearCache() {
        activeMediaKey = null
        keyedCache.clear()
    }

    private fun Pair<IntArray, IntArray>.forGradient(useGradient: Boolean): Pair<IntArray, IntArray> {
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

    private fun isCompatibleMediaText(lyricValue: String?, mediaValue: String): Boolean {
        val lyric = lyricValue.orEmpty().normalizeMediaText()
        val media = mediaValue.normalizeMediaText()
        return lyric.isEmpty() ||
            media.isEmpty() ||
            lyric == media ||
            lyric.contains(media) ||
            media.contains(lyric)
    }

    private fun bitmapFallbackKey(bitmap: Bitmap): String {
        var fingerprint = 1125899906842597L
        fingerprint = fingerprint * 31 + bitmap.width
        fingerprint = fingerprint * 31 + bitmap.height
        val stepX = maxOf(1, bitmap.width / 16)
        val stepY = maxOf(1, bitmap.height / 16)
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                fingerprint = fingerprint * 31 + bitmap.getPixel(x, y).toLong()
            }
        }
        return "artwork:$fingerprint"
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
}
