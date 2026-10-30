/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import kotlin.math.abs

internal class ProgressAnimator {
    var currentWidth = 0f
        private set
    var targetWidth = 0f
        private set
    var isAnimating = false
        private set
    var hasStarted = false
    var hasFinished = false

    private var startWidth = 0f
    private var elapsedNanos = 0L
    private var durationNano = 1L

    private var mode = Mode.NONE
    private var wordRangeStart = 0f
    private var wordRangeEnd = 0f
    private var wordDurationMs = 1f
    private var wordPositionMs = 0f
    private var wordPlaybackSpeed = 1f
    private var wordSustainStrength = 0f

    val currentWordPositionMs: Float?
        get() = wordPositionMs.takeIf { mode == Mode.WORD }

    fun jumpTo(width: Float) {
        currentWidth = width
        targetWidth = width
        isAnimating = false
        mode = Mode.NONE
    }

    fun stopAtCurrent() {
        startWidth = currentWidth
        targetWidth = currentWidth
        elapsedNanos = 0L
        isAnimating = false
        mode = Mode.NONE
    }

    fun animateTo(target: Float, durationMs: Long, playbackSpeed: Float = 1f) {
        startWidth = currentWidth
        targetWidth = target
        val speed = normalizePlaybackSpeed(playbackSpeed)
        durationNano = maxOf(
            1L,
            (maxOf(1L, durationMs).toDouble() * 1_000_000.0 / speed.toDouble()).toLong()
        )
        elapsedNanos = 0L
        isAnimating = true
        mode = Mode.LINEAR
    }

    /** Animates a single continuous visual range without requiring word data. */
    fun animateRange(
        rangeStart: Float,
        rangeEnd: Float,
        durationMs: Long,
        positionMs: Long,
        playbackSpeed: Float,
        forceAnchor: Boolean
    ) = animateTimedRange(
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        durationMs = durationMs,
        positionMs = positionMs,
        playbackSpeed = playbackSpeed,
        sustainStrength = 0f,
        forceAnchor = forceAnchor
    )

    fun animateWord(
        rangeStart: Float,
        rangeEnd: Float,
        durationMs: Long,
        positionMs: Long,
        playbackSpeed: Float,
        sustainStrength: Float,
        forceAnchor: Boolean
    ) = animateTimedRange(
        rangeStart = rangeStart,
        rangeEnd = rangeEnd,
        durationMs = durationMs,
        positionMs = positionMs,
        playbackSpeed = playbackSpeed,
        sustainStrength = sustainStrength,
        forceAnchor = forceAnchor
    )

    private fun animateTimedRange(
        rangeStart: Float,
        rangeEnd: Float,
        durationMs: Long,
        positionMs: Long,
        playbackSpeed: Float,
        sustainStrength: Float,
        forceAnchor: Boolean
    ) {
        if (durationMs <= 0L || rangeEnd <= rangeStart) {
            jumpTo(rangeEnd)
            return
        }

        val previousWidth = currentWidth
        val incomingPosition = positionMs.toFloat().coerceIn(0f, durationMs.toFloat())
        val sameTimeline = mode == Mode.WORD &&
                wordRangeStart == rangeStart &&
                wordRangeEnd == rangeEnd &&
                wordDurationMs == durationMs.toFloat()

        wordRangeStart = rangeStart
        wordRangeEnd = rangeEnd
        wordDurationMs = durationMs.toFloat()
        wordPlaybackSpeed = normalizePlaybackSpeed(playbackSpeed)
        wordSustainStrength = sustainStrength.coerceIn(0f, 1f)

        if (!sameTimeline || forceAnchor) {
            wordPositionMs = incomingPosition
        } else {
            val errorMs = incomingPosition - wordPositionMs
            wordPositionMs = if (abs(errorMs) >= HARD_REANCHOR_THRESHOLD_MS) {
                incomingPosition
            } else {
                (wordPositionMs + errorMs * SOFT_REANCHOR_RATIO)
                    .coerceIn(0f, wordDurationMs)
            }
        }

        targetWidth = rangeEnd
        mode = Mode.WORD
        updateWordWidth()
        if (sameTimeline && currentWidth < previousWidth) {
            currentWidth = previousWidth
        }
        isAnimating = wordPositionMs < wordDurationMs && currentWidth < targetWidth
    }

    fun step(deltaNanos: Long): Boolean {
        if (!isAnimating) return false

        if (mode == Mode.WORD) {
            val previousWidth = currentWidth
            val deltaMs = deltaNanos.toDouble() / 1_000_000.0
            wordPositionMs = (wordPositionMs + deltaMs.toFloat() * wordPlaybackSpeed)
                .coerceAtMost(wordDurationMs)
            updateWordWidth()
            if (wordPositionMs >= wordDurationMs || currentWidth >= targetWidth) {
                currentWidth = targetWidth
                isAnimating = false
            }
            return currentWidth != previousWidth
        }

        elapsedNanos += deltaNanos
        if (elapsedNanos >= durationNano) {
            currentWidth = targetWidth
            isAnimating = false
            return true
        }
        currentWidth =
            startWidth + (targetWidth - startWidth) * (elapsedNanos.toFloat() / durationNano)
        return true
    }

    fun reset() {
        currentWidth = 0f
        targetWidth = 0f
        isAnimating = false
        hasStarted = false
        hasFinished = false
        mode = Mode.NONE
        wordRangeStart = 0f
        wordRangeEnd = 0f
        wordDurationMs = 1f
        wordPositionMs = 0f
        wordPlaybackSpeed = 1f
        wordSustainStrength = 0f
    }

    fun syncFrom(other: ProgressAnimator) {
        currentWidth = other.currentWidth
        targetWidth = other.targetWidth
        isAnimating = other.isAnimating
        hasStarted = other.hasStarted
        hasFinished = other.hasFinished
        startWidth = other.startWidth
        elapsedNanos = other.elapsedNanos
        durationNano = other.durationNano
        mode = other.mode
        wordRangeStart = other.wordRangeStart
        wordRangeEnd = other.wordRangeEnd
        wordDurationMs = other.wordDurationMs
        wordPositionMs = other.wordPositionMs
        wordPlaybackSpeed = other.wordPlaybackSpeed
        wordSustainStrength = other.wordSustainStrength
    }

    private fun updateWordWidth() {
        val linearProgress = (wordPositionMs / wordDurationMs).coerceIn(0f, 1f)
        val mappedProgress = WordProgressMapper.map(linearProgress, wordSustainStrength)
        currentWidth = wordRangeStart + (wordRangeEnd - wordRangeStart) * mappedProgress
    }

    private enum class Mode {
        NONE,
        LINEAR,
        WORD
    }

    private companion object {
        const val HARD_REANCHOR_THRESHOLD_MS = 180f
        const val SOFT_REANCHOR_RATIO = 0.25f
    }
}

internal fun normalizePlaybackSpeed(speed: Float): Float =
    if (speed.isFinite() && speed > 0f) speed.coerceIn(0.1f, 4f) else 1f
