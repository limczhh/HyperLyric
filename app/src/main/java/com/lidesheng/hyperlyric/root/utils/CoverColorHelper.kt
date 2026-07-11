package com.lidesheng.hyperlyric.root.utils

import android.graphics.Bitmap
import com.lidesheng.hyperlyric.common.color.ColorExtractor

object CoverColorHelper {

    private data class CacheEntry(
        val colors: Pair<IntArray, IntArray>
    )

    private var activeMediaKey: String? = null
    private var cachedKey: String? = null
    private var cachedLightColors: IntArray? = null
    private var cachedDarkColors: IntArray? = null
    private val keyedCache = LinkedHashMap<String, CacheEntry>()

    fun updateMediaSession(
        packageName: String,
        title: String,
        artist: String,
        album: String
    ): String {
        val mediaKey = listOf(packageName, title, artist, album)
            .joinToString("\u001F") { it.trim() }
        if (activeMediaKey != mediaKey) {
            activeMediaKey = mediaKey
            cachedKey = null
            cachedLightColors = null
            cachedDarkColors = null
        }
        return mediaKey
    }

    fun currentMediaKey(): String? = activeMediaKey

    fun extractColors(bitmap: Bitmap, useGradient: Boolean, songKey: String? = null): Pair<IntArray, IntArray> {
        val key = buildKey(useGradient, songKey)

        if (key == cachedKey && cachedLightColors != null && cachedDarkColors != null) {
            return Pair(cachedLightColors!!, cachedDarkColors!!)
        }
        keyedCache[key]
            ?.colors
            ?.let { colors ->
                cachedKey = key
                cachedLightColors = colors.first
                cachedDarkColors = colors.second
                return colors
            }

        val result = ColorExtractor.extractThemePalette(bitmap, if (useGradient) 4 else 1)
        val lightColors = result.onWhiteBackground.toIntArray()
        val darkColors = result.onBlackBackground.toIntArray()

        cachedKey = key
        cachedLightColors = lightColors
        cachedDarkColors = darkColors
        val pair = Pair(lightColors, darkColors)
        keyedCache[key] = CacheEntry(pair)
        trimCache()
        return pair
    }

    fun getCachedColors(): Pair<IntArray, IntArray>? {
        val light = cachedLightColors ?: return null
        val dark = cachedDarkColors ?: return null
        return Pair(light, dark)
    }

    fun getCachedColors(useGradient: Boolean, songKey: String? = null): Pair<IntArray, IntArray>? {
        return keyedCache[buildKey(useGradient, songKey)]?.colors
    }

    fun clearCache() {
        activeMediaKey = null
        cachedKey = null
        cachedLightColors = null
        cachedDarkColors = null
        keyedCache.clear()
    }

    private fun buildKey(useGradient: Boolean, songKey: String?): String {
        return "${songKey.orEmpty()}_$useGradient"
    }

    private fun trimCache() {
        while (keyedCache.size > 8) {
            val firstKey = keyedCache.keys.firstOrNull() ?: return
            keyedCache.remove(firstKey)
        }
    }
}
