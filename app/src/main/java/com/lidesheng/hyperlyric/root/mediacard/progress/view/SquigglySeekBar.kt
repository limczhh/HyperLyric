/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted from XiaomiHelper.
 * Copyright (C) 2026 HowieHChen
 */

package com.lidesheng.hyperlyric.root.mediacard.progress.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.graphics.withClip
import kotlin.math.abs
import kotlin.math.cos

class SquigglySeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatSeekBar(context, attrs, defStyleAttr) {
    companion object {
        val EMPHASIZED_DECELERATE: Interpolator =
            PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val STANDARD_DECELERATE: Interpolator = PathInterpolator(0f, 0f, 0f, 1f)
        val TOUCH_SPRING = SpringInterpolator(0.95f, 0.35f)

        const val TWO_PI = (Math.PI * 2f).toFloat()

        private const val HEIGHT_DP = 16
        private const val ALPHA = 0xFF
        private const val DISABLED_ALPHA = 0x4D
        private const val THUMB_NORMAL_HEIGHT_DP = 10
        private const val THUMB_PRESSED_HEIGHT_DP = 16
        private const val THUMB_V_BAR_WIDTH_DP = 4
        private const val THUMB_V_BAR_HEIGHT_DP = 14
        private const val MAX_VISUAL_PROGRESS_ADVANCE_MS = 1200L
    }

    private val thumbHeight = context.dp(THUMB_NORMAL_HEIGHT_DP.toFloat()).toInt()
    private val thumbHeightPressed = context.dp(THUMB_PRESSED_HEIGHT_DP.toFloat()).toInt()
    private val thumbVBarWidth = context.dp(THUMB_V_BAR_WIDTH_DP.toFloat()).toInt()
    private val thumbVBarHeight = context.dp(THUMB_V_BAR_HEIGHT_DP.toFloat()).toInt()

    private val wavePaint = Paint()
    private val linePaint = Paint()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private var heightFraction = 0f
    private var heightAnimator: ValueAnimator? = null
    private var phaseOffset = 0f
    private var lastFrameTime = -1L
    private var tintFallbackColor = Color.WHITE
    private var explicitWaveForeground: Int? = null
    private var explicitWaveTrack: Int? = null
    private var touchAnimProgress = 0f
    private var touchAnimator: ValueAnimator? = null
    private var realProgress = 0
    private var realProgressTime = SystemClock.uptimeMillis()
    private var isUserSeeking = false

    private val transitionPeriods = 1.5f
    private val minWaveEndpoint = 0.2f
    private val matchedWaveEndpoint = 0.6f

    var waveLength = 0f
    var lineAmplitude = 0f
    var phaseSpeed = 0f
    var strokeWidth = 0f
        set(value) {
            if (field == value) return
            field = value
            wavePaint.strokeWidth = value
            linePaint.strokeWidth = value
        }

    var transitionEnabled = true
        set(value) {
            field = value
            invalidate()
        }

