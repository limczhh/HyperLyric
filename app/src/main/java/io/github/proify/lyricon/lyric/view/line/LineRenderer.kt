/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.text.TextPaint
import io.github.proify.lyricon.lyric.view.line.model.LyricModel

internal interface LineRenderer {
    val isPlaying: Boolean
    val isFinished: Boolean
    val isStarted: Boolean

    fun step(deltaNanos: Long, model: LyricModel, state: LineState, viewWidth: Int): Boolean
    fun draw(canvas: Canvas, model: LyricModel, paint: TextPaint, state: LineState, viewWidth: Int, viewHeight: Int)
    fun seek(model: LyricModel, state: LineState, posMs: Long, viewWidth: Int, viewHeight: Int)
    fun update(model: LyricModel, state: LineState, posMs: Long, viewWidth: Int, viewHeight: Int)
    fun reset(state: LineState)
}
