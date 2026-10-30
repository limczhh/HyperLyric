/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel
import com.lidesheng.hyperlyric.lyric.view.line.model.WordModel

/** Shared media-time progress logic for standard and SpaceGate word renderers. */
internal class WordProgressController {
    val animator = ProgressAnimator()

    var sustainAware = false
    var lastPosition = Long.MIN_VALUE
        private set

    private var activeWord: WordModel? = null
    private var settledPositionMs = Long.MIN_VALUE.toDouble()

    /** Uses the same interpolated media clock as the highlight, before its visual easing. */
    val currentPositionMs: Double
        get() {
            val word = activeWord
            val offset = animator.currentWordPositionMs
            return if (word != null && offset != null) word.begin.toDouble() + offset
            else settledPositionMs
        }

    fun seek(posMs: Long, model: LyricModel): Boolean {
        val previousWidth = animator.currentWidth
        animator.jumpTo(exactTargetWidth(posMs, model))
        activeWord = null
        lastPosition = posMs
        settledPositionMs = posMs.toDouble()
        reconcileLifecycleFlags(model)
        return animator.currentWidth != previousWidth
    }

    fun update(posMs: Long, playbackSpeed: Float, model: LyricModel): Boolean {
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            return seek(posMs, model)
        }

        val previousWidth = animator.currentWidth
        val word = model.wordTimingNavigator.first(posMs)
        when {
            word != null -> {
                val duration = durationOf(word)
                if (duration <= 0L) {
                    animator.jumpTo(word.endPosition)
                } else {
                    animator.animateWord(
                        rangeStart = word.startPosition,
                        rangeEnd = word.endPosition,
                        durationMs = duration,
                        positionMs = (posMs - word.begin).coerceIn(0L, duration),
                        playbackSpeed = playbackSpeed,
                        sustainStrength = if (sustainAware) word.sustainStrength else 0f,
                        forceAnchor = activeWord !== word
                    )
                }
            }

            posMs >= model.end -> animator.jumpTo(model.width)
            posMs <= model.begin -> animator.jumpTo(0f)
            else -> animator.jumpTo(widthBeforeGap(posMs, model))
        }

        activeWord = word
        lastPosition = posMs
        settledPositionMs = posMs.toDouble()
        reconcileLifecycleFlags(model)
        return animator.currentWidth != previousWidth
    }

    fun step(deltaNanos: Long): Boolean {
        val changed = animator.step(deltaNanos)
        settledPositionMs = currentPositionMs
        return changed
    }

    fun freeze() {
        settledPositionMs = currentPositionMs
        animator.stopAtCurrent()
        activeWord = null
    }

    fun reset() {
        animator.reset()
        activeWord = null
        lastPosition = Long.MIN_VALUE
        settledPositionMs = Long.MIN_VALUE.toDouble()
    }

    fun syncFrom(other: WordProgressController) {
        animator.syncFrom(other.animator)
        sustainAware = other.sustainAware
        lastPosition = other.lastPosition
        settledPositionMs = other.currentPositionMs
        activeWord = null
    }

    private fun exactTargetWidth(posMs: Long, model: LyricModel): Float {
        val word = model.wordTimingNavigator.first(posMs)
        return when {
            word != null -> {
                val duration = durationOf(word)
                if (duration <= 0L) {
                    word.endPosition
                } else {
                    val linearProgress =
                        ((posMs - word.begin).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    val mappedProgress = WordProgressMapper.map(
                        linearProgress,
                        if (sustainAware) word.sustainStrength else 0f
                    )
                    word.startPosition + word.textWidth * mappedProgress
                }
            }

            posMs >= model.end -> model.width
            posMs <= model.begin -> 0f
            else -> widthBeforeGap(posMs, model)
        }
    }

    private fun widthBeforeGap(posMs: Long, model: LyricModel): Float {
        val previous = model.wordTimingNavigator.findPreviousEntry(posMs) ?: return 0f
        return if (posMs > previous.end) previous.endPosition else animator.currentWidth
    }

    private fun durationOf(word: WordModel): Long =
        (word.end - word.begin).takeIf { it > 0L } ?: word.duration

    private fun reconcileLifecycleFlags(model: LyricModel) {
        if (animator.currentWidth < model.width) animator.hasFinished = false
        if (animator.currentWidth <= 0f) animator.hasStarted = false
    }
}
