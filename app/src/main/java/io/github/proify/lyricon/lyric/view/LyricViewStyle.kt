/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

import android.graphics.Color
import android.graphics.Typeface

data class LyricViewStyle(
    val primary: TextLook = TextLook(),
    val secondary: TextLook = TextLook(size = 14f),
    val highlight: Highlight = Highlight(),
    val marquee: Marquee = Marquee(),
    val wordMotion: WordMotion = WordMotion(),
    val gradient: Boolean = true,
    val fadingEdge: Int = 10,
    val scaleMultiLine: Float = 1f,
    val animation: AnimParams = AnimParams(),
    val placeholder: TitleSlot = TitleSlot.NAME_ARTIST,
    val transitionConfig: String = "smooth",
)

data class TextLook(
    val color: IntArray = intArrayOf(Color.WHITE),
    val size: Float = 16f,
    val typeface: Typeface = Typeface.DEFAULT,
    val relativeProgress: Boolean = false,
    val relativeHighlight: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextLook) return false
        return size == other.size && typeface == other.typeface &&
                relativeProgress == other.relativeProgress &&
                relativeHighlight == other.relativeHighlight &&
                color.contentEquals(other.color)
    }

    override fun hashCode(): Int {
        var r = size.hashCode()
        r = 31 * r + typeface.hashCode()
        r = 31 * r + relativeProgress.hashCode()
        r = 31 * r + relativeHighlight.hashCode()
        r = 31 * r + color.contentHashCode()
        return r
    }
}

data class Highlight(
    val background: IntArray = intArrayOf(Color.GRAY),
    val foreground: IntArray = intArrayOf(Color.WHITE),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Highlight) return false
        return background.contentEquals(other.background) && foreground.contentEquals(other.foreground)
    }

    override fun hashCode(): Int = 31 * background.contentHashCode() + foreground.contentHashCode()
}

data class Marquee(
    val speed: Float = 40f,
    val spacing: Float = 70f,
    val initialDelay: Int = 300,
    val loopDelay: Int = 700,
    val repeatCount: Int = -1,
    val stopAtEnd: Boolean = false,
)

data class WordMotion(
    val enabled: Boolean = true,
    val cjkLiftFactor: Float = 0.055f,
    val cjkWaveFactor: Float = 2.8f,
    val latinLiftFactor: Float = 0.065f,
    val latinWaveFactor: Float = 3.6f,
)

data class AnimParams(
    val enabled: Boolean = false,
    val presetId: String = "stack_flow",
)

enum class TitleSlot { NAME_ARTIST, NAME, NONE }
