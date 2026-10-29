/*
 * Spectrum rendering and FFT sampling are adapted from HyperIsland's MusicIslandSpectrumHook:
 * https://github.com/1812z/HyperIsland
 * Copyright (c) 2026 1812z
 * Licensed under the MIT License; the full notice is in assets/licenses/HyperIsland-MIT.txt.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
 */
package com.lidesheng.hyperlyric.root.island.effects.spectrum

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewParent
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "IslandMusicWaveSpectrumView"
private const val BAR_COUNT = 5
private const val SYSTEM_AUDIO_SESSION = 0
private const val CAPTURE_SIZE = 512
private const val TARGET_FPS = 60
private const val MIN_FRAME_INTERVAL_NANOS = 1_000_000_000L / TARGET_FPS - 1_000_000L
private const val MAX_FRAME_NANOS = 100_000_000L
private const val SILENCE_NANOS = 350_000_000L
private const val ATTACK_TIME_NANOS = 35_000_000L
private const val RELEASE_TIME_NANOS = 70_000_000L
// A signed 8-bit complex FFT bin tops out at hypot(-128, -128) ≈ 181.
private const val FFT_MAGNITUDE_FULL_SCALE = 181.02f
private const val FFT_DISPLAY_MIN_DB = -42f
private const val FFT_DISPLAY_MAX_DB = 0f
// Drop the persistent low-level baseline, then amplify the remaining height changes.
private const val FFT_LEVEL_FLOOR = 0.4f
private const val FFT_LEVEL_GAIN = 1.67f
private const val LEVEL_EPSILON = 0.01f
private const val REDRAW_EPSILON = 0.005f
private const val DEBUG_METRICS_INTERVAL_NANOS = 1_000_000_000L
// Geometry from the SystemUI plugin's native 66x66 music_pause/music_wave Lottie compositions.
private const val NATIVE_COMPOSITION_SIZE = 66f
private const val NATIVE_BAR_FIRST_CENTER_X = 9f
private const val NATIVE_BAR_CENTER_SPACING = 12f
private const val NATIVE_BAR_WIDTH = 5.4f
private const val NATIVE_PAUSE_HALF_HEIGHT = 8.7f
private const val NATIVE_MAX_WAVE_HALF_HEIGHT = 22.359f
// Keep the highest range on the last bar; boundaries are fractions of the sampled upper bin.
private val BAND_EDGE_FRACTIONS = floatArrayOf(0f, 0.04f, 0.12f, 0.23f, 0.36f, 1f)
internal const val ISLAND_MUSIC_WAVE_DEFAULT_TOP_COLOR = 0xFF57F2FF.toInt()
internal const val ISLAND_MUSIC_WAVE_DEFAULT_BOTTOM_COLOR = 0xFFF5E6FF.toInt()

private val warningStates = Collections.synchronizedSet(mutableSetOf<String>())

private fun warnOnce(key: String, message: String) {
    if (warningStates.add(key)) HookLogger.w(TAG, message)
}

private fun smoothingAlpha(elapsedNanos: Long, timeConstantNanos: Long): Float =
    (1.0 - exp(-elapsedNanos.toDouble() / timeConstantNanos)).toFloat()

private fun fftMagnitudeDb(magnitude: Float): Float =
    if (magnitude <= 0f) Float.NEGATIVE_INFINITY else
        20f * log10(magnitude / FFT_MAGNITUDE_FULL_SCALE)

private fun normalizedFftLevel(magnitude: Float): Float {
    val decibels = fftMagnitudeDb(magnitude)
    val level = ((decibels - FFT_DISPLAY_MIN_DB) /
            (FFT_DISPLAY_MAX_DB - FFT_DISPLAY_MIN_DB)).coerceIn(0f, 1f)
    return ((level - FFT_LEVEL_FLOOR) * FFT_LEVEL_GAIN).coerceIn(0f, 1f)
}

