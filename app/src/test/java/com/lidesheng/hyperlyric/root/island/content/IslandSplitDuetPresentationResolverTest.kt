package com.lidesheng.hyperlyric.root.island.content

import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplaySettings
import com.lidesheng.hyperlyric.common.lyric.LyricPresentation
import com.lidesheng.hyperlyric.common.lyric.LyricPresentationResolver
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContent
import com.lidesheng.hyperlyric.common.lyric.METADATA_KEY_ALIGNMENT_RESOLVED
import com.lidesheng.hyperlyric.common.lyric.METADATA_SPLIT_LYRIC_FRAGMENT
import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.LyricLineAssembler
import com.lidesheng.hyperlyric.lyric.view.METADATA_LYRIC_ROW_PLACEHOLDER
import com.lidesheng.hyperlyric.lyric.view.METADATA_NEXT_LINE_PREVIEW
import com.lidesheng.hyperlyric.lyric.view.METADATA_PLACEHOLDER_WORD_MOTION
import com.lidesheng.hyperlyric.lyric.view.splitLyricRightAlignment
import org.junit.Assert.*
import org.junit.Test

class IslandSplitDuetPresentationResolverTest {
    @Test
    fun disabledAutoDuetKeepsTextSplitting() {
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(line("A")), enabled = false, isLeft = true,
            songLines = listOf(line("A"), line("B", true)), splitLine = ::unexpectedSplit
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
    fun centeredDuetRowsKeepSingerAlignmentInEitherRowOrder() {
        for (primaryOnRight in listOf(false, true)) {
            val source = LyricPresentation(line("A", primaryOnRight), line("B", !primaryOnRight))
            for (isLeft in listOf(false, true)) {
                val slot = resolve(source, isLeft)
                for (row in listOfNotNull(slot.primary, slot.secondary)) {
                    if (row.metadata?.getBoolean(METADATA_LYRIC_ROW_PLACEHOLDER) == true) continue
                    val right = row.isAlignedRight || splitLyricRightAlignment(
                        row.metadata, centeredLeftSlot = isLeft, defaultRight = false
                    )
                    assertEquals(!isLeft, right)
                }
            }
        }
    }

    @Test
    fun centeredFragmentsAlignToInnerEdgesAndRestoreExplicitRightStyle() {
        // Split fragments retain the source singer's metadata, but no longer own a vocal side.
        val fragmentMetadata = LyricMetadata(mapOf(
            "agent" to "A", METADATA_SPLIT_LYRIC_FRAGMENT to "true"
        ))
        assertTrue(splitLyricRightAlignment(fragmentMetadata, centeredLeftSlot = true, defaultRight = false))
        assertFalse(splitLyricRightAlignment(fragmentMetadata, centeredLeftSlot = false, defaultRight = false))
        assertFalse(splitLyricRightAlignment(line("A").metadata, centeredLeftSlot = true, defaultRight = false))
        assertTrue(splitLyricRightAlignment(line("A").metadata, centeredLeftSlot = false, defaultRight = true))
    }

    @Test
    fun alternatingVocalsWithoutOverlapKeepTextSplitting() {
        val first = line("A", begin = 0, end = 5000)
        val second = line("B", true, begin = 5000, end = 10000)
        val song = listOf(first, second)
        for (line in song) {
            assertNull(IslandSplitDuetPresentationResolver.resolve(
                LyricPresentation(line), enabled = true, isLeft = true,
                songLines = song, splitLine = ::unexpectedSplit
            ))
        }
    }

    @Test
    fun entireOverlappingLinesStayWholeBeforeAndAfterTheSharedInterval() {
        for (firstOnRight in listOf(false, true)) {
            val first = line("A", firstOnRight, begin = 0, end = 10000)
            val second = line("B", !firstOnRight, begin = 5000, end = 15000)
            val song = listOf(first, second)
            val settings = LyricContentDisplaySettings(
                showTranslation = false, showRoma = false, showNextLyric = false,
                order = listOf(LyricSecondaryContent.OVERLAPPING_LINE)
            )
            for (position in listOf(0L, 4999L, 5000L, 9999L, 10000L, 14999L)) {
                val source = LyricPresentationResolver.resolve(
                    activeLines = song.filter { it.begin <= position && position < it.end },
                    nextLine = null, songLines = song, settings = settings,
                    autoDuet = true, position = position
                )
                val firstSlot = resolve(source, isLeft = !firstOnRight, song = song)
                val secondSlot = resolve(source, isLeft = firstOnRight, song = song)
                assertSame(first, firstSlot.primary)
                if (position < second.begin) {
                    assertNull(secondSlot.primary)
                    assertNull(secondSlot.secondary)
                } else {
                    assertPlaceholder(firstSlot.secondary)
                    assertPlaceholder(secondSlot.primary)
                    assertSame(second, secondSlot.secondary)
                }
            }
        }
    }

    @Test
    fun laterUnrelatedLineResumesSplittingAndSongChangeClearsOverlapCache() {
        val first = line("A", begin = 0, end = 10000)
        val second = line("B", true, begin = 5000, end = 15000)
        val later = line("A", begin = 15000, end = 20000)
        val song = listOf(first, second, later)
        resolve(LyricPresentation(first), isLeft = true, song = song)
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(later), enabled = true, isLeft = true,
            songLines = song, splitLine = ::unexpectedSplit
        ))
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(first), enabled = true, isLeft = true,
            songLines = listOf(first), splitLine = ::unexpectedSplit
        ))
    }

    @Test
    fun sameSingerOverlapDoesNotDisableSplitting() {
        val first = line("A", begin = 0, end = 10000)
        val second = line("A", begin = 5000, end = 15000)
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(first), enabled = true, isLeft = true,
            songLines = listOf(first, second), splitLine = ::unexpectedSplit
        ))
    }

    @Test
    fun unknownVocalSideKeepsTextSplitting() {
        assertNull(IslandSplitDuetPresentationResolver.resolve(
            LyricPresentation(RichLyricLine(text = "unknown")), enabled = true, isLeft = true,
            songLines = listOf(line("A"), line("B", true)), splitLine = ::unexpectedSplit
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

    @Test
    fun nonOverlappingNextPreviewStillSplitsOnBothSides() {
        val current = line("A").copy(
            secondary = "C",
            metadata = LyricMetadata(mapOf("agent" to "A", METADATA_NEXT_LINE_PREVIEW to "true"))
        )
        val next = line("C", true, begin = 2000, end = 3000)
        val song = listOf(current, line("B", true), next)
        for (isLeft in listOf(false, true)) {
            val slot = IslandSplitDuetPresentationResolver.resolve(
                LyricPresentation(current, secondaryContent = LyricSecondaryContent.NEXT_LINE),
                enabled = true, isLeft = isLeft, songLines = song, nextLine = next,
                splitLine = { row, isSecondary ->
                    assertTrue(isSecondary)
                    assertEquals("C", row.text)
                    next.copy(text = if (isLeft) "C-left" else "C-right", metadata = row.metadata)
                }
            ) ?: error("Expected a mixed slot")
            if (isLeft) assertEquals("A", slot.primary?.text) else assertPlaceholder(slot.primary)
            assertEquals(if (isLeft) "C-left" else "C-right", slot.secondary?.text)
            assertEquals(LyricSecondaryContent.NEXT_LINE, slot.secondaryContent)
        }
    }

    @Test
    fun ordinaryCurrentLineStillSplitsWhenNextPreviewParticipatesInOverlap() {
        val current = line("A", begin = 0, end = 1000).copy(
            secondary = "B",
            metadata = LyricMetadata(mapOf("agent" to "A", METADATA_NEXT_LINE_PREVIEW to "true"))
        )
        val next = line("B", true, begin = 2000, end = 4000)
        val song = listOf(current, next, line("A", begin = 3000, end = 5000))
        for (isLeft in listOf(false, true)) {
            val slot = IslandSplitDuetPresentationResolver.resolve(
                LyricPresentation(current, secondaryContent = LyricSecondaryContent.NEXT_LINE),
                enabled = true, isLeft = isLeft, songLines = song, nextLine = next,
                splitLine = { row, isSecondary ->
                    assertFalse(isSecondary)
                    assertNull(row.secondary)
                    current.copy(text = if (isLeft) "A-left" else "A-right", secondary = null)
                }
            ) ?: error("Expected a mixed slot")
            assertEquals(if (isLeft) "A-left" else "A-right", slot.primary?.text)
            if (isLeft) assertPlaceholder(slot.secondary) else assertEquals("B", slot.secondary?.text)
        }
    }

    private fun resolve(
        source: LyricPresentation,
        isLeft: Boolean,
        next: RichLyricLine? = null,
        song: List<IRichLyricLine> = listOfNotNull(source.primary, source.secondary, next)
    ) = IslandSplitDuetPresentationResolver.resolve(
        source, enabled = true, isLeft = isLeft, songLines = song,
        nextLine = next, splitLine = ::unexpectedSplit
    ) ?: error("Expected a duet slot")

    private fun unexpectedSplit(line: IRichLyricLine, isSecondary: Boolean): IRichLyricLine =
        error("Unexpected split: ${line.text}, secondary=$isSecondary")

    private fun assertPlaceholder(line: IRichLyricLine?) {
        assertTrue(line?.metadata?.getBoolean(METADATA_LYRIC_ROW_PLACEHOLDER) == true)
    }

    private fun line(text: String, right: Boolean = false, begin: Long = 0, end: Long = 1000) = RichLyricLine(
        begin = begin, end = end,
        text = text, isAlignedRight = right,
        metadata = LyricMetadata(mapOf("agent" to text, METADATA_KEY_ALIGNMENT_RESOLVED to "true"))
    )
}