    var animate = false
        set(value) {
            if (field == value) return
            field = value
            recordProgressAnchor()
            if (field) lastFrameTime = SystemClock.uptimeMillis()
            heightAnimator?.cancel()
            heightAnimator = ValueAnimator.ofFloat(
                heightFraction,
                if (animate) 1f else 0f
            ).apply {
                if (animate) {
                    startDelay = 60
                    duration = 800
                    interpolator = EMPHASIZED_DECELERATE
                } else {
                    duration = 550
                    interpolator = STANDARD_DECELERATE
                }
                addUpdateListener {
                    heightFraction = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        heightAnimator = null
                    }
                })
                start()
            }
        }

    var thumbStyle = ThumbStyle.Hidden
        set(value) {
            field = value
            adjustThumb()
            invalidate()
        }

    init {
        wavePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeCap = Paint.Cap.ROUND
        wavePaint.style = Paint.Style.STROKE
        linePaint.style = Paint.Style.STROKE
        linePaint.alpha = ALPHA
        updateColorsFromTint()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredHeight = context.dp(HEIGHT_DP.toFloat()).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (animate) {
            invalidate()
            val now = SystemClock.uptimeMillis()
            phaseOffset += (now - lastFrameTime) / 1000f * phaseSpeed
            phaseOffset %= waveLength
            lastFrameTime = now
        }

        val progress = visualProgress()
        val centerY = height / 2f
        val trackWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0).toFloat()
        if (trackWidth <= 0f) return
        val totalProgressPx = trackWidth * progress
        val waveProgressPx = trackWidth * if (
            !transitionEnabled || progress > matchedWaveEndpoint
        ) {
            progress
        } else {
            lerp(
                minWaveEndpoint,
                matchedWaveEndpoint,
                lerpInv(0f, matchedWaveEndpoint, progress)
            )
        }
        val waveStart = -phaseOffset - waveLength / 2f
        val waveEnd = if (transitionEnabled) trackWidth else waveProgressPx
        path.rewind()
        path.moveTo(waveStart, 0f)
        var currentX = waveStart
        var waveSign = 1f
        var currentAmplitude = computeAmplitude(currentX, waveSign, waveProgressPx)
        val segmentLength = waveLength / 2f
        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + segmentLength
            val middleX = currentX + segmentLength / 2f
            val nextAmplitude = computeAmplitude(nextX, waveSign, waveProgressPx)
            path.cubicTo(
                middleX,
                currentAmplitude,
                middleX,
                nextAmplitude,
                nextX,
                nextAmplitude
            )
            currentAmplitude = nextAmplitude
            currentX = nextX
        }

        val clipTop = lineAmplitude + strokeWidth
        val activeClipEnd = if (
            totalProgressPx > 0f && thumbStyle == ThumbStyle.Hidden
        ) {
            totalProgressPx + strokeWidth
        } else {
            totalProgressPx
        }
        val clipEnd = trackWidth + strokeWidth
        canvas.save()
        canvas.translate(paddingLeft.toFloat(), centerY)
        canvas.save()
        canvas.clipRect(0f, -clipTop, activeClipEnd, clipTop)
        canvas.drawPath(path, wavePaint)
        canvas.restore()
        if (transitionEnabled) {
            if (totalProgressPx < trackWidth) {
                canvas.withClip(totalProgressPx, -clipTop, clipEnd, clipTop) {
                    drawPath(path, linePaint)
                }
            }
        } else {
            canvas.drawLine(totalProgressPx, 0f, trackWidth, 0f, linePaint)
        }
        val startAmplitude = cos(abs(waveStart) / waveLength * TWO_PI)
        canvas.drawPoint(0f, startAmplitude * lineAmplitude * heightFraction, wavePaint)
        canvas.restore()

        val thumbCenterX = paddingLeft + totalProgressPx
        when (thumbStyle) {
            ThumbStyle.Circle -> {
                val currentHeight = linearInterpolate(
                    thumbHeight,
                    thumbHeightPressed,
                    touchAnimProgress
                ).coerceAtLeast(strokeWidth.toInt())
                canvas.drawCircle(
                    thumbCenterX,
                    centerY,
                    currentHeight / 2f,
                    thumbPaint
                )
            }

            ThumbStyle.VerticalBar -> {
                val halfWidth = thumbVBarWidth / 2f
                val halfHeight = thumbVBarHeight / 2f
                canvas.drawRoundRect(
                    thumbCenterX - halfWidth,
                    centerY - halfHeight,
                    thumbCenterX + halfWidth,
                    centerY + halfHeight,
                    halfWidth,
                    halfWidth,
                    thumbPaint
                )
            }

            ThumbStyle.Hidden -> Unit
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.actionMasked == MotionEvent.ACTION_DOWN) isUserSeeking = true
        val result = super.onTouchEvent(event)
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTouchAnimation(1f)
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isUserSeeking = false
                recordProgressAnchor()
                startTouchAnimation(0f)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return result
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        recordProgressAnchor()
    }

    override fun setProgress(progress: Int, animate: Boolean) {
        super.setProgress(progress, animate)
        recordProgressAnchor()
    }

    override fun setProgressTintList(tint: ColorStateList?) {
        super.setProgressTintList(tint)
        updateColorsFromTint()
    }

    override fun setProgressBackgroundTintList(tint: ColorStateList?) {
        super.setProgressBackgroundTintList(tint)
        updateColorsFromTint()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        updateColorsFromTint()
    }

    /** Sets the colors used by the active wave and its inactive track. */
    fun setWaveColors(foreground: Int, track: Int) {
        explicitWaveForeground = foreground
        explicitWaveTrack = track
        applyPaintColors(foreground, track)
    }

    fun setWaveForegroundColor(foreground: Int) {
        setWaveColors(foreground, explicitWaveTrack ?: defaultTrackColor(foreground))
    }

    fun setWaveTrackColor(track: Int) {
        setWaveColors(explicitWaveForeground ?: tintFallbackColor, track)
    }

    fun clearWaveColors() {
        explicitWaveForeground = null
        explicitWaveTrack = null
        updateColorsFromTint()
    }

    private fun adjustThumb() {
        val requiredPadding = when (thumbStyle) {
            ThumbStyle.Circle -> thumbHeightPressed / 2
            ThumbStyle.VerticalBar -> thumbVBarWidth / 2
            ThumbStyle.Hidden -> 0
        }
        setPadding(requiredPadding, paddingTop, requiredPadding, paddingBottom)
    }

    private fun computeAmplitude(x: Float, sign: Float, waveProgressPx: Float): Float {
        return if (transitionEnabled) {
            val transitionLength = transitionPeriods * waveLength
            val coefficient = lerpInvSat(
                waveProgressPx + transitionLength / 2f,
                waveProgressPx - transitionLength / 2f,
                x
            )
            sign * heightFraction * lineAmplitude * coefficient
        } else {
            sign * heightFraction * lineAmplitude
        }
    }

    private fun recordProgressAnchor() {
        realProgress = progress
        realProgressTime = SystemClock.uptimeMillis()
    }

    private fun visualProgress(): Float {
        if (max <= 0) return 0f
        val displayProgress = if (animate && !isUserSeeking) {
            val elapsed = (SystemClock.uptimeMillis() - realProgressTime)
                .coerceAtLeast(0L)
                .coerceAtMost(MAX_VISUAL_PROGRESS_ADVANCE_MS)
            realProgress + elapsed
        } else {
            progress.toLong()
        }
        return (displayProgress.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    private fun startTouchAnimation(target: Float) {
        if (touchAnimProgress == target) return
        touchAnimator?.cancel()
        touchAnimator = ValueAnimator.ofFloat(touchAnimProgress, target).apply {
            duration = 200
            interpolator = TOUCH_SPRING
            addUpdateListener {
                touchAnimProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    touchAnimator = null
                }
            })
            start()
        }
    }

    private fun updateColorsFromTint() {
        val explicitForeground = explicitWaveForeground
        val explicitTrack = explicitWaveTrack
        if (explicitForeground != null || explicitTrack != null) {
            val foreground = explicitForeground ?: tintFallbackColor
            applyPaintColors(
                foreground,
                explicitTrack ?: defaultTrackColor(foreground)
            )
            return
        }

        val foreground = progressTintList?.getColorForState(
            drawableState,
            tintFallbackColor
        ) ?: tintFallbackColor
        val track = progressBackgroundTintList?.getColorForState(
            drawableState,
            defaultTrackColor(foreground)
        ) ?: defaultTrackColor(foreground)
        applyPaintColors(foreground, track)
    }

    private fun applyPaintColors(foreground: Int, track: Int) {
        wavePaint.color = foreground
        thumbPaint.color = foreground
        linePaint.color = track
        linePaint.alpha = ALPHA
        invalidate()
    }

    private fun defaultTrackColor(foreground: Int): Int {
        return Color.argb(
            DISABLED_ALPHA,
            Color.red(foreground),
            Color.green(foreground),
            Color.blue(foreground)
        )
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    private fun lerpInv(start: Float, stop: Float, value: Float): Float {
        return if (start != stop) (value - start) / (stop - start) else 0f
    }

    private fun lerpInvSat(start: Float, stop: Float, value: Float): Float {
        return lerpInv(start, stop, value).coerceIn(0f, 1f)
    }

    private fun linearInterpolate(start: Int, stop: Int, amount: Float): Int {
        return start + ((stop - start) * amount).toInt()
    }

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density
}
