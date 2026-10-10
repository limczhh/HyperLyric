/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.animation.LayoutTransition
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.withScale
import androidx.core.view.forEach
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContent
import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.line.LyricLineView
import com.lidesheng.hyperlyric.lyric.view.yoyo.YoYoAnimation

@SuppressLint("ViewConstructor")
class RichLyricLineView(
    context: Context,
    var displayTranslation: Boolean = true,
    var enableRelativeProgress: Boolean = false,
    var enableRelativeProgressHighlight: Boolean = false,
    var displayRoma: Boolean = true,
    var displayBackgroundVocal: Boolean = true,
    var secondaryContentOrder: List<LyricSecondaryContent> = LyricSecondaryContent.DEFAULT_ORDER
) : LinearLayout(context), UpdatableColor {

    val main = LyricLineView(context)
    val secondary = LyricLineView(context).apply { visibleIfChanged = false }

    var alwaysShowSecondary = false

    var renderScale = 1.0f
        private set

    private val assembler = LyricLineAssembler(
        displayTranslation = displayTranslation,
        displayRoma = displayRoma,
        displayBackgroundVocal = displayBackgroundVocal,
        enableRelativeProgress = enableRelativeProgress,
        enableRelativeHighlight = enableRelativeProgressHighlight,
        secondaryContentOrder = secondaryContentOrder
    )
    private var displayLineByLine = false

    private var pendingMainLineWillApply: ((Float) -> Boolean)? = null
    private var pendingMainLineApplied: (() -> Unit)? = null
    private var pendingMainLineCancelled: (() -> Unit)? = null
    private var requestMarquee = false
    private var lastPosition: Long = Long.MIN_VALUE
    private var lastPlaybackSpeed = Float.NaN

    var rawLine: IRichLyricLine? = null
    var rawSecondaryLine: IRichLyricLine? = null
    private var currentMainText: String? = null
    private var secondaryIsNextLinePreview = false
    private var nextLineTransitionRunning = false
    private var nextLineTransitionGeneration = 0

    var line: IRichLyricLine?
        get() = rawLine
        set(value) {
            setLineInternal(value, null, null, null, null)
        }

    fun setLineWithCallbacks(
        value: IRichLyricLine?,
        onMainLineWillApply: ((Float) -> Boolean)? = null,
        onMainLineApplied: (() -> Unit)? = null,
        onMainLineCancelled: (() -> Unit)? = null,
        secondaryLine: IRichLyricLine? = null
    ) {
        setLineInternal(
            value,
            onMainLineWillApply,
            onMainLineApplied,
            onMainLineCancelled,
            secondaryLine
        )
    }

    internal fun setMetadataLine(
        value: IRichLyricLine?,
        secondaryLine: IRichLyricLine? = null,
        preserveMarquee: Boolean = false
    ) {
        setLineInternal(
            value,
            null,
            null,
            null,
            secondaryLine,
            preserveMarquee = preserveMarquee
        )
    }

    fun updateMetadataLine(
        value: IRichLyricLine?,
        secondaryLine: IRichLyricLine? = null
    ) {
        setMetadataLine(value, secondaryLine, preserveMarquee = true)
    }

    private fun setLineInternal(
        value: IRichLyricLine?,
        onMainLineWillApply: ((Float) -> Boolean)?,
        onMainLineApplied: (() -> Unit)?,
        onMainLineCancelled: (() -> Unit)?,
        secondaryLine: IRichLyricLine?,
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
        rawSecondaryLine = secondaryLine
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
        clipChildren = false
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(secondary, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateLayoutTransitionX()
    }

    fun reset() {
        cancelNextLinePromotion()
        appliedMainLine = null
        appliedSecondaryLine = null
        line = null
        renderScale = 1.0f
        lastPosition = Long.MIN_VALUE
        lastPlaybackSpeed = Float.NaN
        currentMainText = null
        rawSecondaryLine = null
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

    internal fun synchronizePosition(
        position: Long,
        playbackSpeed: Float = 1f,
        activeTimeMs: Long = position
    ) {
        val resolvedSpeed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) {
            playbackSpeed
        } else {
            1f
        }
        lastPosition = position
        lastPlaybackSpeed = resolvedSpeed
        main.synchronizePosition(position, resolvedSpeed, activeTimeMs)
        secondary.synchronizePosition(position, resolvedSpeed, activeTimeMs)
    }

    fun setPlaybackActive(active: Boolean) {
        main.setPlaybackActive(active)
        secondary.setPlaybackActive(active)
    }

    internal fun keepPlaybackClockRunningWhenHidden(enabled: Boolean) {
        main.keepPlaybackClockRunningWhenHidden = enabled
        secondary.keepPlaybackClockRunningWhenHidden = enabled
    }

    internal fun useSharedMarqueeClock(enabled: Boolean, originActiveTimeMs: Long = 0L) {
        main.useSharedMarqueeClock(enabled, originActiveTimeMs)
        secondary.useSharedMarqueeClock(enabled, originActiveTimeMs)
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
        displayLineByLine = style.lineDisplay
        assembler.updateFlags(
            displayTranslation = displayTranslation,
            displayRoma = displayRoma,
            displayBackgroundVocal = displayBackgroundVocal,
            enableRelativeProgress = style.primary.relativeProgress,
            enableRelativeHighlight = style.primary.relativeHighlight,
            displayLineByLine = displayLineByLine,
            secondaryContentOrder = secondaryContentOrder
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
            style.centerIfPossible,
            style.rightIfPossible
        )
        applyLineStyle(
            secondary,
            style.secondary,
            style.highlight,
            style.marquee,
            style.gradient,
            style.fadingEdge,
            style.wordMotion,
            style.centerIfPossible,
            style.rightIfPossible
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

    /**
     * 停掉这块歌词(或它的某一行)上正在跑的换行动画,并把变换复位。
     *
     * 换行预设可能只跑在单行上——对唱的第二行换行时第一行还在唱——只取消整块会漏掉那一行;
     * 而被取消的动画不得留下半截变换,否则复用的投影会停在淡出或位移的中途。
     */
    internal fun resetTransitionAnimations() {
        resetAnimationState(this)
        resetAnimationState(main)
        resetAnimationState(secondary)
    }

    private fun resetAnimationState(target: View) {
        YoYoAnimation.cancelAnimation(target)
        target.alpha = 1f
        target.translationX = 0f
        target.translationY = 0f
        target.scaleX = 1f
        target.scaleY = 1f
        target.rotation = 0f
        target.rotationX = 0f
        target.rotationY = 0f
    }

    override fun onDetachedFromWindow() {
        resetTransitionAnimations()
        super.onDetachedFromWindow()
        reset()
    }

    private var oldLine: IRichLyricLine? = null
    private var oldSecondaryLine: IRichLyricLine? = null
    private var lineGeneration = 0
    private var preflightReadyGeneration = -1

    // 已提交到两行的内容:内容未变的那一行不重设(见 [applyMainRow] / [applySecondaryRow])。
    private var appliedMainLine: LyricLine? = null
    private var appliedMainTimeline = false
    private var appliedSecondaryLine: LyricLine? = null
    private var appliedSecondaryTimeline = false

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
        if (!bypassIdentityCheck && oldLine === line &&
            oldSecondaryLine === rawSecondaryLine && line.isTitleLine()
        ) {
            pendingMainLineWillApply = null
            dispatchMainLineApplied()
            return
        }
        assembler.updateFlags(
            displayTranslation = displayTranslation,
            displayRoma = displayRoma,
            displayBackgroundVocal = displayBackgroundVocal,
            enableRelativeProgress = enableRelativeProgress,
            enableRelativeHighlight = enableRelativeProgressHighlight,
            displayLineByLine = displayLineByLine,
            secondaryContentOrder = secondaryContentOrder
        )
        val mainResult = assembler.buildMain(line)
        val secResult = assembler.buildSecondary(line, rawSecondaryLine)

        if (!skipMainLinePreflight) {
            val onMainLineWillApply = pendingMainLineWillApply
            if (onMainLineWillApply != null) {
                pendingMainLineWillApply = null
                val generation = lineGeneration
                val candidateWidth = maxOf(
                    main.measureLineWidth(mainResult.line),
                    secondary.measureLineWidth(secResult.line)
                )
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
        // 内容没变就不重设:重设会 reset + 重新 seek 渲染器,长行还会重算滚动窗口——另一行换行时
        // 本行会跟着动一下(owner 2026-10-09 真机反馈「第二行会影响第一行」)。
        if (rowContentChanged(mainResult.line, mainResult.isLineTimeline, main = true)) {
            if (preserveMarquee) {
                main.setLyricPreservingScroll(mainResult.line, mainResult.isLineTimeline)
            } else {
                main.setLyric(mainResult.line, mainResult.isLineTimeline)
            }
        }
        main.isScrollOnly = mainResult.isScrollOnly
        currentMainText = mainResult.line.text

        alwaysShowSecondary = secResult.alwaysShow
        secondaryIsNextLinePreview = secResult.isNextLinePreview
        secondary.visibleIfChanged = secResult.alwaysShow
        secondary.isStaticPreview = secResult.isNextLinePreview
        secondary.isSustainProgressEnabled = secResult.sustainAwareProgress
        if (rowContentChanged(secResult.line, secResult.isLineTimeline, main = false)) {
            if (preserveMarquee) {
                secondary.setLyricPreservingScroll(secResult.line, secResult.isLineTimeline)
            } else {
                secondary.setLyric(secResult.line, secResult.isLineTimeline)
            }
        }
        secondary.isScrollOnly = if (secResult.isNextLinePreview) false else secResult.isScrollOnly

        // 只有主、副行真正提交后，才把这一行标记为已应用。动态宽度预检可能会在此之前
        // 暂停刷新；过早更新 oldLine 会让后续重入误以为占位符已经显示，从而跳过 setLyric。
        oldLine = line
        oldSecondaryLine = rawSecondaryLine
        if (requestMarquee) requestStartMarquee()
        dispatchMainLineApplied()
    }

    /** True when this row's committed content actually differs from the newly built one. */
    private fun rowContentChanged(line: LyricLine, isLineTimeline: Boolean, main: Boolean): Boolean {
        // 只比"同一行源内容"(时间窗 + 显示文本):切分片段里词边界/元数据的差异不算内容变化,
        // 否则同一句每来一次更新都会被重设一次(owner 真机 3:33 主行被牵连)。
        val previous = if (main) appliedMainLine else appliedSecondaryLine
        val previousTimeline = if (main) appliedMainTimeline else appliedSecondaryTimeline
        val same = previous != null && previousTimeline == isLineTimeline &&
            previous.begin == line.begin && previous.end == line.end && previous.text == line.text
        if (main) {
            appliedMainLine = line
            appliedMainTimeline = isLineTimeline
        } else {
            appliedSecondaryLine = line
            appliedSecondaryTimeline = isLineTimeline
        }
        return !same
    }

    private fun dispatchMainLineApplied() {
        val callback = pendingMainLineApplied
        pendingMainLineApplied = null
        pendingMainLineCancelled = null
        callback?.invoke()
    }

    private fun applyLineStyle(
        view: LyricLineView, text: TextLook, highlight: Highlight,
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
        layoutTransition = LayoutTransitionX(config).apply {
            setAnimateParentHierarchy(true)
            // 任一行换行都会改变两行块的测量高度,进而重排;CHANGING 动画会把这次重排也演一遍,
            // 于是换行结束后"再接一个动画"才滑到位(owner 2026-10-09 真机反馈)。出现/消失仍保留。
            disableTransitionType(LayoutTransition.CHANGING)
        }
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