internal class IslandMusicWaveSpectrumView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bar = RectF()
    private val visibleRect = Rect()
    private val levels = FloatArray(BAR_COUNT)
    private val drawnLevels = FloatArray(BAR_COUNT)
    private val gradientCache = arrayOfNulls<LinearGradient>(BAR_COUNT)
    private val gradientTop = FloatArray(BAR_COUNT)
    private val gradientBottom = FloatArray(BAR_COUNT)
    private val gradientTopColor = IntArray(BAR_COUNT)
    private val gradientBottomColor = IntArray(BAR_COUNT)
    private var topColor = ISLAND_MUSIC_WAVE_DEFAULT_TOP_COLOR
    private var bottomColor = ISLAND_MUSIC_WAVE_DEFAULT_BOTTOM_COLOR
    private var realtime = false
    private var attached = false
    private var acquired = false
    private var failed = false
    private var framePending = false
    private var lastFrameNanos = 0L
    private var failureCallback: (() -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePending = false
            if (!attached || !realtime) return
            val sinceLastFrame = if (lastFrameNanos == 0L) Long.MAX_VALUE else
                frameTimeNanos - lastFrameNanos
            if (sinceLastFrame < MIN_FRAME_INTERVAL_NANOS) {
                postFrame()
                return
            }
            lastFrameNanos = frameTimeNanos
            if (!isRenderable()) {
                postFrame()
                return
            }
            val elapsed = if (sinceLastFrame == Long.MAX_VALUE) MIN_FRAME_INTERVAL_NANOS else
                sinceLastFrame.coerceAtMost(MAX_FRAME_NANOS)
            val attackAlpha = smoothingAlpha(elapsed, ATTACK_TIME_NANOS)
            val releaseAlpha = smoothingAlpha(elapsed, RELEASE_TIME_NANOS)
            val active = VisualizerSource.isAudioActive()
            var settled = true
            var changed = false
            val targets = VisualizerSource.levels()
            for (index in 0 until BAR_COUNT) {
                val current = levels[index]
                val target = if (active) targets[index] else 0f
                val alpha = if (target >= current) attackAlpha else releaseAlpha
                val next = current + (target - current) * alpha
                if (abs(next - current) > REDRAW_EPSILON) changed = true
                if (next > LEVEL_EPSILON) settled = false
                levels[index] = next
            }
            if (changed) invalidate()
            if (active || !settled) postFrame() else lastFrameNanos = 0L
        }
    }

    fun setColors(top: Int, bottom: Int) {
        if (topColor == top && bottomColor == bottom) return
        topColor = top
        bottomColor = bottom
        invalidate()
    }

    fun setOnCaptureFailure(callback: (() -> Unit)?) {
        failureCallback = callback
    }

    fun setPlaying(playing: Boolean): Boolean {
        realtime = playing
        if (!playing) {
            levels.fill(0f)
            cancelFrame()
            syncSource()
            invalidate()
            return true
        }
        failed = false
        syncSource()
        if (failed) return false
        postFrame()
        return !attached || acquired
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        syncSource()
        postFrame()
    }

    override fun onDetachedFromWindow() {
        attached = false
        cancelFrame()
        syncSource()
        super.onDetachedFromWindow()
    }

    private fun syncSource() {
        val shouldAcquire = attached && realtime
        if (shouldAcquire && !acquired && !failed) {
            acquired = VisualizerSource.acquire(this)
            if (!acquired) {
                failed = true
                post { failureCallback?.invoke() }
            }
        } else if (!shouldAcquire && acquired) {
            acquired = false
            VisualizerSource.release(this)
        }
    }

    private fun postFrame() {
        if (framePending || !attached || !realtime || failed) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun cancelFrame() {
        if (!framePending) return
        framePending = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun isRenderable(): Boolean {
        if (!isShown || alpha == 0f) return false
        if (!getGlobalVisibleRect(visibleRect) ||
            visibleRect.width() <= 0 || visibleRect.height() <= 0
        ) {
            return false
        }
        var ancestor: ViewParent? = parent
        var depth = 0
        while (depth < 8) {
            val view = ancestor as? View ?: return true
            if (view.alpha == 0f) return false
            ancestor = view.parent
            depth++
        }
        return true
    }

    fun onAudioFrame() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            postFrame()
        } else {
            post { postFrame() }
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        val scale = min(width, height) / NATIVE_COMPOSITION_SIZE
        val contentLeft = (width - NATIVE_COMPOSITION_SIZE * scale) / 2f
        val contentTop = (height - NATIVE_COMPOSITION_SIZE * scale) / 2f
        val barWidth = NATIVE_BAR_WIDTH * scale
        val centerY = contentTop + NATIVE_COMPOSITION_SIZE / 2f * scale
        val minHalfHeight = NATIVE_PAUSE_HALF_HEIGHT * scale
        val maxHalfHeight = NATIVE_MAX_WAVE_HALF_HEIGHT * scale
        System.arraycopy(levels, 0, drawnLevels, 0, BAR_COUNT)
        for (index in 0 until BAR_COUNT) {
            val fraction = drawnLevels[index].coerceIn(0f, 1f)
            val halfHeight = minHalfHeight + (maxHalfHeight - minHalfHeight) * fraction
            val centerX = contentLeft +
                    (NATIVE_BAR_FIRST_CENTER_X + index * NATIVE_BAR_CENTER_SPACING) * scale
            val top = centerY - halfHeight
            val bottom = centerY + halfHeight
            val left = centerX - barWidth / 2f
            bar.set(left, top, left + barWidth, bottom)
            paint.shader = barShader(index, top, bottom)
            canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, paint)
        }
        paint.shader = null
    }

    private fun barShader(index: Int, top: Float, bottom: Float): LinearGradient? {
        if (gradientTopColor[index] == topColor &&
            gradientBottomColor[index] == bottomColor &&
            gradientTop[index] == top &&
            gradientBottom[index] == bottom
        ) {
            return gradientCache[index]
        }
        val gradient = if (bottom > top) {
            LinearGradient(0f, top, 0f, bottom, topColor, bottomColor, Shader.TileMode.CLAMP)
        } else {
            null
        }
        gradientCache[index] = gradient
        gradientTop[index] = top
        gradientBottom[index] = bottom
        gradientTopColor[index] = topColor
        gradientBottomColor[index] = bottomColor
        return gradient
    }
}

