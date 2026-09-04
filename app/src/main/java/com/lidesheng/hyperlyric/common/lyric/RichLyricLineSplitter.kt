package com.lidesheng.hyperlyric.common.lyric

import android.graphics.Paint
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Splits one rich lyric line for the separated Super Island mode.
 *
 * The container policy decides the desired left-side portion. The lyric views own overflow and
 * may scroll after the split; this class never tries to resize or relayout a View.
 */
object RichLyricLineSplitter {

    data class SplitLineResult(
        val left: IRichLyricLine,
        val right: IRichLyricLine
    )

    /**
     * The two and only two container behaviors used by separated lyrics.
     *
     * [Fixed] keeps both content containers at one size. [Dynamic] grows the pair from its
     * minimum bounds to its maximum bounds according to the measured lyric width.
     */
    sealed interface ContainerWidthSpec {
        fun targetSplitWidthPx(totalTextWidthPx: Float): Float

        data class Fixed(
            val leftWidthPx: Float,
            val rightWidthPx: Float
        ) : ContainerWidthSpec {
            override fun targetSplitWidthPx(totalTextWidthPx: Float): Float {
                val textWidth = totalTextWidthPx.coerceAtLeast(0f)
                if (textWidth <= 0f) return 0f

                val leftWidth = leftWidthPx.coerceAtLeast(0f)
                val totalWidth = (leftWidth + rightWidthPx.coerceAtLeast(0f))
                    .coerceAtLeast(0f)
                if (totalWidth <= 0f) return textWidth / 2f

                return if (textWidth >= totalWidth) {
                    leftWidth
                } else {
                    (textWidth * leftWidth / totalWidth).coerceIn(0f, textWidth)
                }
            }
        }

        data class Dynamic(
            val leftMinWidthPx: Float,
            val leftMaxWidthPx: Float,
            val rightMinWidthPx: Float,
            val rightMaxWidthPx: Float
        ) : ContainerWidthSpec {
            private val minTotalWidthPx: Float
                get() = (leftMinWidthPx.coerceAtLeast(0f) +
                        rightMinWidthPx.coerceAtLeast(0f)).coerceAtLeast(0f)

            private val maxTotalWidthPx: Float
                get() = (leftMaxWidthPx.coerceAtLeast(0f) +
                        rightMaxWidthPx.coerceAtLeast(0f)).coerceAtLeast(minTotalWidthPx)

            override fun targetSplitWidthPx(totalTextWidthPx: Float): Float {
                val textWidth = totalTextWidthPx.coerceAtLeast(0f)
                if (textWidth <= 0f) return 0f

                // Once the island reaches its maximum, the left side is filled to its own
                // maximum and the complete remaining lyric stays on the right.
                if (textWidth >= maxTotalWidthPx) {
                    return leftMaxWidthPx.coerceAtLeast(0f)
                }

                if (textWidth <= minTotalWidthPx) {
                    if (minTotalWidthPx <= 0f) return textWidth / 2f
                    return (textWidth * leftMinWidthPx.coerceAtLeast(0f) /
                            minTotalWidthPx).coerceIn(0f, textWidth)
                }

                val range = (maxTotalWidthPx - minTotalWidthPx).coerceAtLeast(1f)
                val progress = (textWidth - minTotalWidthPx) / range
                return (leftMinWidthPx +
                        (leftMaxWidthPx - leftMinWidthPx) * progress)
                    .coerceIn(0f, textWidth)
            }
        }
    }

