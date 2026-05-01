/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.view.doOnAttach
import io.github.proify.lyricon.lyric.model.LyricLine
import io.github.proify.lyricon.lyric.view.Highlight
import io.github.proify.lyricon.lyric.view.LyricPlayListener
import io.github.proify.lyricon.lyric.view.Marquee
import io.github.proify.lyricon.lyric.view.TextLook
import io.github.proify.lyricon.lyric.view.UpdatableColor
import io.github.proify.lyricon.lyric.view.WordMotion
import io.github.proify.lyricon.lyric.view.dp
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.createModel
import io.github.proify.lyricon.lyric.view.line.model.emptyLyricModel
import io.github.proify.lyricon.lyric.view.sp
import kotlin.math.ceil

open class LyricLineView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), UpdatableColor {

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(10.dp)
    }

    val textPaint: TextPaint = TextPaintX().apply { textSize = 24f.sp }

    val model: LyricModel get() = _model
    private var _model: LyricModel = emptyLyricModel()

    val lineWidth: Float get() = _model.width

    val isPlainText: Boolean get() = _model.isPlainText
    val isWordSync: Boolean get() = !isPlainText
    val isOverflow: Boolean get() = lineWidth > measuredWidth
    val isPlaying: Boolean get() = activeRenderer.isPlaying
    val isFinished: Boolean get() = activeRenderer.isFinished
    val isStarted: Boolean get() = activeRenderer.isStarted

    var isScrollOnly: Boolean = false
        set(value) {
            field = value
            syncRenderer.isScrollOnly = value
        }

    var playListener: LyricPlayListener? = null
        set(value) {
            field = value
            syncRenderer.playListener = value
        }

    var isWordCharMotionEnabled: Boolean
        get() = syncRenderer.isCharMotionEnabled
        set(value) {
            if (syncRenderer.isCharMotionEnabled == value) return
            syncRenderer.isCharMotionEnabled = value
            requestLayout()
            invalidate()
        }

    var wordMotion: WordMotion = WordMotion()
        set(value) {
            if (field == value) return
            field = value
            syncRenderer.isCharMotionEnabled = value.enabled
            syncRenderer.cjkMotionLiftFactor = value.cjkLiftFactor
            syncRenderer.cjkMotionWaveFactor = value.cjkWaveFactor
            syncRenderer.latinMotionLiftFactor = value.latinLiftFactor
            syncRenderer.latinMotionWaveFactor = value.latinWaveFactor
            requestLayout()
            invalidate()
        }

    private val lineState = LineState()
    private val scrollRenderer = ScrollTextRenderer()
    private val syncRenderer = WordSyncRenderer(this)
    private val animator = Animator()

    private var activeRenderer: LineRenderer = scrollRenderer

    private var primaryColors = intArrayOf()
    private var backgroundColors = intArrayOf()
    private var highlightColors = intArrayOf()

    private var ghostSpacing: Float = 40f.dp
    private var scrollStarted = false
    private var scrollUnlocked = false

    val textSize: Float get() = textPaint.textSize

    fun setTextSize(size: Float) {
        val needsUpdate = textPaint.textSize != size || syncRenderer.bgPaint.textSize != size
        if (!needsUpdate) return
        textPaint.textSize = size
        syncRenderer.setTextSize(size)
        refreshSizes()
        syncRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
        invalidate()
    }

    fun setLyric(rawLine: LyricLine?) {
        val line = if (rawLine?.text.isNullOrBlank()) null else rawLine

        reset()
        scrollUnlocked = false
        scrollStarted = false

        _model = line?.normalize()?.createModel() ?: emptyLyricModel()
        activeRenderer = if (_model.isPlainText) scrollRenderer else syncRenderer
        refreshSizes()
        updateColorsIfReady()
        invalidate()
    }

    fun configureWith(
        text: TextLook, highlight: Highlight, marquee: Marquee,
        gradient: Boolean, fadingEdge: Int
    ) {
        updateColor(text.color, highlight.background, highlight.foreground)
        setTextSize(text.size)
        textPaint.typeface = text.typeface
        syncRenderer.setTypeface(text.typeface)
        syncRenderer.isGradientEnabled = gradient

        scrollRenderer.apply {
            scrollSpeed = marquee.speed
            ghostSpacing = marquee.spacing
            initialDelayMs = marquee.initialDelay
            loopDelayMs = marquee.loopDelay
            repeatCount = marquee.repeatCount
            stopAtEnd = marquee.stopAtEnd
        }
        ghostSpacing = marquee.spacing

        if (fadingEdge <= 0) {
            setFadingEdgeLength(0)
            isHorizontalFadingEdgeEnabled = false
        } else {
            setFadingEdgeLength(fadingEdge)
            isHorizontalFadingEdgeEnabled = true
        }

        refreshSizes()
        animator.stop()
        animator.startIfNeeded()
        invalidate()
    }

    fun requestScroll() {
        doOnAttach {
            scrollUnlocked = true
            if (isPlainText) startScrolling()
        }
    }

    fun seekTo(posMs: Long) {
        if (isPlainText) {
            startScrolling()
        } else {
            activeRenderer.seek(_model, lineState, posMs, measuredWidth, measuredHeight)
            animator.startIfNeeded()
        }
    }

    fun updatePosition(posMs: Long) {
        if (isWordSync) {
            if (syncRenderer.isScrollOnly && !isOverflow) return
            activeRenderer.update(_model, lineState, posMs, measuredWidth, measuredHeight)
            if (syncRenderer.isPlaying && !syncRenderer.isFinished) {
                animator.startIfNeeded()
            }
        } else {
            startScrolling()
        }
    }

    fun refreshSizes() {
        _model.updateSizes(textPaint)
    }

    fun relayout() {
        if (isWordSync) syncRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        primaryColors = primary
        backgroundColors = background
        highlightColors = highlight

        textPaint.apply {
            if (primary.isEmpty()) {
                color = Color.BLACK
                shader = null
            } else {
                color = primary.firstOrNull() ?: Color.BLACK
                shader = if (primary.size > 1) makeRainbowShader(primary) else null
            }
        }
        syncRenderer.setColors(background, highlight)
        invalidate()
    }

    fun reset() {
        animator.stop()
        lineState.reset()
        scrollRenderer.reset(lineState)
        syncRenderer.reset(lineState)
        _model = emptyLyricModel()
        activeRenderer = scrollRenderer
        refreshSizes()
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) relayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            refreshSizes()
            updateColorsIfReady()
        }
    }

    override fun onDraw(canvas: Canvas) {
        activeRenderer.draw(canvas, _model, textPaint, lineState, measuredWidth, measuredHeight)
    }

    override fun getLeftFadingEdgeStrength(): Float {
        if (lineWidth <= width || horizontalFadingEdgeLength <= 0) return 0f
        val edgeL = horizontalFadingEdgeLength.toFloat()

        val offsetInUnit = if (isPlainText) {
            scrollRenderer.scrollProgress
        } else {
            -lineState.scrollOffset
        }

        if (offsetInUnit <= 0f) return 0f
        if (isPlainText && offsetInUnit > lineWidth) return 0f
        return (offsetInUnit / edgeL).coerceIn(0f, 1f)
    }

    override fun getRightFadingEdgeStrength(): Float {
        if (lineWidth <= width || horizontalFadingEdgeLength <= 0) return 0f
        val viewW = width.toFloat()
        val edgeL = horizontalFadingEdgeLength.toFloat()

        if (isPlainText) {
            if (lineState.isScrollFinished) {
                val remaining = lineWidth + lineState.scrollOffset - viewW
                return (remaining / edgeL).coerceIn(0f, 1f)
            }
            val offsetInUnit = scrollRenderer.scrollProgress
            val primaryRightEdge = lineWidth - offsetInUnit
            val ghostLeftEdge = primaryRightEdge + ghostSpacing
            return if (primaryRightEdge < viewW && ghostLeftEdge > viewW) 0f else 1.0f
        } else {
            if (isFinished) return 0f
        }

        val remaining = lineWidth + lineState.scrollOffset - viewW
        return (remaining / edgeL).coerceIn(0f, 1f)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val charMotionPadding = if (isWordCharMotionEnabled) {
            val maxLift = maxOf(wordMotion.cjkLiftFactor, wordMotion.latinLiftFactor)
            ceil(textPaint.textSize * maxLift).toInt()
        } else {
            0
        }
        val textHeight = (textPaint.descent() - textPaint.ascent()).toInt() + charMotionPadding
        setMeasuredDimension(w, resolveSize(textHeight, hSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    private fun startScrolling() {
        if (!isPlainText || !scrollUnlocked || scrollStarted) return
        scrollStarted = true
        lineState.reset()
        if (!isOverflow) return
        post {
            scrollRenderer.update(_model, lineState, 0, measuredWidth, measuredHeight)
            animator.stop()
            animator.startIfNeeded()
        }
    }

    private fun updateColorsIfReady() {
        if (primaryColors.isNotEmpty() && backgroundColors.isNotEmpty() && highlightColors.isNotEmpty()) {
            updateColor(primaryColors, backgroundColors, highlightColors)
        }
    }

    private var rainbowShader: Shader? = null
    private var rainbowShaderHash = 0
    private var rainbowShaderWidth = -1f

    private fun makeRainbowShader(colors: IntArray): Shader {
        val hash = colors.contentHashCode()
        if (rainbowShader != null && rainbowShaderHash == hash && rainbowShaderWidth == lineWidth) {
            return rainbowShader!!
        }
        val positions = FloatArray(colors.size) { i -> i.toFloat() / (colors.size - 1) }
        rainbowShader =
            LinearGradient(0f, 0f, lineWidth, 0f, colors, positions, Shader.TileMode.CLAMP)
        rainbowShaderHash = hash
        rainbowShaderWidth = lineWidth
        return rainbowShader!!
    }

    private inner class Animator : Choreographer.FrameCallback {
        private var running = false
        private var lastFrameNanos = 0L

        fun startIfNeeded() {
            if (!running && isAttachedToWindow) {
                running = true
                lastFrameNanos = 0L
                post { Choreographer.getInstance().postFrameCallback(this) }
            }
        }

        fun stop() {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
            lastFrameNanos = 0L
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !isAttachedToWindow) {
                running = false
                return
            }

            val deltaNanos = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
            lastFrameNanos = frameTimeNanos

            val renderer = activeRenderer
            val changed = renderer.step(deltaNanos, _model, lineState, measuredWidth)
            if (changed) postInvalidateOnAnimation()

            if (running) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }
}
