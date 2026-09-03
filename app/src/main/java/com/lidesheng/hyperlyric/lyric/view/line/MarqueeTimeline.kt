package com.lidesheng.hyperlyric.lyric.view.line

import kotlin.math.ceil
import kotlin.math.floor

/** A deterministic marquee frame resolved from one shared active-playback timeline. */
internal data class MarqueeTimelineFrame(
    val unitOffset: Float,
    val repeat: Int,
    val isRunning: Boolean,
    val delayRemainingNanos: Long,
    val isAtTail: Boolean,
    val isFinished: Boolean
)

internal object MarqueeTimeline {

    fun resolve(
        elapsedMs: Long,
        lineWidth: Float,
        viewWidth: Int,
        ghostSpacing: Float,
        speedPxPerMs: Float,
        initialDelayMs: Int,
        loopDelayMs: Int,
        repeatCount: Int,
        stopAtEnd: Boolean,
        peerLineWidth: Float
    ): MarqueeTimelineFrame {
        val viewportWidth = viewWidth.coerceAtLeast(0).toFloat()
        if (!lineWidth.isFinite() || lineWidth <= viewportWidth || repeatCount == 0) {
            return finishedFrame()
        }

        val elapsed = elapsedMs.coerceAtLeast(0L).toDouble()
        val initialDelay = initialDelayMs.coerceAtLeast(0).toDouble()
        if (elapsed < initialDelay) {
            return delayFrame(
                unitOffset = 0f,
                repeat = 0,
                remainingMs = initialDelay - elapsed,
                isAtTail = false
            )
        }

        val speed = speedPxPerMs.toDouble()
        if (!speed.isFinite() || speed <= 0.0) {
            return runningFrame(unitOffset = 0f, repeat = 0, isAtTail = false)
        }

        val spacing = ghostSpacing.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
        val unit = (lineWidth + spacing).toDouble()
        if (!unit.isFinite() || unit <= 0.0) return finishedFrame()

        val activeElapsed = elapsed - initialDelay
        val tailOffset = (lineWidth - viewportWidth).coerceIn(0f, unit.toFloat()).toDouble()
        val motionDuration = unit / speed
        val loopDelay = loopDelayMs.coerceAtLeast(0).toDouble()

        return if (repeatCount < 0) {
            resolveInfinite(
                activeElapsed = activeElapsed,
                unit = unit,
                tailOffset = tailOffset,
                motionDuration = motionDuration,
                loopDelay = loopDelay,
                speed = speed,
                stopAtEnd = stopAtEnd,
                lineWidth = lineWidth,
                peerLineWidth = peerLineWidth
            )
        } else {
            resolveFinite(
                activeElapsed = activeElapsed,
                unit = unit,
                tailOffset = tailOffset,
                motionDuration = motionDuration,
                loopDelay = loopDelay,
                speed = speed,
                repeatCount = repeatCount,
                stopAtEnd = stopAtEnd
            )
        }
    }

    private fun resolveInfinite(
        activeElapsed: Double,
        unit: Double,
        tailOffset: Double,
        motionDuration: Double,
        loopDelay: Double,
        speed: Double,
        stopAtEnd: Boolean,
        lineWidth: Float,
        peerLineWidth: Float
    ): MarqueeTimelineFrame {
        val usesTailPause = stopAtEnd && peerLineWidth.isFinite() && peerLineWidth > 0f
        val tailDelay = if (usesTailPause && peerLineWidth > lineWidth) {
            ceil((peerLineWidth - lineWidth).toDouble() / speed)
        } else {
            0.0
        }
        val cycleDuration = motionDuration + tailDelay + loopDelay
        if (!cycleDuration.isFinite() || cycleDuration <= 0.0) {
            return runningFrame(unitOffset = 0f, repeat = 0, isAtTail = false)
        }

        val completedCycles = floor(activeElapsed / cycleDuration).coerceAtLeast(0.0)
        val repeat = repeatIndex(completedCycles)
        val phase = (activeElapsed - completedCycles * cycleDuration)
            .coerceIn(0.0, cycleDuration)

        if (usesTailPause) {
            val timeToTail = tailOffset / speed
            if (phase < timeToTail) {
                return runningFrame((phase * speed).toFloat(), repeat, isAtTail = false)
            }
            if (phase < timeToTail + tailDelay) {
                return delayFrame(
                    unitOffset = tailOffset.toFloat(),
                    repeat = repeat,
                    remainingMs = timeToTail + tailDelay - phase,
                    isAtTail = true
                )
            }
            if (phase < motionDuration + tailDelay) {
                val offset = tailOffset + (phase - timeToTail - tailDelay) * speed
                return runningFrame(
                    unitOffset = offset.coerceAtMost(unit).toFloat(),
                    repeat = repeat,
                    isAtTail = true
                )
            }
        } else if (phase < motionDuration) {
            return runningFrame(
                unitOffset = (phase * speed).coerceAtMost(unit).toFloat(),
                repeat = repeat,
                isAtTail = false
            )
        }

        return delayFrame(
            unitOffset = 0f,
            repeat = repeat + 1,
            remainingMs = cycleDuration - phase,
            isAtTail = false
        )
    }

