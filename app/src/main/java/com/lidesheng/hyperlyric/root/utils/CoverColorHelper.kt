package com.lidesheng.hyperlyric.root.utils

import android.graphics.Bitmap
import io.github.proify.lyricon.common.util.CoverThemeColorExtractor
import io.github.proify.lyricon.common.util.CoverThemeGradientExtractor

object CoverColorHelper {

    private var cachedKey: String? = null
    private var cachedLightColors: IntArray? = null
    private var cachedDarkColors: IntArray? = null

    fun extractColors(bitmap: Bitmap, useGradient: Boolean, songKey: String? = null): Pair<IntArray, IntArray> {
        val key = "${songKey}_${useGradient}"
        if (key == cachedKey && cachedLightColors != null && cachedDarkColors != null) {
            return Pair(cachedLightColors!!, cachedDarkColors!!)
        }

        val result = if (useGradient) {
            val gradient = CoverThemeGradientExtractor.extract(bitmap)
            Pair(gradient.lightModeColors, gradient.darkModeColors)
        } else {
            val color = CoverThemeColorExtractor.extract(bitmap)
            Pair(intArrayOf(color.lightModeColor), intArrayOf(color.darkModeColor))
        }

        cachedKey = key
        cachedLightColors = result.first
        cachedDarkColors = result.second
        return result
    }

    fun getCachedColors(): Pair<IntArray, IntArray>? {
        val light = cachedLightColors ?: return null
        val dark = cachedDarkColors ?: return null
        return Pair(light, dark)
    }

    fun clearCache() {
        cachedKey = null
        cachedLightColors = null
        cachedDarkColors = null
    }
}
