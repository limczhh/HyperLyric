/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

internal class LineState {
    var scrollOffset: Float = 0f
    var isScrollFinished: Boolean = false

    fun reset() {
        scrollOffset = 0f
        isScrollFinished = false
    }
}
