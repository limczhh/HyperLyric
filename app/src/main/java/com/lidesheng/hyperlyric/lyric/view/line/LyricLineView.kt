/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Shader
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.core.view.doOnAttach
import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.view.Highlight
import com.lidesheng.hyperlyric.lyric.view.LyricPlayListener
import com.lidesheng.hyperlyric.lyric.view.Marquee
import com.lidesheng.hyperlyric.lyric.view.TextLook
import com.lidesheng.hyperlyric.lyric.view.UpdatableColor
import com.lidesheng.hyperlyric.lyric.view.WordMotion
import com.lidesheng.hyperlyric.lyric.view.dp
import com.lidesheng.hyperlyric.lyric.view.isCountdownLine
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel
import com.lidesheng.hyperlyric.lyric.view.line.model.createModel
import com.lidesheng.hyperlyric.lyric.view.line.model.emptyLyricModel
import com.lidesheng.hyperlyric.lyric.view.sp
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

    val lineWidth: Float
        get() = if (_model.isCountdownLine()) countdownRenderer.contentWidth() else _model.width

    // ---- Metadata marquee overrides (called from HyperLyric) ----
    fun setMarqueeSpeed(speed: Float) {
        scrollRenderer.scrollSpeed = speed
    }

    fun setMarqueeInitialDelay(ms: Int) {
        scrollRenderer.initialDelayMs = ms
    }

    fun setMarqueeLoopDelay(ms: Int) {
        scrollRenderer.loopDelayMs = ms
    }

    fun setMarqueeRepeatCount(count: Int) {
        scrollRenderer.repeatCount = count
    }

    fun setMarqueeStopAtEnd(stop: Boolean) {
        scrollRenderer.stopAtEnd = stop
    }

    fun setPeerLineWidth(width: Float) {
        scrollRenderer.peerLineWidth = width
    }

    val isPlainText: Boolean get() = activeRenderer === scrollRenderer
    val isWordSync: Boolean get() = activeRenderer !== scrollRenderer
    val isOverflow: Boolean get() = lineWidth > measuredWidth
    val isPlaying: Boolean get() = activeRenderer.isPlaying
    val isFinished: Boolean get() = activeRenderer.isFinished
    val isStarted: Boolean get() = activeRenderer.isStarted

    var isScrollOnly: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            syncRenderer.isScrollOnly = value
            requestLayout()
            invalidate()
        }

    var centerIfPossible: Boolean = false
        set(value) {
            field = value
            syncRenderer.centerIfPossible = value
            scrollRenderer.centerIfPossible = value
            lineTimelineRenderer.centerIfPossible = value
            countdownRenderer.centerIfPossible = value
        }

    var isSustainProgressEnabled: Boolean
        get() = syncRenderer.isSustainProgressEnabled
        set(value) {
            syncRenderer.isSustainProgressEnabled = value
        }

    var rightIfPossible: Boolean = false
        set(value) {
            field = value
            syncRenderer.rightIfPossible = value
            scrollRenderer.rightIfPossible = value
            lineTimelineRenderer.rightIfPossible = value
            countdownRenderer.rightIfPossible = value
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
            syncRenderer.latinMotionByCharacter = value.latinByCharacter
            syncRenderer.latinMotionLiftFactor = value.latinLiftFactor
            syncRenderer.latinMotionWaveFactor = value.latinWaveFactor
            requestLayout()
            invalidate()
        }

    private val lineState = LineState()
    private val scrollRenderer = ScrollTextRenderer()
    private val syncRenderer = WordSyncRenderer(this)
    private val lineTimelineRenderer = LineTimelineRenderer()
    private val countdownRenderer = CountdownDotsRenderer()
    private val animator = Animator()

    private var activeRenderer: LineRenderer = scrollRenderer

    private var primaryColors = intArrayOf()
    private var backgroundColors = intArrayOf()
    private var highlightColors = intArrayOf()

    private var ghostSpacing: Float = 40f.dp
    private var scrollStarted = false
    private var scrollUnlocked = false
    private var playbackActive = true
    private var sharedMarqueeClockEnabled = false
    private var sharedMarqueeOriginActiveTimeMs = 0L
    private var sharedMarqueeActiveTimeMs = 0L

    /**
     * Island Real/Fake trees are two presentations of one playback clock. Their hidden tree keeps
     * advancing renderer state so Xiaomi can exchange visibility without a catch-up frame.
     */
    internal var keepPlaybackClockRunningWhenHidden: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (playbackActive && canAdvancePlaybackClock()) {
                resumePlaybackAnimation()
            } else if (!isShown) {
                animator.stop()
            }
        }

    internal fun useSharedMarqueeClock(enabled: Boolean, originActiveTimeMs: Long = 0L) {
        val resolvedOrigin = if (enabled) originActiveTimeMs.coerceAtLeast(0L) else 0L
        if (sharedMarqueeClockEnabled == enabled &&
            sharedMarqueeOriginActiveTimeMs == resolvedOrigin
        ) return
        sharedMarqueeClockEnabled = enabled
        sharedMarqueeOriginActiveTimeMs = resolvedOrigin
        sharedMarqueeActiveTimeMs = 0L
    }

    var isStaticPreview: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                animator.stop()
                scrollUnlocked = false
                scrollStarted = false
                lineState.reset()
            }
            updatePlainTextColors()
            invalidate()
        }

    val textSize: Float get() = textPaint.textSize

    fun currentTextStartX(): Float = resolveTextStartX(lineWidth, _model.isAlignedRight)

    fun textStartX(text: String?, isAlignedRight: Boolean): Float =
        resolveTextStartX(textPaint.measureText(text.orEmpty()), isAlignedRight)

    fun setTextSize(size: Float) {
        val needsUpdate = textPaint.textSize != size || syncRenderer.bgPaint.textSize != size
        if (!needsUpdate) return
        textPaint.textSize = size
        syncRenderer.setTextSize(size)
        countdownRenderer.setTextSize(size)
        refreshSizes()
        syncRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
        lineTimelineRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
        invalidate()
    }

    fun setLyric(rawLine: LyricLine?, useLineTimeline: Boolean = false) {
        val line = if (rawLine?.text.isNullOrBlank() && !rawLine.isCountdownLine()) null else rawLine

        reset()
        scrollUnlocked = false
        scrollStarted = false

        _model = line?.normalize()?.createModel() ?: emptyLyricModel()
        activeRenderer = when {
            _model.isCountdownLine() -> countdownRenderer
            useLineTimeline && _model.isPlainText -> lineTimelineRenderer
            _model.isPlainText -> scrollRenderer
            else -> syncRenderer
        }
        refreshSizes()
        updateColorsIfReady()
        requestLayout()
        invalidate()
    }

    /**
     * Replaces a plain-text line while keeping the current marquee state.
     * This is intentionally separate from [setLyric], which is expected to
     * start a new line from its initial delay.
     */
    fun setLyricPreservingScroll(rawLine: LyricLine?, useLineTimeline: Boolean = false) {
        val line = if (rawLine?.text.isNullOrBlank() && !rawLine.isCountdownLine()) null else rawLine
        val model = line?.normalize()?.createModel() ?: emptyLyricModel()
        val targetLineTimeline = useLineTimeline && model.isPlainText && !model.isCountdownLine()
        val currentLineTimeline = activeRenderer === lineTimelineRenderer

        if (!sharedMarqueeClockEnabled) {
            setLyric(rawLine, useLineTimeline)
            return
        }

        if (targetLineTimeline != currentLineTimeline ||
            (!targetLineTimeline && (!model.isPlainText || activeRenderer !== scrollRenderer))
        ) {
            setLyric(rawLine, useLineTimeline)
            return
        }

        if (targetLineTimeline) {
            _model = model
            refreshSizes()
            updateColorsIfReady()
            lineTimelineRenderer.updateLayout(
                _model, lineState, measuredWidth, measuredHeight
            )
            requestLayout()
            invalidate()
            return
        }

        _model = model
        refreshSizes()
        updateColorsIfReady()
        scrollRenderer.synchronizeTo(
            activeTimeMs = sharedMarqueeActiveTimeMs,
            model = _model,
            state = lineState,
            viewWidth = measuredWidth
        )
        if (playbackActive && scrollUnlocked && scrollRenderer.isPlaying) {
            animator.startIfNeeded()
        }
        requestLayout()
        invalidate()
    }

    /**
     * Measures a candidate line with the same model-building rules as [setLyric], without
     * replacing the line that is currently being displayed.
     */
    fun measureLineWidth(rawLine: LyricLine?): Float {
        val line = if (rawLine?.text.isNullOrBlank() && !rawLine.isCountdownLine()) null else rawLine
        val model = line?.normalize()?.createModel() ?: emptyLyricModel()
        if (model.isCountdownLine()) return countdownRenderer.contentWidth()
        model.updateSizes(textPaint)
        return model.width
    }

    fun configureWith(
        text: TextLook, highlight: Highlight, marquee: Marquee,
        gradient: Boolean, fadingEdge: Int, center: Boolean, right: Boolean
    ) {
        this.centerIfPossible = center
        this.rightIfPossible = right
        updateColor(text.color, highlight.background, highlight.foreground)
        setTextSize(text.size)
        textPaint.applyFont(text.typeface, text.fontVariationSettings)
        syncRenderer.setFont(text.typeface, text.fontVariationSettings)
        syncRenderer.isGradientEnabled = gradient
        countdownRenderer.isGradientEnabled = gradient

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
        if (!isStaticPreview && playbackActive) animator.startIfNeeded()
        invalidate()
    }

    fun requestScroll() {
        if (isStaticPreview) return
        doOnAttach {
            if (isStaticPreview) return@doOnAttach
            scrollUnlocked = true
            if (isPlainText && playbackActive) startScrolling()
        }
    }

    fun seekTo(posMs: Long) {
        if (isStaticPreview) return
        if (isPlainText) {
            if (playbackActive) startScrolling()
        } else {
            activeRenderer.seek(_model, lineState, posMs, measuredWidth, measuredHeight)
            if (activeRenderer === countdownRenderer || activeRenderer === lineTimelineRenderer) {
                invalidate()
            }
            if (playbackActive) {
                animator.startIfNeeded()
            } else {
                animator.stop()
                invalidate()
            }
        }
    }

    fun updatePosition(posMs: Long, playbackSpeed: Float = 1f) {
        if (isStaticPreview) return
        if (isWordSync) {
            if (activeRenderer === syncRenderer && syncRenderer.isScrollOnly && !isOverflow) return
            if (activeRenderer === lineTimelineRenderer && !isOverflow) return
            if (playbackActive) {
                activeRenderer.update(
                    _model,
                    lineState,
                    posMs,
                    measuredWidth,
                    measuredHeight,
                    playbackSpeed
                )
                if (activeRenderer === countdownRenderer || activeRenderer === lineTimelineRenderer) {
                    invalidate()
                }
                if (activeRenderer.isPlaying && !activeRenderer.isFinished) {
                    animator.startIfNeeded()
                }
            } else {
                activeRenderer.seek(_model, lineState, posMs, measuredWidth, measuredHeight)
                animator.stop()
                invalidate()
            }
        } else {
            if (playbackActive) startScrolling()
        }
    }

    fun setPlaybackActive(active: Boolean) {
        if (playbackActive == active) {
            if (active) resumePlaybackAnimation() else animator.stop()
            return
        }
        playbackActive = active
        if (!active) {
            animator.stop()
            (activeRenderer as? WordSyncRenderer)?.let { renderer ->
                renderer.freeze(_model, lineState, measuredWidth)
                if (isShown) invalidate()
            }
            (activeRenderer as? LineTimelineRenderer)?.let { renderer ->
                renderer.freeze(_model, lineState, measuredWidth)
                if (isShown) invalidate()
            }
            (activeRenderer as? CountdownDotsRenderer)?.let { renderer ->
                renderer.freeze()
                if (isShown) invalidate()
            }
            return
        }

        resumePlaybackAnimation()
    }

    private fun resumePlaybackAnimation() {
        if (isPlainText) {
            if (!scrollUnlocked) return
            if (!scrollStarted) {
                startScrolling()
            } else if (activeRenderer.isPlaying) {
                animator.startIfNeeded()
            }
        } else if (activeRenderer.isPlaying && !activeRenderer.isFinished) {
            animator.startIfNeeded()
        }
    }

    fun refreshSizes() {
        _model.updateSizes(textPaint)
    }

    fun relayout() {
        when (activeRenderer) {
            syncRenderer -> syncRenderer.updateLayout(_model, lineState, measuredWidth, measuredHeight)
            lineTimelineRenderer -> lineTimelineRenderer.updateLayout(
                _model, lineState, measuredWidth, measuredHeight
            )
        }
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        if (primaryColors.contentEquals(primary) &&
            backgroundColors.contentEquals(background) &&
            highlightColors.contentEquals(highlight)
        ) return

        primaryColors = primary
        backgroundColors = background
        highlightColors = highlight
        applyColors()
    }

    fun reset() {
        animator.stop()
        lineState.reset()
        scrollRenderer.reset(lineState)
        syncRenderer.reset(lineState)
        lineTimelineRenderer.reset(lineState)
        countdownRenderer.reset(lineState)
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
        val charMotionPadding = ceil(syncRenderer.motionBottomPadding(_model)).toInt()
        val textHeight = (textPaint.descent() - textPaint.ascent()).toInt() + charMotionPadding
        setMeasuredDimension(w, resolveSize(textHeight, hSpec))
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (playbackActive && (isVisible || keepPlaybackClockRunningWhenHidden)) {
            resumePlaybackAnimation()
        } else {
            animator.stop()
        }
    }

    /** Applies one canonical media-time sample without carrying local correction error. */
    internal fun synchronizePosition(
        posMs: Long,
        playbackSpeed: Float = 1f,
        activeTimeMs: Long = posMs
    ) {
        if (isStaticPreview) return
        if (!isWordSync) {
            sharedMarqueeActiveTimeMs =
                activeTimeMs.coerceAtLeast(sharedMarqueeOriginActiveTimeMs) -
                        sharedMarqueeOriginActiveTimeMs
            if (playbackActive) startScrolling()
            synchronizeSharedMarquee()
            return
        }
        if (activeRenderer === syncRenderer && syncRenderer.isScrollOnly && !isOverflow) return
        if (activeRenderer === lineTimelineRenderer && !isOverflow) return

        activeRenderer.seek(_model, lineState, posMs, measuredWidth, measuredHeight)
        if (playbackActive) {
            activeRenderer.update(
                _model,
                lineState,
                posMs,
                measuredWidth,
                measuredHeight,
                playbackSpeed
            )
            if (activeRenderer.isPlaying && !activeRenderer.isFinished) {
                animator.startIfNeeded()
            }
        } else {
            animator.stop()
        }
        if (isShown) invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (playbackActive && canAdvancePlaybackClock()) {
            resumePlaybackAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    private fun startScrolling() {
        if (!playbackActive || isStaticPreview || !isPlainText || !scrollUnlocked || scrollStarted) return
        scrollStarted = true
        lineState.reset()
        if (!isOverflow) return
        post {
            if (!scrollStarted || !playbackActive || isStaticPreview) return@post
            if (sharedMarqueeClockEnabled) {
                scrollRenderer.synchronizeTo(
                    activeTimeMs = sharedMarqueeActiveTimeMs,
                    model = _model,
                    state = lineState,
                    viewWidth = measuredWidth
                )
            } else {
                scrollRenderer.update(_model, lineState, 0, measuredWidth, measuredHeight, 1f)
            }
            animator.stop()
            animator.startIfNeeded()
        }
    }

    private fun synchronizeSharedMarquee() {
        if (!sharedMarqueeClockEnabled || !scrollUnlocked || !scrollStarted || !isPlainText) return
        scrollRenderer.synchronizeTo(
            activeTimeMs = sharedMarqueeActiveTimeMs,
            model = _model,
            state = lineState,
            viewWidth = measuredWidth
        )
        if (playbackActive && scrollRenderer.isPlaying) {
            animator.startIfNeeded()
        } else {
            animator.stop()
        }
        if (isShown) invalidate()
    }

    private fun canAdvancePlaybackClock(): Boolean {
        return isAttachedToWindow && (isShown || keepPlaybackClockRunningWhenHidden)
    }

    private fun updateColorsIfReady() {
        if (primaryColors.isNotEmpty() && backgroundColors.isNotEmpty() && highlightColors.isNotEmpty()) {
            applyColors()
        }
    }

    private fun applyColors() {
        updatePlainTextColors()
        syncRenderer.setColors(backgroundColors, highlightColors)
        countdownRenderer.setColors(backgroundColors, highlightColors)
        invalidate()
    }

    private fun updatePlainTextColors() {
        val colors = if (isStaticPreview && backgroundColors.isNotEmpty()) {
            backgroundColors
        } else {
            primaryColors
        }
        textPaint.apply {
            color = colors.firstOrNull() ?: Color.BLACK
            shader = if (colors.size > 1) makeRainbowShader(colors) else null
        }
    }

    private fun resolveTextStartX(textWidth: Float, isAlignedRight: Boolean): Float {
        val availableWidth = measuredWidth.toFloat()
        return when {
            textWidth >= availableWidth -> 0f
            isAlignedRight || rightIfPossible -> availableWidth - textWidth
            centerIfPossible -> (availableWidth - textWidth) / 2f
            else -> 0f
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
        rainbowShader = LyricGradientShader.create(0f, lineWidth, colors)
        rainbowShaderHash = hash
        rainbowShaderWidth = lineWidth
        return rainbowShader!!
    }

    private inner class Animator : Choreographer.FrameCallback {
        private var running = false
        private var lastFrameNanos = 0L

        fun startIfNeeded() {
            if (playbackActive && !running && canAdvancePlaybackClock()) {
                running = true
                lastFrameNanos = 0L
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun stop() {
            running = false
            Choreographer.getInstance().removeFrameCallback(this)
            lastFrameNanos = 0L
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running || !playbackActive || !canAdvancePlaybackClock()) {
                running = false
                return
            }

            val deltaNanos = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
            lastFrameNanos = frameTimeNanos

            val renderer = activeRenderer
            val changed = renderer.step(deltaNanos, _model, lineState, measuredWidth)
            if (changed && isShown) postInvalidateOnAnimation()

            if (running && renderer.isPlaying) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
                lastFrameNanos = 0L
            }
        }
    }
}


