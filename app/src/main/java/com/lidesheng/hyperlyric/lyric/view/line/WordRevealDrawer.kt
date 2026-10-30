package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.Camera
import android.graphics.Canvas
import android.text.TextPaint
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.withSave
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel
import kotlin.math.roundToInt

/** Draws into each word's reserved space; animation never changes the line's measured size. */
internal class WordRevealDrawer {
    private val decelerate = DecelerateInterpolator()
    private val bounce = BounceInterpolator()
    private val camera by lazy { Camera() }

    fun draw(
        canvas: Canvas,
        model: LyricModel,
        positionMs: Double,
        animation: WordRevealAnimation,
        durationMs: Int,
        visibleStart: Float,
        visibleEnd: Float,
        viewHeight: Float,
        baselineY: Float,
        lineHeight: Float,
        paint: TextPaint
    ) {
        val originalAlpha = paint.alpha
        try {
            canvas.withSave {
                clipRect(visibleStart, 0f, visibleEnd, viewHeight)
                for (word in model.words) {
                    val inkStart = word.startPosition + word.inkStartOffset
                    val inkEnd = word.startPosition + word.inkEndOffset
                    if (inkEnd < visibleStart || inkStart > visibleEnd) continue
                    val progress = animation.progress(positionMs, word.begin, word.end, durationMs)
                    if (progress <= 0f) continue
                    paint.alpha = if (animation == WordRevealAnimation.FADE_IN) {
                        (originalAlpha * progress).roundToInt()
                    } else {
                        originalAlpha
                    }
                    if (progress >= 1f) {
                        drawText(word.text, word.startPosition, baselineY, paint)
                        continue
                    }

                    val eased = decelerate.getInterpolation(progress)
                    val remaining = 1f - eased
                    val centerX = (word.startPosition + word.endPosition) / 2f
                    val centerY = viewHeight / 2f
                    withSave {
                        clipRect(inkStart, 0f, inkEnd, viewHeight)
                        when (animation) {
                            WordRevealAnimation.SLIDE_UP -> translate(0f, lineHeight * remaining)
                            WordRevealAnimation.SLIDE_DOWN -> translate(0f, -lineHeight * remaining)
                            WordRevealAnimation.SLIDE_RIGHT -> translate(-word.textWidth * remaining, 0f)
                            WordRevealAnimation.SLIDE_LEFT -> translate(word.textWidth * remaining, 0f)
                            WordRevealAnimation.ZOOM_IN -> scale(eased, eased, centerX, centerY)
                            WordRevealAnimation.BOUNCE_IN -> {
                                val scale = bounce.getInterpolation(progress)
                                scale(scale, scale, centerX, centerY)
                            }
                            WordRevealAnimation.FLIP_IN -> {
                                translate(centerX, centerY)
                                camera.save()
                                camera.rotateY(90f * remaining)
                                camera.applyToCanvas(this)
                                camera.restore()
                                translate(-centerX, -centerY)
                            }
                            WordRevealAnimation.WIPE_IN -> clipRect(
                                inkStart, 0f, inkStart + (inkEnd - inkStart) * eased, viewHeight
                            )
                            WordRevealAnimation.NONE, WordRevealAnimation.FADE_IN -> Unit
                        }
                        drawText(word.text, word.startPosition, baselineY, paint)
                    }
                }
            }
        } finally {
            paint.alpha = originalAlpha
        }
    }
}
