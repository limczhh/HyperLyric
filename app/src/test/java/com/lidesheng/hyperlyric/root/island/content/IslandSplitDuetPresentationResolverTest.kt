package com.lidesheng.hyperlyric.root.island.content

import com.lidesheng.hyperlyric.common.lyric.LyricPresentation
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContent
import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.view.LyricLineAssembler
import com.lidesheng.hyperlyric.lyric.view.METADATA_LYRIC_ROW_PLACEHOLDER
import com.lidesheng.hyperlyric.lyric.view.METADATA_NEXT_LINE_PREVIEW
import com.lidesheng.hyperlyric.lyric.view.METADATA_PLACEHOLDER_WORD_MOTION
import org.junit.Assert.*
import org.junit.Test

class IslandSplitDuetPresentationResolverTest {
    @Test
    fun ordinaryTrackKeepsTextSplitting() {
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(line("A")), isDuetTrack = false, isLeft = true
        ))
    }

    @Test
    fun bothVocalOrdersPreserveMainAndSecondaryRows() {
        for (primaryOnRight in listOf(false, true)) {
            val primary = line("A", primaryOnRight)
            val secondary = line("B", !primaryOnRight)
            val source = LyricPresentation(primary, secondary, LyricSecondaryContent.OVERLAPPING_LINE)
            val upperSlot = resolve(source, isLeft = !primaryOnRight)
            val lowerSlot = resolve(source, isLeft = primaryOnRight)
            assertSame(primary, upperSlot.primary)
            assertPlaceholder(upperSlot.secondary)
            assertPlaceholder(lowerSlot.primary)
            assertSame(secondary, lowerSlot.secondary)
        }
    }

    @Test
    fun alternatingSingleVocalOnlyOccupiesItsOwnSlot() {
        val primary = line("B", true)
        val source = LyricPresentation(primary)
        assertSame(primary, resolve(source, isLeft = false).primary)
        assertNull(resolve(source, isLeft = true).primary)
    }

    @Test
    fun unknownVocalSideKeepsTextSplitting() {
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(RichLyricLine(text = "unknown")), isDuetTrack = true, isLeft = true
        ))
    }

    @Test
    fun placeholderIsBlankButRetainsWordMotionMeasurementAndSecondaryVisibility() {
        val secondary = line("B", true).copy(words = listOf(
            LyricWord(begin = 0, end = 1000, text = "B")
        ))
        val slot = resolve(LyricPresentation(line("A"), secondary), isLeft = true)
        val result = LyricLineAssembler().buildSecondary(slot.primary, slot.secondary)
        assertTrue(result.alwaysShow)
        assertEquals("", result.line.text)
        assertTrue(result.line.words.isNullOrEmpty())
        assertTrue(result.line.metadata?.getBoolean(METADATA_PLACEHOLDER_WORD_MOTION) == true)
    }

    @Test
    fun oppositeVocalPreviewUsesNextSingersSlotAndStaysStatic() {
        val current = line("A").copy(
            secondary = "B",
            metadata = LyricMetadata(mapOf("agent" to "A", METADATA_NEXT_LINE_PREVIEW to "true"))
        )
        val next = line("B", true)
        val source = LyricPresentation(current, secondaryContent = LyricSecondaryContent.NEXT_LINE)
        val left = resolve(source, isLeft = true, next = next)
        val right = resolve(source, isLeft = false, next = next)
        assertNull(left.primary?.secondary)
        assertPlaceholder(left.secondary)
        assertPlaceholder(right.primary)
        assertEquals("B", right.secondary?.text)
        val assembler = LyricLineAssembler(enableRelativeProgress = true)
        val preview = assembler.buildSecondary(right.primary, right.secondary)
        assertTrue(preview.isNextLinePreview)
        assertTrue(preview.line.words.isNullOrEmpty())
        val blank = assembler.buildSecondary(left.primary, left.secondary)
        assertFalse(blank.isNextLinePreview)
        assertEquals("", blank.line.text)
        assertTrue(blank.alwaysShow)
    }

    private fun resolve(source: LyricPresentation, isLeft: Boolean, next: RichLyricLine? = null) =
        IslandSplitDuetPresentationResolver.resolve(source, true, isLeft, next)
            ?: error("Expected a duet slot")

    private fun assertPlaceholder(line: com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine?) {
        assertTrue(line?.metadata?.getBoolean(METADATA_LYRIC_ROW_PLACEHOLDER) == true)
    }

    private fun line(text: String, right: Boolean = false) = RichLyricLine(
        text = text, isAlignedRight = right, metadata = LyricMetadata(mapOf("agent" to text))
    )
}
