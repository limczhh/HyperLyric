/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.lidesheng.hyperlyric.lyric.view.LyricPlayListener
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

internal class SpaceGateWordSyncRenderer(private val view: SpaceGateLyricLineView) : LineRenderer {

    val bgPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    val hlPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val progressController = WordProgressController()
    private val scrollStepper = ScrollStepper()
    private val textDrawer = TextDrawer()

    var isScrollOnly = false
    override var centerIfPossible = false
    override var rightIfPossible = false

    var isCharMotionEnabled = true
    var isWordRevealEnabled = false
    var wordRevealAnimation = WordRevealAnimation.NONE
    var wordRevealSpeedRate = 100
    private val baseRevealDurationMs = view.resources.getInteger(android.R.integer.config_shortAnimTime)
    private val revealDurationMs: Int
        get() = (baseRevealDurationMs.toLong() * 100L / wordRevealSpeedRate)
            .coerceAtLeast(1L)
            .toInt()

    var hasSourceWordTiming: Boolean
        get() = progressController.sustainAware
        set(value) {
            progressController.sustainAware = value
        }

    var hasSimulatedWordTiming = false

    fun motionBottomPadding(model: LyricModel): Float =
        if (isWordRevealEnabled && hasRevealWordTiming && !isScrollOnly) {
            0f
        } else if (isCharMotionEnabled && !isScrollOnly) {
            textDrawer.motionBottomPadding(model, bgPaint.textSize)
        } else {
            0f
        }

    var cjkMotionLiftFactor: Float
        get() = textDrawer.cjkLiftFactor
        set(value) {
            textDrawer.cjkLiftFactor = value
        }

    var cjkMotionWaveFactor: Float
        get() = textDrawer.cjkWaveFactor
        set(value) {
            textDrawer.cjkWaveFactor = value
        }

    var latinMotionByCharacter: Boolean
        get() = textDrawer.latinByCharacter
        set(value) {
            textDrawer.latinByCharacter = value
        }

    var latinMotionLiftFactor: Float
        get() = textDrawer.latinLiftFactor
        set(value) {
            textDrawer.latinLiftFactor = value
        }

    var latinMotionWaveFactor: Float
        get() = textDrawer.latinWaveFactor
        set(value) {
            textDrawer.latinWaveFactor = value
        }

    var isGradientEnabled = true
        set(value) {
            if (field != value) {
                field = value
                textDrawer.clearShaderCache()
            }
        }

    var playListener: LyricPlayListener? = null
        set(value) {
            field = value
            _playListener = value ?: NoOpPlayListener
        }

    private var _playListener: LyricPlayListener = NoOpPlayListener

    val lastPosition: Long get() = progressController.lastPosition

    override val isPlaying get() = progressController.animator.isAnimating
    override val isFinished get() = progressController.animator.hasFinished
    override val isStarted get() = progressController.animator.hasStarted

    fun setTextSize(size: Float) {
        bgPaint.textSize = size
        hlPaint.textSize = size
        textDrawer.updateMetrics(bgPaint)
        textDrawer.clearShaderCache()
    }

    fun setFont(tf: Typeface?, variationSettings: String?) {
        bgPaint.applyFont(tf, variationSettings)
        hlPaint.applyFont(tf, variationSettings)
        textDrawer.updateMetrics(bgPaint)
        textDrawer.clearShaderCache()
    }

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgPaint.color = background[0]
        if (highlight.isNotEmpty()) hlPaint.color = highlight[0]
        textDrawer.setColors(background, highlight)
        textDrawer.clearShaderCache()
    }

    fun updateLayout(model: LyricModel, state: LineState, viewWidth: Int, viewHeight: Int) {
        textDrawer.updateMetrics(bgPaint)
        if (progressController.animator.hasFinished) {
            progressController.animator.jumpTo(model.width)
        }
        updateScrollState(model, state, viewWidth)
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        progressController.seek(posMs, model)
        updateScrollState(model, state, viewWidth)
        notifyProgress(model)
    }

    override fun update(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playbackSpeed: Float
    ) {
        val changed = progressController.update(posMs, playbackSpeed, model)
        if (changed && !progressController.animator.isAnimating) {
            updateScrollState(model, state, viewWidth)
            notifyProgress(model)
            view.postInvalidateOnAnimation()
        }
    }

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int
    ): Boolean {
        if (progressController.step(deltaNanos)) {
            updateScrollState(model, state, viewWidth)
            notifyProgress(model)
            return true
        }
        return false
    }

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        textDrawer.draw(
            canvas, model, viewWidth, viewHeight,
            state.scrollOffset, model.width > viewWidth,
            progressController.animator.currentWidth,
            progressController.currentPositionMs,
            isWordRevealEnabled && hasRevealWordTiming,
            wordRevealAnimation, revealDurationMs,
            isGradientEnabled, isScrollOnly, isCharMotionEnabled,
            centerIfPossible, rightIfPossible,
            bgPaint, hlPaint, paint,
            view.resolveSplitGradientStartX(),
            view.resolveSplitGradientEndX(model.width)
        )
    }

    private val hasRevealWordTiming: Boolean
        get() = hasSourceWordTiming || hasSimulatedWordTiming

    fun clearGradientShaderCache() {
        textDrawer.clearShaderCache()
    }

    override fun reset(state: LineState) {
        progressController.reset()
        state.reset()
        textDrawer.clearShaderCache()
    }

    fun freeze(model: LyricModel, state: LineState, viewWidth: Int) {
        progressController.freeze()
        updateScrollState(model, state, viewWidth)
        notifyProgress(model)
    }

    private fun updateScrollState(model: LyricModel, state: LineState, viewWidth: Int) {
        val offset = scrollStepper.compute(
            progressController.animator.currentWidth, model.width,
            viewWidth.toFloat(), progressController.animator.hasFinished, state.isScrollFinished
        )
        state.scrollOffset = offset
        if (progressController.animator.hasFinished) {
            state.isScrollFinished = true
        }
    }

    private val dummyLyricLineView by lazy { LyricLineView(view.context) }

    private fun notifyProgress(model: LyricModel) {
        val animator = progressController.animator
        val current = animator.currentWidth
        val total = model.width

        if (!animator.hasStarted && current > 0f) {
            animator.hasStarted = true
            _playListener.onPlayStarted(dummyLyricLineView)
        }
        if (!animator.hasFinished && current >= total) {
            animator.hasFinished = true
            _playListener.onPlayEnded(dummyLyricLineView)
        }
        _playListener.onPlayProgress(dummyLyricLineView, total, current)
    }

    fun syncFrom(other: SpaceGateWordSyncRenderer) {
        progressController.syncFrom(other.progressController)
    }

    companion object {
        private val NoOpPlayListener = object : LyricPlayListener {
            override fun onPlayStarted(view: LyricLineView) {}
            override fun onPlayEnded(view: LyricLineView) {}
            override fun onPlayProgress(view: LyricLineView, total: Float, progress: Float) {}
        }
    }
}