    private fun resolveFinite(
        activeElapsed: Double,
        unit: Double,
        tailOffset: Double,
        motionDuration: Double,
        loopDelay: Double,
        speed: Double,
        repeatCount: Int,
        stopAtEnd: Boolean
    ): MarqueeTimelineFrame {
        val cyclesBeforeFinal = (repeatCount - 1).coerceAtLeast(0)
        val cycleDuration = motionDuration + loopDelay
        val timeBeforeFinal = cyclesBeforeFinal.toDouble() * cycleDuration

        if (activeElapsed < timeBeforeFinal && cycleDuration > 0.0) {
            val completedCycles = floor(activeElapsed / cycleDuration).coerceAtLeast(0.0)
            val repeat = repeatIndex(completedCycles)
            val phase = (activeElapsed - completedCycles * cycleDuration)
                .coerceIn(0.0, cycleDuration)
            return if (phase < motionDuration) {
                runningFrame(
                    unitOffset = (phase * speed).coerceAtMost(unit).toFloat(),
                    repeat = repeat,
                    isAtTail = false
                )
            } else {
                delayFrame(
                    unitOffset = 0f,
                    repeat = repeat + 1,
                    remainingMs = cycleDuration - phase,
                    isAtTail = false
                )
            }
        }

        val finalElapsed = (activeElapsed - timeBeforeFinal).coerceAtLeast(0.0)
        val finalOffset = if (stopAtEnd) tailOffset else unit
        val finalDuration = finalOffset / speed
        if (finalElapsed >= finalDuration) {
            return finishedFrame(
                unitOffset = if (stopAtEnd) tailOffset.toFloat() else 0f,
                repeat = if (stopAtEnd) cyclesBeforeFinal else repeatCount
            )
        }

        return runningFrame(
            unitOffset = (finalElapsed * speed).coerceAtMost(finalOffset).toFloat(),
            repeat = cyclesBeforeFinal,
            isAtTail = false
        )
    }

    private fun runningFrame(
        unitOffset: Float,
        repeat: Int,
        isAtTail: Boolean
    ) = MarqueeTimelineFrame(
        unitOffset = unitOffset,
        repeat = repeat,
        isRunning = true,
        delayRemainingNanos = 0L,
        isAtTail = isAtTail,
        isFinished = false
    )

    private fun delayFrame(
        unitOffset: Float,
        repeat: Int,
        remainingMs: Double,
        isAtTail: Boolean
    ) = MarqueeTimelineFrame(
        unitOffset = unitOffset,
        repeat = repeat,
        isRunning = false,
        delayRemainingNanos = millisecondsToNanos(remainingMs),
        isAtTail = isAtTail,
        isFinished = false
    )

    private fun finishedFrame(
        unitOffset: Float = 0f,
        repeat: Int = 0
    ) = MarqueeTimelineFrame(
        unitOffset = unitOffset,
        repeat = repeat,
        isRunning = false,
        delayRemainingNanos = 0L,
        isAtTail = false,
        isFinished = true
    )

    private fun repeatIndex(value: Double): Int = value
        .coerceIn(0.0, Int.MAX_VALUE.toDouble())
        .toInt()

    private fun millisecondsToNanos(milliseconds: Double): Long {
        if (!milliseconds.isFinite() || milliseconds <= 0.0) return 0L
        return (milliseconds * 1_000_000.0)
            .coerceIn(1.0, Long.MAX_VALUE.toDouble())
            .toLong()
    }
}
