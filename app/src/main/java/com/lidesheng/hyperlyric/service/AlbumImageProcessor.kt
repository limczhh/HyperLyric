package com.lidesheng.hyperlyric.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.utils.LogManager

/**
 * 专辑图片与色彩处理中心。
 *
 * 负责所有 Bitmap 裁剪、圆角处理和从封面提取强调色的逻辑。
 * LiveLyricService 仅在对应开关打开时才调用此处的方法，
 */
object AlbumImageProcessor {

    private val defaultColor = "#E0E0E0".toColorInt()

    data class ExtractedColors(
        val main: Int,
        val secondary: Int
    )

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

    fun processAlbumBitmapCircular(source: Bitmap, targetSize: Int = 128): Bitmap {
        val w = source.width
        val h = source.height
        val cropSize = minOf(w, h)
        val xOffset = (w - cropSize) / 2
        val yOffset = (h - cropSize) / 2

        val output = createBitmap(targetSize, targetSize)
        val canvas = Canvas(output)
        val radius = targetSize / 2f

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(radius, radius, radius, maskPaint)

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
     * 使用 ColorExtractor 从专辑封面提取主色和次色。
     * 统一返回适配深色背景的高亮度颜色（即使在浅色背景下也使用鲜艳色）。
     */
    fun extractColors(bitmap: Bitmap?): ExtractedColors {
        val fallback = ExtractedColors(defaultColor, defaultColor)
        if (bitmap == null || bitmap.isRecycled) return fallback
        
        return try {
            val targetBitmap = if (bitmap.width > 100 || bitmap.height > 100) {
                bitmap.scale(100, 100, false)
            } else bitmap

            // 使用 ColorExtractor 提取调色板
            val palette = ColorExtractor.extractThemePalette(targetBitmap, 2)
            
            if (targetBitmap != bitmap && !targetBitmap.isRecycled) targetBitmap.recycle()

            // 统一取 onBlackBackground (高亮度) 的结果
            val result = ExtractedColors(
                main = palette.onBlackBackground.getOrNull(0) ?: defaultColor,
                secondary = palette.onBlackBackground.getOrNull(1) ?: (palette.onBlackBackground.getOrNull(0) ?: defaultColor)
            )
            LogManager.d("AlbumProcessor", "正在提取取色: ${bitmap.width}x${bitmap.height}, 主色=${String.format("#%06X", 0xFFFFFF and result.main)}, 次色=${String.format("#%06X", 0xFFFFFF and result.secondary)}")
            result
        } catch (e: Exception) {
            LogManager.e("AlbumProcessor", "取色失败: ${bitmap.width}x${bitmap.height}", e)
            fallback
        }
    }

    /**
     * 安全拷贝 Bitmap，防止跨线程并发问题。
     */
    fun safeCopyBitmap(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null || bitmap.isRecycled) return null
        return try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            LogManager.w("AlbumProcessor", "Bitmap 拷贝失败: ${bitmap.width}x${bitmap.height}", e)
            null
        }
    }
}
