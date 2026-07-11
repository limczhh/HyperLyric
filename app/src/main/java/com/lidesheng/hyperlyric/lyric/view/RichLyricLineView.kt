/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import android.annotation.SuppressLint
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.graphics.withScale
import androidx.core.view.forEach
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.line.LyricLineView

@SuppressLint("ViewConstructor")
class RichLyricLineView(
    context: Context,
    var displayTranslation: Boolean = true,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
    var displayRoma: Boolean = true
) : LinearLayout(context), UpdatableColor {

    val main = LyricLineView(context)
    val secondary = LyricLineView(context).apply { visibleIfChanged = false }

    var alwaysShowSecondary = false

    var renderScale = 1.0f
        private set

    private val assembler = LyricLineAssembler(
        displayTranslation, displayRoma,
        enableRelativeProgress, enableRelativeProgressHighlight
    )

    private var animationTransition = false
    private var pendingLine: IRichLyricLine? = null
    private var pendingPosition: Long? = null
    private var requestMarquee = false
    private var lastPosition: Long = Long.MIN_VALUE

    var rawLine: IRichLyricLine? = null
    private var currentMainText: String? = null
    private var secondaryIsNextLinePreview = false
    private var nextLineTransitionRunning = false
    private var nextLineTransitionGeneration = 0

    var line: IRichLyricLine?
        get() = rawLine
        set(value) {
            rawLine = value
            lastPosition = Long.MIN_VALUE
            requestMarquee = false
            if (animationTransition) {
                pendingLine = value
            } else {
                refreshLines()
            }
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(secondary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateLayoutTransitionX()
    }

    fun reset() {
        cancelNextLinePromotion()
        line = null
        renderScale = 1.0f
        animationTransition = false
        pendingLine = null
        pendingPosition = null
        lastPosition = Long.MIN_VALUE
        currentMainText = null
        secondaryIsNextLinePreview = false
        alwaysShowSecondary = false
        refreshLines()
    }

    fun beginAnimationTransition() {
        cancelNextLinePromotion()
        animationTransition = true
    }

    fun endAnimationTransition() {
        animationTransition = false
        if (pendingLine != null) {
            refreshLines()
            pendingPosition?.let { setPosition(it) }
        }
        pendingLine = null
        pendingPosition = null
    }

    fun setTransitionConfig(config: String?) {
        updateLayoutTransitionX(config)
    }

    fun notifyLineChanged() = refreshLines()

    fun seekTo(position: Long) {
        if (animationTransition) {
            pendingPosition = position; return
        }
        main.seekTo(position)
        secondary.seekTo(position)
    }

    fun setPosition(position: Long) {
        if (animationTransition) {
            pendingPosition = position; return
        }
        if (lastPosition == position) return
        lastPosition = position
        main.updatePosition(position)
        secondary.updatePosition(position)
    }

    fun setPlaybackActive(active: Boolean) {
        main.setPlaybackActive(active)
        secondary.setPlaybackActive(active)
    }

    fun requestStartMarquee() {
        requestMarquee = true
        main.requestScroll()
        if (!secondaryIsNextLinePreview) secondary.requestScroll()
    }

    /**
     * 覆盖歌曲信息行的跑马灯参数（与歌词参数独立）
     */
    fun setMetadataMarqueeConfig(
        speed: Float, initialDelay: Int, loopDelay: Int,
        repeatCount: Int, stopAtEnd: Boolean
    ) {
        listOf(main, secondary).forEach {
            it.setMarqueeSpeed(speed)
            it.setMarqueeInitialDelay(initialDelay)
            it.setMarqueeLoopDelay(loopDelay)
            it.setMarqueeRepeatCount(repeatCount)
            it.setMarqueeStopAtEnd(stopAtEnd)
        }
    }

    fun setStyle(style: LyricViewStyle) {
        assembler.updateFlags(
            displayTranslation, displayRoma,
            style.primary.relativeProgress, style.primary.relativeHighlight
        )
        enableRelativeProgress = style.primary.relativeProgress
        enableRelativeProgressHighlight = style.primary.relativeHighlight

        setTransitionConfig(style.transitionConfig)

        applyLineStyle(
            main,
            style.primary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion,
            style.centerIfPossible
        )
        applyLineStyle(
            secondary,
            style.secondary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion,
            style.centerIfPossible
        )
    }

    override fun updateColor(primary: IntArray, background: IntArray, highlight: IntArray) {
        forEach { if (it is UpdatableColor) it.updateColor(primary, background, highlight) }
    }

    fun setMainLyricPlayListener(listener: LyricPlayListener?) {
        main.playListener = listener
    }

    fun setSecondaryLyricPlayListener(listener: LyricPlayListener?) {
        secondary.playListener = listener
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        if (renderScale != 1.0f && renderScale > 0) {
            val origW = MeasureSpec.getSize(wSpec)
            val mode = MeasureSpec.getMode(wSpec)
            val compW = (origW / renderScale).toInt()
            super.onMeasure(MeasureSpec.makeMeasureSpec(compW, mode), hSpec)
            setMeasuredDimension(origW, measuredHeight)
        } else {
            super.onMeasure(wSpec, hSpec)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (renderScale != 1.0f) {
            canvas.withScale(renderScale, renderScale, 0f, height / 2f) {
                super.dispatchDraw(this)
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }

    fun setRenderScale(scale: Float) {
        if (renderScale != scale) {
            renderScale = scale
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reset()
    }

    private var oldLine: IRichLyricLine? = null

    private fun refreshLines(allowNextLinePromotion: Boolean = true, bypassIdentityCheck: Boolean = false) {
        if (nextLineTransitionRunning) return
        if (!bypassIdentityCheck && oldLine === line && line.isTitleLine()) return
        oldLine = line

        val mainResult = assembler.buildMain(line)
        val secResult = assembler.buildSecondary(line)

        val shouldPromote = allowNextLinePromotion &&
                secondaryIsNextLinePreview && secResult.isNextLinePreview &&
                currentMainText != null && currentMainText != mainResult.line.text &&
                secondary.model.text == mainResult.line.text &&
                isAttachedToWindow && main.height > 0 && secondary.height > 0
        if (shouldPromote) {
            animateNextLinePromotion()
            return
        }

        main.setLyric(mainResult.line)
        main.isScrollOnly = mainResult.isScrollOnly
        currentMainText = mainResult.line.text

        alwaysShowSecondary = secResult.alwaysShow
        secondaryIsNextLinePreview = secResult.isNextLinePreview
        secondary.visibleIfChanged = secResult.alwaysShow
        secondary.isStaticPreview = secResult.isNextLinePreview
        secondary.setLyric(secResult.line)
        secondary.isScrollOnly = if (secResult.isNextLinePreview) false else secResult.isScrollOnly

        if (requestMarquee) requestStartMarquee()
    }

    private fun applyLineStyle(
        view: LyricLineView, text: TextLook, highlight: Highlight,
        marquee: Marquee, gradient: Boolean, fadingEdge: Int, wordMotion: WordMotion,
        centerIfPossible: Boolean
    ) {
        view.wordMotion = wordMotion
        view.configureWith(text, highlight, marquee, gradient, fadingEdge, centerIfPossible)
    }

    private fun updateLayoutTransitionX(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransition = LayoutTransitionX(config).apply { setAnimateParentHierarchy(true) }
    }

    private fun animateNextLinePromotion() {
        val generation = ++nextLineTransitionGeneration
        nextLineTransitionRunning = true
        val targetTranslationY = (main.top - secondary.top).toFloat()
        val targetTranslationX = (main.left - secondary.left).toFloat()
        val targetScale = (main.textSize / secondary.textSize).coerceIn(0.5f, 2f)

        main.animate().cancel()
        secondary.animate().cancel()
        secondary.pivotX = 0f
        secondary.pivotY = 0f
        main.animate()
            .alpha(0f)
            .translationY(-main.height * 0.65f)
            .setDuration(NEXT_LINE_PROMOTION_DURATION)
            .withLayer()
            .start()
        secondary.animate()
            .translationX(targetTranslationX)
            .translationY(targetTranslationY)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(NEXT_LINE_PROMOTION_DURATION)
            .withLayer()
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (generation != nextLineTransitionGeneration) return
                    finishNextLinePromotion()
                }
            })
            .start()
    }

    private fun finishNextLinePromotion() {
        clearNextLineTransitionState()
        nextLineTransitionRunning = false
        refreshLines(allowNextLinePromotion = false, bypassIdentityCheck = true)
        if (secondaryIsNextLinePreview) {
            secondary.alpha = 0f
            secondary.animate()
                .alpha(1f)
                .setDuration(NEXT_LINE_PREVIEW_FADE_DURATION)
                .withLayer()
                .setListener(null)
                .start()
        }
    }

    private fun cancelNextLinePromotion() {
        nextLineTransitionGeneration++
        main.animate().setListener(null)
        secondary.animate().setListener(null)
        main.animate().cancel()
        secondary.animate().cancel()
        nextLineTransitionRunning = false
        clearNextLineTransitionState()
    }

    private fun clearNextLineTransitionState() {
        main.alpha = 1f
        main.translationY = 0f
        secondary.alpha = 1f
        secondary.translationX = 0f
        secondary.translationY = 0f
        secondary.scaleX = 1f
        secondary.scaleY = 1f
    }

    private companion object {
        const val NEXT_LINE_PROMOTION_DURATION = 220L
        const val NEXT_LINE_PREVIEW_FADE_DURATION = 140L
    }
}

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean("TitleLine", false) == true


