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
import android.view.ViewParent
import androidx.core.graphics.withSave
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

open class SpaceGateLyricLineView(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), UpdatableColor {

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(10.dp)
    }

    // Space Gate synchronization settings
    var isRightSide = false
    var siblingView: SpaceGateLyricLineView? = null
    var spaceGateEnabled = true

    private var splitGradientPeer: SpaceGateLyricLineView? = null
    private var isRightSplitGradientSide = false


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

    fun setSplitGradientPeer(isRightSide: Boolean, peer: SpaceGateLyricLineView?) {
        if (isRightSplitGradientSide == isRightSide && splitGradientPeer === peer) return
        isRightSplitGradientSide = isRightSide
        splitGradientPeer = peer
        clearSplitGradientShaders()
    }

    private fun splitGradientPeerWidth(): Float = splitGradientPeer?.lineWidth
        ?.takeIf { it.isFinite() && it > 0f }
        ?: 0f

    internal fun resolveSplitGradientStartX(): Float =
        if (isRightSplitGradientSide) -splitGradientPeerWidth() else 0f

    internal fun resolveSplitGradientEndX(localWidth: Float): Float =
        localWidth.coerceAtLeast(0f) +
            if (isRightSplitGradientSide) 0f else splitGradientPeerWidth()

    val isPlainText: Boolean get() = activeRenderer === scrollRenderer
    val isWordSync: Boolean get() = activeRenderer !== scrollRenderer
    val isOverflow: Boolean get() = lineWidth > getSpaceGateVirtualWidth()
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
            siblingView?.requestLayout()
            siblingView?.invalidate()
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

    internal val lineState = LineState()
    internal val scrollRenderer = SpaceGateScrollTextRenderer()
    internal val syncRenderer = SpaceGateWordSyncRenderer(this)
    internal val lineTimelineRenderer = LineTimelineRenderer()
    private val countdownRenderer = CountdownDotsRenderer()
    private val animator = Animator()

    internal var activeRenderer: LineRenderer = scrollRenderer

    private var primaryColors = intArrayOf()
    private var backgroundColors = intArrayOf()
    private var highlightColors = intArrayOf()

    private var ghostSpacing: Float = 40f.dp
    private var scrollStarted = false
    private var scrollUnlocked = false
    private var playbackActive = true

    /** See [LyricLineView.keepPlaybackClockRunningWhenHidden]. */
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
            siblingView?.invalidate()
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
        syncRenderer.updateLayout(_model, lineState, getSpaceGateVirtualWidth(), measuredHeight)
        lineTimelineRenderer.updateLayout(
            _model, lineState, getSpaceGateVirtualWidth(), measuredHeight
        )
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
        val previousModel = _model
        val model = line?.normalize()?.createModel() ?: emptyLyricModel()
        val targetLineTimeline = useLineTimeline && model.isPlainText && !model.isCountdownLine()
        val currentLineTimeline = activeRenderer === lineTimelineRenderer

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
                _model, lineState, getSpaceGateVirtualWidth(), measuredHeight
            )
            requestLayout()
            invalidate()
            siblingView?.invalidate()
            return
        }

        _model = model
        refreshSizes()
        updateColorsIfReady()
        scrollRenderer.updateModelPreservingScroll(
            previousWidth = previousModel.width,
            model = _model,
            state = lineState,
            viewWidth = getSpaceGateVirtualWidth()
        )
        if (playbackActive && scrollUnlocked && _model.width > getSpaceGateVirtualWidth()) {
            scrollRenderer.resumeIfNeeded()
            animator.startIfNeeded()
        }
        requestLayout()
        invalidate()
        siblingView?.invalidate()
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

        if (spaceGateEnabled || fadingEdge <= 0) {
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
        if (!isRightSide && spaceGateEnabled) return // Slave view delegates animation to Master
        if (isPlainText) {
            if (playbackActive) startScrolling()
        } else {
            activeRenderer.seek(
                _model,
                lineState,
                posMs,
                getSpaceGateVirtualWidth(),
                measuredHeight
            )
            if (activeRenderer === countdownRenderer || activeRenderer === lineTimelineRenderer) {
                invalidate()
                siblingView?.invalidate()
            }
            if (playbackActive) {
                animator.startIfNeeded()
            } else {
                animator.stop()
                invalidate()
                siblingView?.invalidate()
            }
        }
    }

    fun updatePosition(posMs: Long, playbackSpeed: Float = 1f) {
        if (isStaticPreview) return
        if (!isRightSide && spaceGateEnabled) return // Slave view delegates animation to Master
        if (isWordSync) {
            if (activeRenderer === syncRenderer && syncRenderer.isScrollOnly && !isOverflow) return
            if (activeRenderer === lineTimelineRenderer && !isOverflow) return
            if (playbackActive) {
                activeRenderer.update(
                    _model,
                    lineState,
                    posMs,
                    getSpaceGateVirtualWidth(),
                    measuredHeight,
                    playbackSpeed
                )
                if (activeRenderer === countdownRenderer || activeRenderer === lineTimelineRenderer) {
                    invalidate()
                    siblingView?.invalidate()
                }
                if (activeRenderer.isPlaying && !activeRenderer.isFinished) {
                    animator.startIfNeeded()
                }
            } else {
                activeRenderer.seek(
                    _model,
                    lineState,
                    posMs,
                    getSpaceGateVirtualWidth(),
                    measuredHeight
                )
                animator.stop()
                invalidate()
                siblingView?.invalidate()
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
            (activeRenderer as? SpaceGateWordSyncRenderer)?.let { renderer ->
                renderer.freeze(_model, lineState, getSpaceGateVirtualWidth())
                if (isShown) invalidate()
                siblingView?.takeIf { it.isShown }?.invalidate()
            }
            (activeRenderer as? LineTimelineRenderer)?.let { renderer ->
                renderer.freeze(_model, lineState, getSpaceGateVirtualWidth())
                if (isShown) invalidate()
                siblingView?.takeIf { it.isShown }?.invalidate()
            }
            (activeRenderer as? CountdownDotsRenderer)?.let { renderer ->
                renderer.freeze()
                if (isShown) invalidate()
                siblingView?.takeIf { it.isShown }?.invalidate()
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
        splitGradientPeer?.onSplitGradientGeometryChanged()
    }

    fun relayout() {
        when (activeRenderer) {
            syncRenderer -> syncRenderer.updateLayout(
                _model,
                lineState,
                getSpaceGateVirtualWidth(),
                measuredHeight
            )
            lineTimelineRenderer -> lineTimelineRenderer.updateLayout(
                _model,
                lineState,
                getSpaceGateVirtualWidth(),
                measuredHeight
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
        if (!spaceGateEnabled) {
            activeRenderer.draw(canvas, _model, textPaint, lineState, measuredWidth, measuredHeight)
            return
        }

        val master = if (isRightSide) this else siblingView
        if (master == null) {
            activeRenderer.draw(canvas, _model, textPaint, lineState, measuredWidth, measuredHeight)
            return
        }

        val sibling = siblingView
        val (leftView, rightView) = if (isRightSide) {
            Pair(sibling ?: this, this)
        } else {
            Pair(this, sibling ?: this)
        }

        val virtualWidth = leftView.width + rightView.width
        val translationX = if (isRightSide) -leftView.width.toFloat() else 0f

        // Slave View copies Master View's drawing offset and renderer progress state
        if (!isRightSide) {
            this.lineState.scrollOffset = master.lineState.scrollOffset
            this.lineState.isScrollFinished = master.lineState.isScrollFinished
            val myRenderer = this.activeRenderer
            val masterRenderer = master.activeRenderer
            if (myRenderer is SpaceGateWordSyncRenderer && masterRenderer is SpaceGateWordSyncRenderer) {
                myRenderer.syncFrom(masterRenderer)
            } else if (myRenderer is SpaceGateScrollTextRenderer && masterRenderer is SpaceGateScrollTextRenderer) {
                myRenderer.syncFrom(masterRenderer)
            } else if (myRenderer is LineTimelineRenderer && masterRenderer is LineTimelineRenderer) {
                myRenderer.syncFrom(masterRenderer)
            } else if (myRenderer is CountdownDotsRenderer && masterRenderer is CountdownDotsRenderer) {
                myRenderer.syncFrom(masterRenderer)
            }
        }

        canvas.withSave {
            translate(translationX, 0f)
            activeRenderer.draw(canvas, _model, textPaint, lineState, virtualWidth, measuredHeight)
        }

        // If we are Master, request Slave View to redraw in the next frame callback
        if (isRightSide) {
            sibling?.postInvalidateOnAnimation()
        }
    }

    private fun findGateRoot(view: View): View? {
        var current: ViewParent? = view.parent
        while (current != null && current.javaClass.simpleName != "DynamicIslandContentView") {
            current = current.parent
        }
        return current as? View
    }

    private fun getSpaceGateVirtualWidth(): Int {
        if (!spaceGateEnabled) return measuredWidth
        if (isRightSide) this else siblingView ?: return measuredWidth

        val sibling = siblingView
        val (leftView, rightView) = if (isRightSide) {
            Pair(sibling ?: this, this)
        } else {
            Pair(this, sibling ?: this)
        }

        val virtualWidth = leftView.width + rightView.width
        return maxOf(measuredWidth, virtualWidth)
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

    /** Applies one canonical media-time sample without carrying local correction error. */
    internal fun synchronizePosition(posMs: Long, playbackSpeed: Float = 1f) {
        if (isStaticPreview) return
        if (!isRightSide && spaceGateEnabled) return
        if (!isWordSync) {
            if (playbackActive) startScrolling()
            return
        }
        if (activeRenderer === syncRenderer && syncRenderer.isScrollOnly && !isOverflow) return
        if (activeRenderer === lineTimelineRenderer && !isOverflow) return

        val virtualWidth = getSpaceGateVirtualWidth()
        activeRenderer.seek(_model, lineState, posMs, virtualWidth, measuredHeight)
        if (playbackActive) {
            activeRenderer.update(
                _model,
                lineState,
                posMs,
                virtualWidth,
                measuredHeight,
                playbackSpeed
            )
            if (activeRenderer.isPlaying && !activeRenderer.isFinished) {
                animator.startIfNeeded()
            }
        } else {
            animator.stop()
        }
        if (isShown) {
            invalidate()
            siblingView?.takeIf { it.isShown }?.invalidate()
        }
    }

    override fun getLeftFadingEdgeStrength(): Float {
        val vw = getSpaceGateVirtualWidth()
        if (lineWidth <= vw || horizontalFadingEdgeLength <= 0) return 0f
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
        val vw = getSpaceGateVirtualWidth()
        if (lineWidth <= vw || horizontalFadingEdgeLength <= 0) return 0f
        val viewW = vw.toFloat()
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
        if (!isRightSide && spaceGateEnabled) return // Slave delegates animation
        if (!playbackActive || isStaticPreview || !isPlainText || !scrollUnlocked || scrollStarted) return
        scrollStarted = true
        lineState.reset()
        if (!isOverflow) return
        post {
            if (!scrollStarted || !playbackActive || isStaticPreview) return@post
            scrollRenderer.update(
                _model,
                lineState,
                0,
                getSpaceGateVirtualWidth(),
                measuredHeight,
                1f
            )
            animator.stop()
            animator.startIfNeeded()
        }
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

    private var rainbowShader: Shader? = null
    private var rainbowShaderHash = 0
    private var rainbowShaderStartX = Float.NaN
    private var rainbowShaderEndX = Float.NaN

    private fun makeRainbowShader(colors: IntArray): Shader {
        val hash = colors.contentHashCode()
        val startX = resolveSplitGradientStartX()
        val endX = resolveSplitGradientEndX(lineWidth)
        if (rainbowShader != null && rainbowShaderHash == hash &&
            rainbowShaderStartX == startX && rainbowShaderEndX == endX
        ) {
            return rainbowShader!!
        }
        rainbowShader = LyricGradientShader.create(startX, endX, colors)
        rainbowShaderHash = hash
        rainbowShaderStartX = startX
        rainbowShaderEndX = endX
        return rainbowShader!!
    }

    private fun clearSplitGradientShaders() {
        rainbowShader = null
        rainbowShaderStartX = Float.NaN
        rainbowShaderEndX = Float.NaN
        syncRenderer.clearGradientShaderCache()
        updatePlainTextColors()
        invalidate()
    }

    private fun onSplitGradientGeometryChanged() {
        clearSplitGradientShaders()
    }

    private inner class Animator : Choreographer.FrameCallback {
        private var running = false
        private var lastFrameNanos = 0L

        fun startIfNeeded() {
            if (!isRightSide && spaceGateEnabled) return // Slave doesn't run frame callback
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

            val virtualWidth = getSpaceGateVirtualWidth()
            val deltaNanos = if (lastFrameNanos == 0L) 0L else frameTimeNanos - lastFrameNanos
            lastFrameNanos = frameTimeNanos

            val renderer = activeRenderer
            val changed = renderer.step(deltaNanos, _model, lineState, virtualWidth)
            if (changed && isShown) {
                postInvalidateOnAnimation()
                siblingView?.takeIf { it.isShown }?.postInvalidateOnAnimation()
            }

            if (running && renderer.isPlaying) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
                lastFrameNanos = 0L
            }
        }
    }
}