    /**
     * Splits [line] according to the resolved fixed or dynamic container policy.
     *
     * [primaryPaint] and [secondaryPaint] must describe the actual two lyric rows. The split
     * target is a desired position, not a hard drawable-width limit; a nearby character or word
     * boundary may exceed it slightly, and the lyric view will scroll if necessary.
     */
    fun split(
        line: IRichLyricLine,
        primaryPaint: Paint,
        secondaryPaint: Paint,
        containerWidthSpec: ContainerWidthSpec,
        partitionUntimedTimeline: Boolean = false
    ): SplitLineResult {
        val text = line.text
            ?: return SplitLineResult(toRichLyricLine(line), RichLyricLine())

        val primary = splitText(
            text = text,
            words = line.words,
            paint = primaryPaint,
            containerWidthSpec = containerWidthSpec
        )
        val secondary = splitText(
            text = line.secondary,
            words = line.secondaryWords,
            paint = secondaryPaint,
            containerWidthSpec = containerWidthSpec
        )
        val translation = splitText(
            text = line.translation,
            words = line.translationWords,
            paint = secondaryPaint,
            containerWidthSpec = containerWidthSpec
        )
        val timelineSplit = if (partitionUntimedTimeline && line.words.isNullOrEmpty()) {
            splitUntimedTimeline(line, primary, primaryPaint)
        } else {
            null
        }

        val leftLine = RichLyricLine(
            begin = line.begin,
            end = timelineSplit?.splitTimeMs ?: line.end,
            duration = 0,
            isAlignedRight = false,
            metadata = line.metadata,
            text = primary.leftText.orEmpty(),
            words = primary.leftWords,
            secondary = secondary.leftText,
            secondaryWords = secondary.leftWords,
            translation = translation.leftText,
            translationWords = translation.leftWords,
            roma = null
        )

        val rightLine = RichLyricLine(
            begin = timelineSplit?.splitTimeMs
                ?: primary.rightWords?.firstOrNull()?.begin
                ?: line.begin,
            end = timelineSplit?.endTimeMs ?: line.end,
            duration = 0,
            isAlignedRight = false,
            metadata = line.metadata,
            text = primary.rightText.orEmpty(),
            words = primary.rightWords,
            secondary = secondary.rightText,
            secondaryWords = secondary.rightWords,
            translation = translation.rightText,
            translationWords = translation.rightWords,
            roma = null
        )

        return SplitLineResult(leftLine, rightLine)
    }

    /**
     * Partitions a line-only timeline at the visual split point. The lyric views can then create
     * their usual local relative-progress word while retaining one continuous left-to-right
     * highlight across both separated slots.
     */
    private fun splitUntimedTimeline(
        line: IRichLyricLine,
        primary: TextParts,
        paint: Paint
    ): TimelineSplit? {
        val leftText = primary.leftText?.takeIf { it.isNotEmpty() } ?: return null
        val rightText = primary.rightText?.takeIf { it.isNotEmpty() } ?: return null
        if (line.begin < 0L) return null

        val durationMs = (line.end - line.begin).takeIf { it > 0L }
            ?: line.duration.takeIf { it > 0L }
            ?: return null
        if (durationMs < 2L) return null

        val leftWidth = paint.measureText(leftText).coerceAtLeast(0f)
        val rightWidth = paint.measureText(rightText).coerceAtLeast(0f)
        val totalWidth = leftWidth + rightWidth
        if (!totalWidth.isFinite() || totalWidth <= 0f) return null

        val leftDurationMs = (durationMs.toDouble() * leftWidth / totalWidth)
            .roundToLong()
            .coerceIn(1L, durationMs - 1L)
        return TimelineSplit(
            splitTimeMs = line.begin + leftDurationMs,
            endTimeMs = line.begin + durationMs
        )
    }

    private data class TextParts(
        val leftText: String? = null,
        val rightText: String? = null,
        val leftWords: List<LyricWord>? = null,
        val rightWords: List<LyricWord>? = null
    )

    private data class TimelineSplit(
        val splitTimeMs: Long,
        val endTimeMs: Long
    )

    private fun splitText(
        text: String?,
        words: List<LyricWord>?,
        paint: Paint,
        containerWidthSpec: ContainerWidthSpec
    ): TextParts {
        if (text.isNullOrEmpty()) return TextParts()

        val totalWidth = paint.measureText(text)
        val targetWidth = containerWidthSpec.targetSplitWidthPx(totalWidth)
        val splitIndex = computeSplitIndex(text, paint, targetWidth)

        if (splitIndex <= 0) {
            return TextParts(rightText = text, rightWords = words)
        }
        if (splitIndex >= text.length) {
            return TextParts(leftText = text, leftWords = words)
        }

        if (words.isNullOrEmpty()) {
            return TextParts(
                leftText = text.substring(0, splitIndex),
                rightText = text.substring(splitIndex)
            )
        }

        val (leftWords, rightWords) = splitWordsAtCharIndex(words, splitIndex)
        return TextParts(
            leftText = leftWords.joinToString("") { it.text.orEmpty() }
                .takeIf { it.isNotEmpty() },
            rightText = rightWords.joinToString("") { it.text.orEmpty() }
                .takeIf { it.isNotEmpty() },
            leftWords = leftWords,
            rightWords = rightWords
        )
    }

