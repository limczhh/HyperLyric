/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.content.res.Resources
import android.graphics.Canvas
import android.view.animation.LinearInterpolator
import androidx.core.graphics.withTranslation
import io.github.proify.lyricon.lyric.view.dp
import java.lang.ref.WeakReference

class Marquee(private val viewRef: WeakReference<LyricLineView>) {
    companion object {
        private const val DEFAULT_SCROLL_SPEED_DP = 40f
    }

    private val interpolator = LinearInterpolator()

    // --- 配置参数 ---
    var ghostSpacing: Float = 40f.dp

    var scrollSpeed: Float = calculateSpeed(DEFAULT_SCROLL_SPEED_DP)
        set(value) {
            field = calculateSpeed(value)
        }

    var initialDelayMs: Int = 400
    var loopDelayMs: Int = 800
    var repeatCount: Int = -1 // -1 为无限循环
    var stopAtEnd: Boolean = false

    // --- 内部运行状态 ---
    var currentRepeat: Int = 0
        private set

    var isRunning: Boolean = false
        private set

    var isPendingDelay: Boolean = false
        private set

    // 新增：明确的结束状态标记
    private var _isFinished: Boolean = false

    private var delayRemainingNanos = 0L

    /**
     * 当前滚动的进度位移 (0f .. unit)
     * unit = lyricWidth + ghostSpacing
     */
    var currentUnitOffset: Float = 0f
        private set

    private fun calculateSpeed(dpPerSec: Float): Float {
        // 转换为 px/ms
        return (dpPerSec * Resources.getSystem().displayMetrics.density) / 1000f
    }

    /**
     * 判断播放是否彻底结束
     * 返回 true 的情况：
     * 1. 文本短于视图宽度（不需要滚动，视为立即结束）
     * 2. 达到了重复次数限制，并且不在暂停状态
     */
    fun isAnimationFinished(): Boolean {
        // 检查 View 是否存活以及内容宽度
        val view = viewRef.get() ?: return true
        if (view.lyricWidth <= view.width) return true

        return _isFinished
    }

    fun start() {
        // 如果重复次数为0，直接标记为结束
        if (repeatCount == 0) {
            markFinished()
            return
        }
        reset()
        scheduleDelay(initialDelayMs.toLong())
    }

    fun reset() {
        isRunning = false
        isPendingDelay = false
        _isFinished = false
        currentRepeat = 0
        currentUnitOffset = 0f
        delayRemainingNanos = 0L
        updateViewOffset(0f, false)
    }

    private fun scheduleDelay(delayMs: Long) {
        if (delayMs <= 0L) {
            isRunning = true
            isPendingDelay = false
        } else {
            delayRemainingNanos = delayMs * 1_000_000L
            isPendingDelay = true
            isRunning = false
        }
    }

    fun step(deltaNanos: Long) {
        if (_isFinished) return

        val view = viewRef.get() ?: return
        val lyricWidth = view.lyricWidth
        val viewWidth = view.width.toFloat()

        if (lyricWidth <= viewWidth) {
            updateViewOffset(0f, true)
            markFinished()
            return
        }

        if (isPendingDelay) {
            delayRemainingNanos -= deltaNanos
            if (delayRemainingNanos <= 0) {
                isPendingDelay = false
                isRunning = true
            }
            return
        }

        if (!isRunning) return

        val unit = lyricWidth + ghostSpacing
        val deltaPx = scrollSpeed * (deltaNanos / 1_000_000f)
        currentUnitOffset += deltaPx

        // --- 核心修复：处理 stopAtEnd 的提前终点 ---
        // 判断是否是最后一次播放循环
        val isLastRepeat = repeatCount > 0 && (currentRepeat + 1) >= repeatCount

        if (stopAtEnd && isLastRepeat) {
            // 在 stopAtEnd 模式下，最后一遍的终点是：文字右侧与 View 右侧对齐
            val targetStopOffset = lyricWidth - viewWidth
            if (currentUnitOffset >= targetStopOffset) {
                currentUnitOffset = targetStopOffset // 锁定在终点
                val finalX = -targetStopOffset       // 转换为 ScrollX (负数)
                updateViewOffset(finalX, true)
                markFinished()
                return
            }
        }

        // --- 处理正常循环跳转 ---
        if (currentUnitOffset >= unit) {
            currentUnitOffset -= unit
            currentRepeat++

            // 无限循环或还没到次数
            @Suppress("ConvertTwoComparisonsToRangeCheck")
            if (repeatCount < 0 || currentRepeat < repeatCount) {
                scheduleDelay(loopDelayMs.toLong())
                updateViewOffset(0f, false)
            } else {
                // 如果没有设置 stopAtEnd 但次数到了，直接回到起点结束
                updateViewOffset(0f, true)
                markFinished()
            }
            return
        }

        // 正常平滑滚动更新
        val progress = (currentUnitOffset / unit).coerceIn(0f, 1f)
        val easedOffset = -interpolator.getInterpolation(progress) * unit
        updateViewOffset(easedOffset, false)
    }

    private fun markFinished() {
        isRunning = false
        isPendingDelay = false
        _isFinished = true
        viewRef.get()?.isScrollFinished = true
    }

    private fun updateViewOffset(offset: Float, finished: Boolean) {
        viewRef.get()?.let {
            it.scrollXOffset = offset
            it.isScrollFinished = finished
        }
    }

    /**
     * 绘制逻辑
     * 优化点：根据 offset 位置决定绘制，而不是根据 isRunning 状态
     */
    fun draw(canvas: Canvas) {
        val view = viewRef.get() ?: return
        val model = view.lyric
        val paint = view.textPaint
        val text = model.text
        val lyricWidth = model.width
        val viewWidth = view.width.toFloat()

        // 这里的 offset 是由 step() 计算并赋值给 view.scrollXOffset 的
        val offset = view.scrollXOffset

        // 计算基线，垂直居中
        val fontMetrics = paint.fontMetrics
        val fontHeight = fontMetrics.descent - fontMetrics.ascent
        val baseline = (view.height - fontHeight) / 2f - fontMetrics.ascent

        // 1. 绘制主体文本
        // 只要主体还在屏幕内（或者部分在屏幕内），就绘制
        if (offset < viewWidth && offset + lyricWidth > 0) {
            canvas.withTranslation(x = offset) {
                drawText(text, 0f, baseline, paint)
            }
        }

        // 2. 绘制“鬼影”副本 (循环滚动的连接部分)
        // 触发条件：主体文本的尾部已经进入屏幕 (offset + lyricWidth < viewWidth)
        // 且文本确实比视图长 (lyricWidth > viewWidth)
        if (lyricWidth > viewWidth) {
            val rightEdgeOfMainText = offset + lyricWidth
            if (rightEdgeOfMainText < viewWidth) {
                val ghostOffset = rightEdgeOfMainText + ghostSpacing
                // 只有鬼影在屏幕内才绘制
                if (ghostOffset < viewWidth) {
                    canvas.withTranslation(x = ghostOffset) {
                        drawText(text, 0f, baseline, paint)
                    }
                }
            }
        }
    }
}