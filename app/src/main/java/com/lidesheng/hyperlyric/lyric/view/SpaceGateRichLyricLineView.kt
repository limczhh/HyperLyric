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
import com.lidesheng.hyperlyric.lyric.view.line.SpaceGateLyricLineView
import com.lidesheng.hyperlyric.lyric.view.yoyo.YoYoAnimation

@SuppressLint("ViewConstructor")
class SpaceGateRichLyricLineView(
    context: Context,
    var displayTranslation: Boolean = true,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
    var displayRoma: Boolean = true
) : LinearLayout(context), UpdatableColor {

    val main = SpaceGateLyricLineView(context)
    val secondary = SpaceGateLyricLineView(context).apply { visibleIfChanged = false }

    var alwaysShowSecondary = false

    var renderScale = 1.0f
        private set

    private val assembler = LyricLineAssembler(
        displayTranslation, displayRoma,
        enableRelativeProgress, enableRelativeProgressHighlight
    )
    private var displayLineByLine = false

    private var pendingMainLineWillApply: ((Float) -> Boolean)? = null
    private var pendingMainLineApplied: (() -> Unit)? = null
    private var pendingMainLineCancelled: (() -> Unit)? = null
    private var requestMarquee = false
    private var lastPosition: Long = Long.MIN_VALUE
    private var lastPlaybackSpeed = Float.NaN

    var rawLine: IRichLyricLine? = null
    private var currentMainText: String? = null
    private var secondaryIsNextLinePreview = false
    private var nextLineTransitionRunning = false
    private var nextLineTransitionGeneration = 0

    var line: IRichLyricLine?
        get() = rawLine
        set(value) {
            setLineInternal(value, null, null, null)
        }

    fun setLineWithCallbacks(
        value: IRichLyricLine?,
        onMainLineWillApply: ((Float) -> Boolean)? = null,
        onMainLineApplied: (() -> Unit)? = null,
        onMainLineCancelled: (() -> Unit)? = null
    ) {
        setLineInternal(value, onMainLineWillApply, onMainLineApplied, onMainLineCancelled)
    }

    fun updateMetadataLine(value: IRichLyricLine?) {
        setLineInternal(value, null, null, null, preserveMarquee = true)
    }

    private fun setLineInternal(
        value: IRichLyricLine?,
        onMainLineWillApply: ((Float) -> Boolean)?,
        onMainLineApplied: (() -> Unit)?,
        onMainLineCancelled: (() -> Unit)?,
        preserveMarquee: Boolean = false
    ) {
        val cancellation = pendingMainLineCancelled
        pendingMainLineWillApply = null
        pendingMainLineApplied = null
        pendingMainLineCancelled = null
        preflightReadyGeneration = -1
        cancellation?.invoke()

        lineGeneration++
        pendingMainLineWillApply = onMainLineWillApply
        pendingMainLineApplied = onMainLineApplied
        pendingMainLineCancelled = onMainLineCancelled
        rawLine = value
        if (!preserveMarquee) {
            lastPosition = Long.MIN_VALUE
            lastPlaybackSpeed = Float.NaN
            requestMarquee = false
        }
        refreshLines(preserveMarquee = preserveMarquee)
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(secondary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateLayoutTransitionX()
    }

    fun setSpaceGateConfig(isRightSide: Boolean, sibling: SpaceGateRichLyricLineView?) {
        main.isRightSide = isRightSide
        main.siblingView = sibling?.main
        secondary.isRightSide = isRightSide
        secondary.siblingView = sibling?.secondary
    }

    fun setSplitGradientConfig(isRightSide: Boolean, sibling: SpaceGateRichLyricLineView?) {
        main.setSplitGradientPeer(isRightSide, sibling?.main)
        secondary.setSplitGradientPeer(isRightSide, sibling?.secondary)
    }

    fun reset() {
        cancelNextLinePromotion()
        line = null
        renderScale = 1.0f
        lastPosition = Long.MIN_VALUE
        lastPlaybackSpeed = Float.NaN
        currentMainText = null
        secondaryIsNextLinePreview = false
        alwaysShowSecondary = false
        refreshLines()
    }

    fun setTransitionConfig(config: String?) {
        updateLayoutTransitionX(config)
    }

    fun notifyLineChanged() = refreshLines()

    fun seekTo(position: Long) {
        main.seekTo(position)
        secondary.seekTo(position)
    }

    fun setPosition(position: Long, playbackSpeed: Float = 1f) {
        val resolvedSpeed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) {
            playbackSpeed
        } else {
            1f
        }
        if (lastPosition == position && lastPlaybackSpeed == resolvedSpeed) return
        lastPosition = position
        lastPlaybackSpeed = resolvedSpeed
        main.updatePosition(position, resolvedSpeed)
        secondary.updatePosition(position, resolvedSpeed)
    }

    internal fun synchronizePosition(position: Long, playbackSpeed: Float = 1f) {
        val resolvedSpeed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) {
            playbackSpeed
        } else {
            1f
        }
        lastPosition = position
        lastPlaybackSpeed = resolvedSpeed
        main.synchronizePosition(position, resolvedSpeed)
        secondary.synchronizePosition(position, resolvedSpeed)
    }

    fun setPlaybackActive(active: Boolean) {
        main.setPlaybackActive(active)
        secondary.setPlaybackActive(active)
    }

    internal fun keepPlaybackClockRunningWhenHidden(enabled: Boolean) {
        main.keepPlaybackClockRunningWhenHidden = enabled
        secondary.keepPlaybackClockRunningWhenHidden = enabled
    }

    fun requestStartMarquee() {
        requestMarquee = true
        main.requestScroll()
        if (!secondaryIsNextLinePreview) secondary.requestScroll()
    }

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

    fun setStyle(
        style: LyricViewStyle,
        isLeftSplitSide: Boolean = false
    ) {
        displayLineByLine = style.lineDisplay
        assembler.updateFlags(
            displayTranslation, displayRoma,
            style.primary.relativeProgress, style.primary.relativeHighlight,
            displayLineByLine
        )
        enableRelativeProgress = style.primary.relativeProgress
        enableRelativeProgressHighlight = style.primary.relativeHighlight

        setTransitionConfig(style.transitionConfig)

        // 分离歌词的“居中”以左右容器的交界线为中心：左侧贴右，右侧保持默认左对齐。
        val centerIfPossible = false
        val rightIfPossible = if (style.centerIfPossible) {
            isLeftSplitSide
        } else {
            style.rightIfPossible
        }

        applyLineStyle(
            main,
            style.primary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion,
            centerIfPossible,
            rightIfPossible
        )
        applyLineStyle(
            secondary,
            style.secondary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion,
            centerIfPossible,
            rightIfPossible
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
        YoYoAnimation.cancelAnimation(this)
        super.onDetachedFromWindow()
        reset()
    }

    private var oldLine: IRichLyricLine? = null
    private var lineGeneration = 0
    private var preflightReadyGeneration = -1

    private fun refreshLines(
        allowNextLinePromotion: Boolean = true,
        bypassIdentityCheck: Boolean = false,
        skipMainLinePreflight: Boolean = false,
        preserveMarquee: Boolean = false
    ) {
        if (nextLineTransitionRunning) return
        if (skipMainLinePreflight) {
            preflightReadyGeneration = -1
        } else if (preflightReadyGeneration == lineGeneration) {
            return
        }
        if (!bypassIdentityCheck && oldLine === line && line.isTitleLine()) {
            pendingMainLineWillApply = null
            dispatchMainLineApplied()
            return
        }

        assembler.updateFlags(
            displayTranslation, displayRoma,
            enableRelativeProgress, enableRelativeProgressHighlight,
            displayLineByLine
        )
        val mainResult = assembler.buildMain(line)
        val secResult = assembler.buildSecondary(line)
        val mainContentWidth = main.measureLineWidth(mainResult.line)
        val secondaryContentWidth = secondary.measureLineWidth(secResult.line)

        if (!skipMainLinePreflight) {
            val onMainLineWillApply = pendingMainLineWillApply
            if (onMainLineWillApply != null) {
                pendingMainLineWillApply = null
                val generation = lineGeneration
                val candidateWidth = maxOf(mainContentWidth, secondaryContentWidth)
                val shouldDefer = onMainLineWillApply.invoke(candidateWidth)
                if (generation != lineGeneration) return
                if (shouldDefer) {
                    preflightReadyGeneration = generation
                    if (isAttachedToWindow) {
                        postOnAnimation {
                            if (generation != lineGeneration || preflightReadyGeneration != generation) {
                                return@postOnAnimation
                            }
                            refreshLines(
                                allowNextLinePromotion = allowNextLinePromotion,
                                bypassIdentityCheck = bypassIdentityCheck,
                                skipMainLinePreflight = true,
                                preserveMarquee = preserveMarquee
                            )
                        }
                    } else {
                        preflightReadyGeneration = -1
                        refreshLines(
                            allowNextLinePromotion = allowNextLinePromotion,
                            bypassIdentityCheck = bypassIdentityCheck,
                            skipMainLinePreflight = true,
                            preserveMarquee = preserveMarquee
                        )
                    }
                    return
                }
            }
        }

        val hasMainVisualContent = currentMainText != null || main.model.isCountdownLine()
        val shouldPromote = allowNextLinePromotion &&
                secondaryIsNextLinePreview && secResult.isNextLinePreview &&
                hasMainVisualContent && currentMainText != mainResult.line.text &&
                secondary.model.text == mainResult.line.text &&
                isAttachedToWindow && main.height > 0 && secondary.height > 0
        if (shouldPromote) {
            animateNextLinePromotion(mainResult.line.text, mainResult.line.isAlignedRight)
            return
        }

        main.isSustainProgressEnabled = mainResult.sustainAwareProgress
        if (preserveMarquee) {
            main.setLyricPreservingScroll(mainResult.line, mainResult.isLineTimeline)
        } else {
            main.setLyric(mainResult.line, mainResult.isLineTimeline)
        }
        main.isScrollOnly = mainResult.isScrollOnly
        currentMainText = mainResult.line.text

        alwaysShowSecondary = secResult.alwaysShow
        secondaryIsNextLinePreview = secResult.isNextLinePreview
        secondary.visibleIfChanged = secResult.alwaysShow
        secondary.isStaticPreview = secResult.isNextLinePreview
        secondary.isSustainProgressEnabled = secResult.sustainAwareProgress
        if (preserveMarquee) {
            secondary.setLyricPreservingScroll(secResult.line, secResult.isLineTimeline)
        } else {
            secondary.setLyric(secResult.line, secResult.isLineTimeline)
        }
        secondary.isScrollOnly = if (secResult.isNextLinePreview) false else secResult.isScrollOnly

        // 只有主、副行真正提交后，才把这一行标记为已应用。动态宽度预检可能会在此之前
        // 暂停刷新；过早更新 oldLine 会让后续重入误以为占位符已经显示，从而跳过 setLyric。
        oldLine = line
        if (requestMarquee) requestStartMarquee()
        dispatchMainLineApplied()
    }

    private fun dispatchMainLineApplied() {
        val callback = pendingMainLineApplied
        pendingMainLineApplied = null
        pendingMainLineCancelled = null
        callback?.invoke()
    }

    private fun applyLineStyle(
        view: SpaceGateLyricLineView, text: TextLook, highlight: Highlight,
        marquee: Marquee, gradient: Boolean, fadingEdge: Int, wordMotion: WordMotion,
        centerIfPossible: Boolean, rightIfPossible: Boolean
    ) {
        view.wordMotion = wordMotion
        view.configureWith(
            text, highlight, marquee, gradient, fadingEdge,
            centerIfPossible, rightIfPossible
        )
    }

    private fun updateLayoutTransitionX(config: String? = LayoutTransitionX.TRANSITION_CONFIG_SMOOTH) {
        layoutTransition = LayoutTransitionX(config).apply { setAnimateParentHierarchy(true) }
    }

    private fun animateNextLinePromotion(nextMainText: String?, nextMainAlignedRight: Boolean) {
        val generation = ++nextLineTransitionGeneration
        nextLineTransitionRunning = true
        val targetTranslationY = (main.top - secondary.top).toFloat()
        val secondaryTextStartX = secondary.currentTextStartX()
        val targetMainTextStartX = main.textStartX(nextMainText, nextMainAlignedRight)
        val targetTranslationX = (main.left - secondary.left).toFloat() +
                targetMainTextStartX - secondaryTextStartX
        val targetScale = (main.textSize / secondary.textSize).coerceIn(0.5f, 2f)

        main.animate().cancel()
        secondary.animate().cancel()
        secondary.pivotX = secondaryTextStartX
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