    private fun toRichLyricLine(line: IRichLyricLine): RichLyricLine {
        return line as? RichLyricLine ?: RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = line.metadata,
            text = line.text,
            words = line.words,
            secondary = line.secondary,
            secondaryWords = line.secondaryWords,
            translation = line.translation,
            translationWords = line.translationWords,
            roma = line.roma
        )
    }

    /**
     * Splits word timing at a UTF-16 boundary. If a timed word crosses the boundary, its timing
     * is interpolated so both lyric views keep a valid local timeline.
     */
    private fun splitWordsAtCharIndex(
        words: List<LyricWord>,
        charIndex: Int
    ): Pair<List<LyricWord>, List<LyricWord>> {
        val leftWords = mutableListOf<LyricWord>()
        val rightWords = mutableListOf<LyricWord>()
        var charPos = 0

        for (word in words) {
            val wordText = word.text.orEmpty()
            val wordEnd = charPos + wordText.length

            when {
                wordEnd <= charIndex -> leftWords.add(word)
                charPos >= charIndex -> rightWords.add(word)
                else -> {
                    val leftLength = charIndex - charPos
                    val rightLength = wordText.length - leftLength
                    val duration = word.end - word.begin
                    val splitMs = word.begin + (duration * leftLength) / wordText.length

                    if (leftLength > 0) {
                        leftWords.add(
                            LyricWord(
                                begin = word.begin,
                                end = splitMs,
                                duration = splitMs - word.begin,
                                text = wordText.substring(0, leftLength),
                                metadata = word.metadata
                            )
                        )
                    }
                    if (rightLength > 0) {
                        rightWords.add(
                            LyricWord(
                                begin = splitMs,
                                end = word.end,
                                duration = word.end - splitMs,
                                text = wordText.substring(leftLength),
                                metadata = word.metadata
                            )
                        )
                    }
                }
            }
            charPos = wordEnd
        }
        return leftWords to rightWords
    }

    /** Finds the nearest user-visible character boundary to the requested pixel position. */
    private fun computeSplitIndex(
        text: String,
        paint: Paint,
        targetWidthPx: Float
    ): Int {
        if (text.isEmpty()) return 0

        val totalWidth = paint.measureText(text)
        if (targetWidthPx <= 0f) return 0
        if (targetWidthPx >= totalWidth) return text.length

        val target = targetWidthPx.coerceIn(0f, totalWidth)
        val boundaries = characterBoundaries(text)
        var lowerIndex = 0
        var lowerWidth = 0f
        var upperIndex = text.length
        var upperWidth = totalWidth

        for (boundary in boundaries) {
            if (boundary == 0) continue
            val width = paint.measureText(text, 0, boundary)
            if (width <= target) {
                lowerIndex = boundary
                lowerWidth = width
            } else {
                upperIndex = boundary
                upperWidth = width
                break
            }
        }

        val lowerDistance = abs(lowerWidth - target)
        val upperDistance = abs(upperWidth - target)
        val nearestIndex = if (lowerDistance <= upperDistance) lowerIndex else upperIndex
        return movePastEnglishWord(text, nearestIndex)
    }

    /**
     * If the pixel target lands inside an ASCII English word, move the split to that word's end.
     * This keeps words such as "love" intact: "i love you" becomes "i love" + "you".
     */
    private fun movePastEnglishWord(
        text: String,
        originalIndex: Int
    ): Int {
        if (originalIndex <= 0 || originalIndex >= text.length) return originalIndex

        val isAsciiAlnum = { c: Char -> c.isLetterOrDigit() && c.code < 128 }
        if (!isAsciiAlnum(text[originalIndex - 1]) || !isAsciiAlnum(text[originalIndex])) {
            return originalIndex
        }

        var forwardIndex = originalIndex
        while (forwardIndex < text.length && isAsciiAlnum(text[forwardIndex])) forwardIndex++
        return forwardIndex
    }

    /** Android's text layout is UTF-16 based, but a split must not cut a grapheme cluster. */
    private fun characterBoundaries(text: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val boundaries = ArrayList<Int>(text.length + 1)
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            boundaries.add(boundary)
            boundary = iterator.next()
        }
        return boundaries
    }
}
