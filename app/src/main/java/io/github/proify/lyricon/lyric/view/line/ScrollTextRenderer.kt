/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.content.res.Resources
import android.graphics.Canvas
import android.text.TextPaint
import android.view.animation.LinearInterpolator
import androidx.core.graphics.withTranslation
import io.github.proify.lyricon.lyric.view.dp
import io.github.proify.lyricon.lyric.view.line.model.LyricModel

internal class ScrollTextRenderer : LineRenderer {

    companion object {
        private const val DEFAULT_SPEED_DP = 40f
    }

    private val interpolator = LinearInterpolator()

    var ghostSpacing: Float = 40f.dp
    var scrollSpeed: Float = pxPerMs(DEFAULT_SPEED_DP)
        set(value) { field = pxPerMs(value) }
    var initialDelayMs: Int = 400
    var loopDelayMs: Int = 800
    var repeatCount: Int = -1
    var stopAtEnd: Boolean = false

    override val isPlaying get() = isRunning || isPendingDelay
    override val isFinished get() = finished
    override val isStarted get() = true

    val scrollProgress get() = currentUnitOffset

    private var isRunning = false
    private var isPendingDelay = false
    private var finished = false
    private var currentRepeat = 0
    private var delayRemainingNanos = 0L
    private var currentUnitOffset = 0f
    private var lastViewWidth = 0
    private var lastLyricWidth = 0f
    private var cachedBaseline = 0f
    private var cachedViewHeight = -1

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int
    ): Boolean {
        lastViewWidth = viewWidth
        lastLyricWidth = model.width

        if (finished) return false

        val vw = viewWidth.toFloat()
        if (model.width <= vw) {
            state.scrollOffset = 0f
            state.isScrollFinished = true
            markFinished(state)
            return false
        }

        if (isPendingDelay) {
            delayRemainingNanos -= deltaNanos
            if (delayRemainingNanos <= 0) {
                isPendingDelay = false
                isRunning = true
            }
            return false
        }

        if (!isRunning) return false

        val unit = model.width + ghostSpacing
        val deltaPx = scrollSpeed * (deltaNanos / 1_000_000f)
        currentUnitOffset += deltaPx

        val isLastRepeat = repeatCount > 0 && (currentRepeat + 1) >= repeatCount

        if (stopAtEnd && isLastRepeat) {
            val targetStopOffset = model.width - vw
            if (currentUnitOffset >= targetStopOffset) {
                currentUnitOffset = targetStopOffset
                state.scrollOffset = -targetStopOffset
                state.isScrollFinished = true
                markFinished(state)
                return true
            }
        }

        if (currentUnitOffset >= unit) {
            currentUnitOffset -= unit
            currentRepeat++

            if (repeatCount < 0 || currentRepeat < repeatCount) {
                scheduleDelay(loopDelayMs.toLong())
                state.scrollOffset = 0f
                state.isScrollFinished = false
            } else {
                state.scrollOffset = 0f
                state.isScrollFinished = true
                markFinished(state)
            }
            return true
        }

        val progress = (currentUnitOffset / unit).coerceIn(0f, 1f)
        val easedOffset = -interpolator.getInterpolation(progress) * unit
        state.scrollOffset = easedOffset
        state.isScrollFinished = false
        return true
    }

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val vw = viewWidth.toFloat()
        val offset = state.scrollOffset

        if (cachedViewHeight != viewHeight) {
            val fm = paint.fontMetrics
            cachedBaseline = (viewHeight - (fm.descent - fm.ascent)) / 2f - fm.ascent
            cachedViewHeight = viewHeight
        }

        if (offset < vw && offset + model.width > 0) {
            canvas.withTranslation(x = offset) {
                drawText(model.text, 0f, cachedBaseline, paint)
            }
        }

        if (model.width > vw) {
            val rightEdge = offset + model.width
            if (rightEdge < vw) {
                val ghostX = rightEdge + ghostSpacing
                if (ghostX < vw) {
                    canvas.withTranslation(x = ghostX) {
                        drawText(model.text, 0f, cachedBaseline, paint)
                    }
                }
            }
        }
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        lastViewWidth = viewWidth
        lastLyricWidth = model.width
        startFromBeginning(model, state)
    }

    override fun update(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        lastViewWidth = viewWidth
        lastLyricWidth = model.width
        startFromBeginning(model, state)
    }

    private fun startFromBeginning(model: LyricModel, state: LineState) {
        resetInternal()
        state.reset()

        if (repeatCount == 0) {
            markFinished(state)
            return
        }
        scheduleDelay(initialDelayMs.toLong())
    }

    override fun reset(state: LineState) {
        resetInternal()
        state.reset()
    }

    private fun resetInternal() {
        isRunning = false
        isPendingDelay = false
        finished = false
        currentRepeat = 0
        currentUnitOffset = 0f
        delayRemainingNanos = 0L
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

    private fun markFinished(state: LineState) {
        isRunning = false
        isPendingDelay = false
        finished = true
        state.isScrollFinished = true
    }

    private fun pxPerMs(dpPerSec: Float): Float {
        return (dpPerSec * Resources.getSystem().displayMetrics.density) / 1000f
    }
}
