/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.text.TextPaint
import androidx.core.graphics.withSave
import io.github.proify.lyricon.lyric.view.line.model.LyricModel
import io.github.proify.lyricon.lyric.view.line.model.WordModel
import kotlin.math.abs
import kotlin.math.max

internal class TextDrawer {
    private var bgColors = intArrayOf(Color.GRAY)
    private var hlColors = intArrayOf(Color.WHITE)

    var cjkLiftFactor = DEFAULT_CJK_LIFT_FACTOR
    var cjkWaveFactor = DEFAULT_CJK_WAVE_FACTOR
    var latinLiftFactor = DEFAULT_LATIN_LIFT_FACTOR
    var latinWaveFactor = DEFAULT_LATIN_WAVE_FACTOR

    val isRainbowBg get() = bgColors.size > 1
    val isRainbowHl get() = hlColors.size > 1

    private val fontMetrics = Paint.FontMetrics()
    private var baselineOffset = 0f

    private var cachedRainbowShader: LinearGradient? = null
    private var cachedAlphaMaskShader: LinearGradient? = null
    private var lastTotalWidth = -1f
    private var lastHighlightWidth = -1f
    private var lastColorsHash = 0

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) bgColors = background
        if (highlight.isNotEmpty()) hlColors = highlight
    }

    fun updateMetrics(paint: TextPaint) {
        paint.getFontMetrics(fontMetrics)
        baselineOffset = -(fontMetrics.descent + fontMetrics.ascent) / 2f
    }

    fun clearShaderCache() {
        cachedRainbowShader = null
        cachedAlphaMaskShader = null
        lastTotalWidth = -1f
    }

    fun draw(
        canvas: Canvas,
        model: LyricModel,
        viewWidth: Int,
        viewHeight: Int,
        scrollX: Float,
        isOverflow: Boolean,
        highlightWidth: Float,
        useGradient: Boolean,
        scrollOnly: Boolean,
        charMotionEnabled: Boolean,
        bgPaint: TextPaint,
        hlPaint: TextPaint,
        normPaint: TextPaint
    ) {
        val y = (viewHeight / 2f) + baselineOffset
        canvas.withSave {
            val xOffset = when {
                isOverflow -> scrollX
                model.isAlignedRight -> viewWidth - model.width
                else -> 0f
            }
            translate(xOffset, 0f)

            if (scrollOnly) {
                canvas.drawText(model.wordText, 0f, y, normPaint)
                return@withSave
            }

            if (isRainbowBg) {
                bgPaint.shader = getOrCreateRainbowShader(model.width, bgColors)
            } else {
                bgPaint.shader = null
            }

            if (charMotionEnabled) {
                val bgClipStart = if (useGradient) 0f else highlightWidth
                drawAnimatedUnits(
                    canvas,
                    model,
                    highlightWidth,
                    bgClipStart,
                    Float.MAX_VALUE,
                    viewHeight,
                    y,
                    bgPaint
                )
            } else if (!useGradient) {
                canvas.withSave {
                    canvas.clipRect(highlightWidth, 0f, Float.MAX_VALUE, viewHeight.toFloat())
                    canvas.drawText(model.wordText, 0f, y, bgPaint)
                }
            } else {
                canvas.drawText(model.wordText, 0f, y, bgPaint)
            }

            if (highlightWidth > 0f) {
                canvas.withSave {
                    canvas.clipRect(0f, 0f, highlightWidth, viewHeight.toFloat())

                    //val atEnd = highlightWidth >= model.width
                    val atEnd = false
                    if (useGradient && !atEnd) {
                        val baseShader = if (isRainbowHl) {
                            getOrCreateRainbowShader(model.width, hlColors)
                        } else {
                            LinearGradient(
                                0f, 0f, model.width, 0f,
                                hlPaint.color, hlPaint.color,
                                Shader.TileMode.CLAMP
                            )
                        }
                        val maskShader = getOrCreateAlphaMaskShader(model.width, highlightWidth)
                        hlPaint.shader = ComposeShader(baseShader, maskShader, PorterDuff.Mode.DST_IN)
                    } else {
                        if (isRainbowHl) {
                            hlPaint.shader = getOrCreateRainbowShader(model.width, hlColors)
                        } else {
                            hlPaint.shader = null
                        }
                    }
                    if (charMotionEnabled) {
                        drawAnimatedUnits(
                            canvas,
                            model,
                            highlightWidth,
                            0f,
                            highlightWidth,
                            viewHeight,
                            y,
                            hlPaint
                        )
                    } else {
                        canvas.drawText(model.wordText, 0f, y, hlPaint)
                    }
                }
            }
        }
    }

    private fun drawAnimatedUnits(
        canvas: Canvas,
        model: LyricModel,
        highlightWidth: Float,
        clipStart: Float,
        clipEnd: Float,
        viewHeight: Int,
        baselineY: Float,
        paint: TextPaint
    ) {
        model.words.forEach { word ->
            val motionSpec = word.motionSpec()
            if (!motionSpec.animateByChar) {
                drawAnimatedTextUnit(
                    canvas = canvas,
                    text = word.text,
                    start = 0,
                    end = word.text.length,
                    drawX = word.startPosition,
                    unitStart = word.startPosition,
                    unitEnd = word.endPosition,
                    highlightWidth = highlightWidth,
                    clipStart = clipStart,
                    clipEnd = clipEnd,
                    viewHeight = viewHeight,
                    baselineY = baselineY,
                    paint = paint,
                    motionSpec = motionSpec
                )
                return@forEach
            }

            for (i in word.chars.indices) {
                val charStart = word.charStartPositions[i]
                val charEnd = word.charEndPositions[i]
                drawAnimatedTextUnit(
                    canvas = canvas,
                    text = word.text,
                    start = i,
                    end = i + 1,
                    drawX = charStart,
                    unitStart = charStart,
                    unitEnd = charEnd,
                    highlightWidth = highlightWidth,
                    clipStart = clipStart,
                    clipEnd = clipEnd,
                    viewHeight = viewHeight,
                    baselineY = baselineY,
                    paint = paint,
                    motionSpec = motionSpec
                )
            }
        }
    }

    private fun drawAnimatedTextUnit(
        canvas: Canvas,
        text: String,
        start: Int,
        end: Int,
        drawX: Float,
        unitStart: Float,
        unitEnd: Float,
        highlightWidth: Float,
        clipStart: Float,
        clipEnd: Float,
        viewHeight: Int,
        baselineY: Float,
        paint: TextPaint,
        motionSpec: MotionSpec
    ) {
        if (unitEnd <= clipStart || unitStart >= clipEnd) return

        val visibleLeft = unitStart.coerceAtLeast(clipStart)
        val visibleRight = unitEnd.coerceAtMost(clipEnd)
        val liftY = computeUnitLift(highlightWidth, unitStart, unitEnd, paint.textSize, motionSpec)

        canvas.withSave {
            clipRect(visibleLeft, 0f, visibleRight, viewHeight.toFloat())
            drawText(text, start, end, drawX, baselineY + liftY, paint)
        }
    }

    private fun computeUnitLift(
        highlightWidth: Float,
        unitStart: Float,
        unitEnd: Float,
        textSize: Float,
        motionSpec: MotionSpec
    ): Float {
        val maxOffset = textSize * motionSpec.liftFactor
        val unitCenter = (unitStart + unitEnd) / 2f
        val waveLength = textSize * motionSpec.waveFactor
        val phase = ((highlightWidth - unitCenter) / waveLength).coerceIn(0f, 1f)
        return maxOffset * (1f - easeOutQuint(phase))
    }

    private fun WordModel.motionSpec(): MotionSpec {
        return if (text.any { it.isCjk() }) {
            MotionSpec(animateByChar = true, liftFactor = cjkLiftFactor, waveFactor = cjkWaveFactor)
        } else {
            MotionSpec(
                animateByChar = false,
                liftFactor = latinLiftFactor,
                waveFactor = latinWaveFactor
            )
        }
    }

    private fun Char.isCjk(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    }

    private fun easeOutQuint(value: Float): Float {
        val inverse = 1f - value
        return 1f - inverse * inverse * inverse * inverse * inverse
    }

    private data class MotionSpec(
        val animateByChar: Boolean,
        val liftFactor: Float,
        val waveFactor: Float
    )

    private fun getOrCreateRainbowShader(totalWidth: Float, colors: IntArray): Shader {
        val colorsHash = colors.contentHashCode()
        if (cachedRainbowShader == null || lastTotalWidth != totalWidth || lastColorsHash != colorsHash) {
            cachedRainbowShader = LinearGradient(
                0f, 0f, totalWidth, 0f,
                colors, null, Shader.TileMode.CLAMP
            )
            lastTotalWidth = totalWidth
            lastColorsHash = colorsHash
        }
        return cachedRainbowShader!!
    }

    private fun getOrCreateAlphaMaskShader(totalWidth: Float, highlightWidth: Float): Shader {
        val edgePosition = max(highlightWidth / totalWidth, 0.9f)
        if (cachedAlphaMaskShader == null || abs(lastHighlightWidth - highlightWidth) > 0.1f) {
            cachedAlphaMaskShader = LinearGradient(
                0f, 0f, highlightWidth, 0f,
                intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                floatArrayOf(0f, edgePosition, 1f),
                Shader.TileMode.CLAMP
            )
            lastHighlightWidth = highlightWidth
        }
        return cachedAlphaMaskShader!!
    }

    private companion object {
        const val DEFAULT_CJK_LIFT_FACTOR = 0.055f
        const val DEFAULT_CJK_WAVE_FACTOR = 2.8f
        const val DEFAULT_LATIN_LIFT_FACTOR = 0.065f
        const val DEFAULT_LATIN_WAVE_FACTOR = 3.6f
    }
}
