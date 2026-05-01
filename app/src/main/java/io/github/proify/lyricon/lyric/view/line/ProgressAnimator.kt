/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

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

    fun jumpTo(width: Float) {
        currentWidth = width
        targetWidth = width
        isAnimating = false
    }

    fun animateTo(target: Float, durationMs: Long) {
        startWidth = currentWidth
        targetWidth = target
        durationNano = maxOf(1L, durationMs) * 1_000_000L
        elapsedNanos = 0L
        isAnimating = true
    }

    fun step(deltaNanos: Long): Boolean {
        if (!isAnimating) return false
        elapsedNanos += deltaNanos
        if (elapsedNanos >= durationNano) {
            currentWidth = targetWidth
            isAnimating = false
            return true
        }
        currentWidth = startWidth + (targetWidth - startWidth) * (elapsedNanos.toFloat() / durationNano)
        return true
    }

    fun reset() {
        currentWidth = 0f
        targetWidth = 0f
        isAnimating = false
        hasStarted = false
        hasFinished = false
    }
}