/** Visualizer(0) is process-wide for this audio mix, so all real/fake holders share one source. */
private object VisualizerSource {
    private val lock = Any()
    private val clients = Collections.newSetFromMap(WeakHashMap<IslandMusicWaveSpectrumView, Boolean>())
    private val scratch = FloatArray(BAR_COUNT)
    private var visualizer: Visualizer? = null
    private var lastMetricsLogAtNanos = 0L

    @Volatile
    private var lastAudioAtNanos = 0L

    @Volatile
    private var levelSnapshot = FloatArray(BAR_COUNT)

    fun levels(): FloatArray = levelSnapshot

    fun isAudioActive(): Boolean = lastAudioAtNanos != 0L &&
            System.nanoTime() - lastAudioAtNanos < SILENCE_NANOS

    fun acquire(view: IslandMusicWaveSpectrumView): Boolean = synchronized(lock) {
        if (visualizer != null) {
            clients.add(view)
            return@synchronized true
        }
        var instance: Visualizer? = null
        val captureRate = Visualizer.getMaxCaptureRate()
        val created = try {
            Visualizer(SYSTEM_AUDIO_SESSION).also {
                instance = it
                it.captureSize = CAPTURE_SIZE
                it.setDataCaptureListener(
                    captureListener,
                    captureRate,
                    false,
                    true
                )
                it.enabled = true
            }
        } catch (error: Throwable) {
            runCatching { instance?.release() }
            warnOnce(
                "visualizer_create_failed",
                "创建实时频谱采样失败: ${error.javaClass.simpleName}"
            )
            null
        } ?: return@synchronized false
        visualizer = created
        clients.add(view)
        HookLogger.dState(
            stateId = "IslandMusicWaveSpectrumView.visualizer",
            tag = TAG,
            state = "active"
        ) {
            "实时频谱采样器已启动: session=$SYSTEM_AUDIO_SESSION, captureSize=$CAPTURE_SIZE, " +
                    "captureRate=$captureRate, bars=$BAR_COUNT, response=fast"
        }
        true
    }

    fun release(view: IslandMusicWaveSpectrumView) {
        synchronized(lock) {
            clients.remove(view)
            if (clients.isNotEmpty()) return
            releaseVisualizerLocked()
        }
    }

    fun releaseAll() {
        synchronized(lock) {
            clients.clear()
            releaseVisualizerLocked()
        }
    }

