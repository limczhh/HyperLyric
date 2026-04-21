package com.lidesheng.hyperlyric.service.utils

import android.graphics.Paint
import android.util.DisplayMetrics
import android.util.TypedValue
import kotlin.math.abs

/**
 * 歌词分割工具类
 * 负责根据像素宽度将歌词文本拆分为灵动岛左右侧和通知栏左右侧
 */
class LyricSplitter(
    private val paint: Paint,
    displayMetrics: DisplayMetrics
) {
    data class SplitResult(
        val islandLeft: String,
        val islandRight: String,
        val notificationLeft: String,
        val notificationRight: String
    )

    data class Config(
        val showIslandLeftAlbum: Boolean,
        val showAlbumArt: Boolean,
        val disableLyricSplit: Boolean,
        val limitMaxWidth: Boolean,
        val maxWidth: Int
    )

    private val defaultSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, displayMetrics)

    private fun scalePxToInternalLimit(targetLimitPx: Float): Float {
        return targetLimitPx * (paint.textSize / defaultSizePx.coerceAtLeast(1f))
    }

    /**
     * 执行分割逻辑
     */
    fun split(title: String, config: Config): SplitResult {
        if (title.isBlank()) return SplitResult("", "HyperLyric", "", "HyperLyric")

        val totalWidth = paint.measureText(title)
        
        var islandLeft: String
        var islandRight: String

        if (config.limitMaxWidth) {
            // ================= 模式 1：限制最大宽度（智能平衡 + 截断） =================
            val maxWidthPX = scalePxToInternalLimit(config.maxWidth.toFloat())
            val halfLimitPX = maxWidthPX / 2f
            
            val leftMaxPX = if (config.showIslandLeftAlbum) {
                (halfLimitPX - scalePxToInternalLimit(80f)).coerceAtLeast(0f)
            } else {
                halfLimitPX
            }

            if (totalWidth <= maxWidthPX) {
                // 短歌词：执行理想的对半分逻辑（比如 "爱在西" | "元前"）
                val targetLeftWidth = if (config.showIslandLeftAlbum) (totalWidth / 2f) - scalePxToInternalLimit(60f) else totalWidth / 2f
                val cutIndex = computeSplitIndexByPixel(title, targetLeftWidth.coerceAtLeast(0f), leftMaxPX)
                islandLeft = title.substring(0, cutIndex).trim()
                islandRight = title.substring(cutIndex).trim()
            } else {
                // 长歌词：执行截断逻辑，左侧占满限额
                val cutIndex = computeSplitIndexByPixel(title, leftMaxPX, leftMaxPX)
                islandLeft = title.substring(0, cutIndex).trim()
                val remainingText = title.substring(cutIndex).trim()
                
                // 右侧也要根据剩余限额进行截断（halfLimitPX）
                val rightIndex = computeSplitIndexByPixel(remainingText, halfLimitPX, halfLimitPX)
                islandRight = remainingText.substring(0, rightIndex).trim()
            }
        } else {
            // ================= 模式 2：标准模式（原有逻辑） =================
            val cutLimitRaw = if (config.showIslandLeftAlbum) 650f else 720f
            val leftTextLimitRaw = if (config.showIslandLeftAlbum) 280f else 360f
            
            val cutLimitPX = scalePxToInternalLimit(cutLimitRaw)
            val leftTextLimitPX = scalePxToInternalLimit(leftTextLimitRaw)
            
            if (totalWidth <= cutLimitPX) {
                // 短歌词对分环绕
                val targetLeftWidth = if (config.showIslandLeftAlbum) (totalWidth / 2f) - scalePxToInternalLimit(60f) else totalWidth / 2f
                val cutIndex = computeSplitIndexByPixel(title, targetLeftWidth.coerceAtLeast(0f), leftTextLimitPX)
                islandLeft = title.substring(0, cutIndex).trim()
                islandRight = title.substring(cutIndex).trim()
            } else {
                // 长歌词截断
                val cutIndex = computeSplitIndexByPixel(title, leftTextLimitPX, leftTextLimitPX)
                islandLeft = title.substring(0, cutIndex).trim()
                islandRight = title.substring(cutIndex).trim()
            }
        }

        // 处理“禁止分割”设置
        if (config.disableLyricSplit) {
            islandRight = if (config.limitMaxWidth) {
                // 如果开启了宽度限制，即便不分割，右侧单侧也需截断至最大宽度的一半（保持右侧物理边界一致）
                val halfMaxWidthPX = scalePxToInternalLimit(config.maxWidth.toFloat()) / 2f
                val cutIndex = computeSplitIndexByPixel(title, halfMaxWidthPX, halfMaxWidthPX)
                title.substring(0, cutIndex).trim()
            } else {
                title
            }
            islandLeft = ""
        }

        // 最终检查：如果右侧为空但左侧有值，将其转移到右侧
        if (islandRight.isEmpty() && islandLeft.isNotEmpty()) {
            islandRight = islandLeft
            islandLeft = ""
        }

        // ================= 通知栏轨道逻辑 =================
        val focusLimitRaw = if (config.showAlbumArt) 560f else 680f
        val focusNotificationLimitPX = scalePxToInternalLimit(focusLimitRaw)
        val nLeft: String
        val nRight: String

        if (totalWidth <= focusNotificationLimitPX) {
            nLeft = title
            nRight = " "
        } else {
            val cutIndex = computeSplitIndexByPixel(title, focusNotificationLimitPX, focusNotificationLimitPX)
            nLeft = title.substring(0, cutIndex).trim()
            nRight = title.substring(cutIndex).trim()
        }

        return SplitResult(islandLeft, islandRight, nLeft, nRight)
    }

    private fun computeSplitIndexByPixel(title: String, targetWidthPx: Float, maxLeftPx: Float): Int {
        var splitIndex = paint.breakText(title, true, targetWidthPx, null)

        if (splitIndex < title.length && paint.measureText(title, 0, splitIndex) < targetWidthPx) {
            if (paint.measureText(title, 0, splitIndex + 1) <= maxLeftPx) {
                splitIndex++
            }
        }
        
        splitIndex = splitIndex.coerceIn(0, title.length)
        return adjustForWordBoundary(title, splitIndex, maxLeftPx)
    }

    private fun adjustForWordBoundary(text: String, originalIndex: Int, maxLimitPx: Float): Int {
        if (originalIndex <= 0 || originalIndex >= text.length) return originalIndex.coerceIn(
            0,
            text.length
        )

        val isAsciiAlnum = { c: Char -> c.isLetterOrDigit() && c.code < 128 }
        if (!isAsciiAlnum(text[originalIndex - 1]) || !isAsciiAlnum(text[originalIndex])) return originalIndex

        var backSplit = originalIndex
        while (backSplit > 0 && isAsciiAlnum(text[backSplit - 1])) backSplit--

        var forwardSplit = originalIndex
        while (forwardSplit < text.length && isAsciiAlnum(text[forwardSplit])) forwardSplit++

        val forwardPx = paint.measureText(text, 0, forwardSplit)
        if (forwardPx > maxLimitPx) return backSplit

        val forwardDiff = abs(forwardSplit - (text.length - forwardSplit))
        val backDiff = abs(backSplit - (text.length - backSplit))

        return if (backDiff < forwardDiff) backSplit else forwardSplit
    }
}
