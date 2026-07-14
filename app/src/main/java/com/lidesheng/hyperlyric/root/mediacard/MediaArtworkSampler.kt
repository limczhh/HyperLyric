package com.lidesheng.hyperlyric.root.mediacard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.sqrt

internal object MediaArtworkSampler {
    private const val MAX_SAMPLE_PIXELS = 100 * 100

    fun sample(bitmap: Bitmap): Bitmap? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val pixelCount = bitmap.width.toLong() * bitmap.height
        if (pixelCount <= MAX_SAMPLE_PIXELS) {
            return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        }
        val scale = sqrt(MAX_SAMPLE_PIXELS.toDouble() / pixelCount)
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrNull() ?: return null
        if (scaled.config == Bitmap.Config.ARGB_8888) return scaled
        return runCatching { scaled.copy(Bitmap.Config.ARGB_8888, false) }
            .also { scaled.recycle() }
            .getOrNull()
    }

    fun sample(drawable: Drawable): Bitmap? {
        (drawable as? BitmapDrawable)?.bitmap?.let { return sample(it) }
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        val pixelCount = sourceWidth.toLong() * sourceHeight
        val scale = if (pixelCount > MAX_SAMPLE_PIXELS) {
            sqrt(MAX_SAMPLE_PIXELS.toDouble() / pixelCount)
        } else {
            1.0
        }
        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalBounds = Rect(drawable.bounds)
        return try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(result))
            result
        } catch (_: Throwable) {
            result.recycle()
            null
        } finally {
            drawable.bounds = originalBounds
        }
    }

    fun fingerprint(bitmap: Bitmap): Long {
        var hash = 1125899906842597L
        hash = hash * 31 + bitmap.width
        hash = hash * 31 + bitmap.height
        val stepX = maxOf(1, bitmap.width / 16)
        val stepY = maxOf(1, bitmap.height / 16)
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                hash = hash * 31 + bitmap.getPixel(x, y).toLong()
            }
        }
        return hash
    }
}
