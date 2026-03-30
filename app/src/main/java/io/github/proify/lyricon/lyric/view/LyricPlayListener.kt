/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import io.github.proify.lyricon.lyric.view.line.LyricLineView

interface LyricPlayListener {
    fun onPlayStarted(view: LyricLineView)
    fun onPlayEnded(view: LyricLineView)
    fun onPlayProgress(view: LyricLineView, total: Float, progress: Float)
}