    private fun releaseVisualizerLocked() {
        val active = visualizer
        visualizer = null
        lastAudioAtNanos = 0L
        lastMetricsLogAtNanos = 0L
        levelSnapshot = FloatArray(BAR_COUNT)
        active?.let {
            runCatching { it.enabled = false }
            runCatching { it.release() }
            HookLogger.dState(
                stateId = "IslandMusicWaveSpectrumView.visualizer",
                tag = TAG,
                state = "inactive"
            ) { "实时频谱采样器已释放" }
        }
    }

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer,
            waveform: ByteArray,
            samplingRate: Int,
        ) = Unit

        override fun onFftDataCapture(
            visualizer: Visualizer,
            fft: ByteArray,
            samplingRate: Int,
        ) {
            if (fft.all { it.toInt() == 0 }) {
                val lastAudioAt = lastAudioAtNanos
                if (lastAudioAt != 0L && System.nanoTime() - lastAudioAt >= SILENCE_NANOS) {
                    HookLogger.dState(
                        stateId = "IslandMusicWaveSpectrumView.audio",
                        tag = TAG,
                        state = "silent"
                    ) { "实时频谱采样已静音" }
                }
                return
            }
            val binCount = fft.size / 2
            if (binCount <= BAR_COUNT + 1) return
            val maxBin = (binCount / 2).coerceIn(BAR_COUNT + 1, binCount - 1)
            var frameMax = 0f
            for (index in 0 until BAR_COUNT) {
                val start = frequencyBin(index, maxBin)
                val end = max(frequencyBin(index + 1, maxBin), start + 1)
                var peak = 0f
                for (bin in start until end) {
                    val real = fft[2 * bin].toInt()
                    val imaginary = fft[2 * bin + 1].toInt()
                    peak = max(peak, sqrt((real * real + imaginary * imaginary).toFloat()))
                }
                scratch[index] = peak
                frameMax = max(frameMax, peak)
            }
            if (frameMax <= 0f) return
            val snapshot = FloatArray(BAR_COUNT)
            val now = System.nanoTime()
            val wasSilent = lastAudioAtNanos == 0L || now - lastAudioAtNanos >= SILENCE_NANOS
            synchronized(lock) {
                for (index in 0 until BAR_COUNT) {
                    snapshot[index] = normalizedFftLevel(scratch[index])
                }
                lastAudioAtNanos = now
                levelSnapshot = snapshot
                clients.toList().forEach(IslandMusicWaveSpectrumView::onAudioFrame)
            }
            if (wasSilent) {
                HookLogger.dState(
                    stateId = "IslandMusicWaveSpectrumView.audio",
                    tag = TAG,
                    state = "active"
                ) {
                    "收到实时频谱 FFT 数据: fftSize=${fft.size}, samplingRate=$samplingRate"
                }
            }
            if (HookLogger.isDebugEnabled) {
                val shouldLogMetrics = synchronized(lock) {
                    if (lastMetricsLogAtNanos != 0L &&
                        now - lastMetricsLogAtNanos < DEBUG_METRICS_INTERVAL_NANOS
                    ) {
                        false
                    } else {
                        lastMetricsLogAtNanos = now
                        true
                    }
                }
                if (shouldLogMetrics) {
                    val rawPeaks = scratch.joinToString(separator = ",", prefix = "[", postfix = "]") {
                        it.toInt().toString()
                    }
                    val dbBands = scratch.joinToString(separator = ",", prefix = "[", postfix = "]") {
                        val decibels = fftMagnitudeDb(it)
                        if (decibels <= FFT_DISPLAY_MIN_DB) "≤${FFT_DISPLAY_MIN_DB.toInt()}dB" else
                            "${decibels.toInt()}dB"
                    }
                    val levels = snapshot.joinToString(separator = ",", prefix = "[", postfix = "]") {
                        "${(it * 100).toInt()}%"
                    }
                    HookLogger.d(
                        TAG,
                        "实时频谱幅度: peakBands=$rawPeaks, dbBands=$dbBands, levels=$levels"
                    )
                }
            }
        }
    }

    private fun frequencyBin(index: Int, maxBin: Int): Int =
        (maxBin * BAND_EDGE_FRACTIONS[index]).toInt()
            .coerceIn(1, maxBin - 1)
}

internal fun releaseIslandMusicWaveSpectrumSource() = VisualizerSource.releaseAll()
