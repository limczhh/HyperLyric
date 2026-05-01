/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

internal class ScrollStepper {
    fun compute(
        highlightWidth: Float,
        lineWidth: Float,
        viewWidth: Float,
        isFinished: Boolean,
        isScrollFinished: Boolean
    ): Float {
        if (lineWidth <= viewWidth) return 0f
        val minScroll = -(lineWidth - viewWidth)
        if (isFinished) return minScroll
        val halfWidth = viewWidth / 2f
        return if (highlightWidth > halfWidth) {
            (halfWidth - highlightWidth).coerceIn(minScroll, 0f)
        } else 0f
    }
}
