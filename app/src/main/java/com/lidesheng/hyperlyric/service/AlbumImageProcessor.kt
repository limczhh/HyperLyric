package com.lidesheng.hyperlyric.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.palette.graphics.Palette
import kotlin.math.abs

/**
 * 专辑图片与色彩处理中心。
 *
 * 负责所有 Bitmap 裁剪、圆角处理和从封面提取强调色的逻辑。
 * LiveLyricService 仅在对应开关打开时才调用此处的方法，
 */
object AlbumImageProcessor {

    private val defaultColor = "#E0E0E0".toColorInt()

    data class ExtractedColors(val dominant: Int, val vibrant: Int)

    /**
     * 将原始专辑封面裁剪为正方形并添加圆角。
     * 用于灵动岛通知中的小图标显示。
     */
    fun processAlbumBitmap(source: Bitmap, targetSize: Int = 128): Bitmap {
        val w = source.width
        val h = source.height
        val cropSize = minOf(w, h)
        val xOffset = (w - cropSize) / 2
        val yOffset = (h - cropSize) / 2

        val output = createBitmap(targetSize, targetSize)
        val canvas = Canvas(output)
        val cornerRadius = targetSize / 4f
        val rectF = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, maskPaint)

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        val srcRect = android.graphics.Rect(xOffset, yOffset, xOffset + cropSize, yOffset + cropSize)
        val dstRect = android.graphics.Rect(0, 0, targetSize, targetSize)
        canvas.drawBitmap(source, srcRect, dstRect, bitmapPaint)

        return output
    }

    /**
     * 从专辑封面提取主色和强调色。
     * 当用户未开启进度条强调色开关时，调用方不应调用此方法。
     */
    fun extractColors(bitmap: Bitmap?): ExtractedColors {
        if (bitmap == null || bitmap.isRecycled) return ExtractedColors(defaultColor, defaultColor)
        return try {
            val targetBitmap = if (bitmap.width > 100 || bitmap.height > 100) {
                bitmap.scale(100, 100, false)
            } else bitmap

            val palette = Palette.from(targetBitmap).generate()
            if (targetBitmap != bitmap && !targetBitmap.isRecycled) targetBitmap.recycle()

            val dominant = palette.getDominantColor(defaultColor)
            var vibrant = palette.getVibrantColor(dominant)

            if (isNearBlack(dominant) || isNearWhite(dominant)) {
                vibrant = "#808080".toColorInt()
            } else if (vibrant == dominant || isColorTooSimilar(dominant, vibrant)) {
                vibrant = lightenColor(dominant)
            }

            ExtractedColors(dominant, vibrant)
        } catch (_: Exception) {
            ExtractedColors(defaultColor, defaultColor)
        }
    }

    /**
     * 安全拷贝 Bitmap，防止跨线程并发问题。
     */
    fun safeCopyBitmap(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) return null
        return try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        }
    }

    private fun isNearBlack(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2] < 0.15f
    }

    private fun isNearWhite(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[2] > 0.85f && hsv[1] < 0.15f
    }

    private fun isColorTooSimilar(color1: Int, color2: Int): Boolean {
        val hsv1 = FloatArray(3)
        val hsv2 = FloatArray(3)
        Color.colorToHSV(color1, hsv1)
        Color.colorToHSV(color2, hsv2)

        return abs(hsv1[0] - hsv2[0]) < 10 && abs(hsv1[1] - hsv2[1]) < 0.1f
    }

    private fun lightenColor(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * 1.4f).coerceAtMost(1.0f) // 提高亮度
        hsv[1] = (hsv[1] * 0.8f) // 降低饱和度
        return Color.HSVToColor(hsv)
    }
}
