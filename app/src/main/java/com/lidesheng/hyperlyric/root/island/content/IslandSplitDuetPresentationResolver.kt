package com.lidesheng.hyperlyric.root.island.content

import com.lidesheng.hyperlyric.common.lyric.LyricPresentation
import com.lidesheng.hyperlyric.common.lyric.agentId
import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf
import com.lidesheng.hyperlyric.lyric.view.METADATA_LYRIC_ROW_PLACEHOLDER
import com.lidesheng.hyperlyric.lyric.view.METADATA_NEXT_LINE_PREVIEW

/** Projects resolved vocal sides into one split slot without changing the original row order. */
internal object IslandSplitDuetPresentationResolver {
    private data class VocalTiming(val begin: Long, val end: Long, val agent: String)

    private var cachedSongLines: List<IRichLyricLine>? = null
    private val overlapByLine = HashMap<VocalTiming, Boolean>()

    fun resolve(
        presentation: LyricPresentation,
        enabled: Boolean,
        isLeft: Boolean,
        songLines: List<IRichLyricLine>?,
        nextLine: IRichLyricLine? = null,
        splitLine: (IRichLyricLine, Boolean) -> IRichLyricLine
    ): LyricPresentation? {
        if (!enabled) return null
        val sourcePrimary = presentation.primary ?: return null

        val isPreview = sourcePrimary.metadata?.getBoolean(METADATA_NEXT_LINE_PREVIEW) == true
        val primary = if (isPreview && nextLine != null) {
            sourcePrimary.toRichLine().copy(
                metadata = sourcePrimary.metadata?.filterKeys { it != METADATA_NEXT_LINE_PREVIEW }
                    ?.let(::LyricMetadata),
                secondary = null,
                secondaryWords = null
            )
        } else {
            sourcePrimary
        }
        val secondary = presentation.secondary ?: if (isPreview && nextLine != null) {
            RichLyricLine(
                begin = nextLine.begin,
                end = nextLine.end,
                text = sourcePrimary.secondary,
                words = emptyList(),
                isAlignedRight = nextLine.isAlignedRight,
                metadata = lyricMetadataOf(
                    *(nextLine.metadata?.entries?.map { it.key to it.value }.orEmpty()).toTypedArray(),
                    METADATA_NEXT_LINE_PREVIEW to "true"
                )
            )
        } else {
            null
        }
        val routePrimary = participatesInOverlap(primary, songLines)
        val routeSecondary = secondary != null && participatesInOverlap(secondary, songLines)
        if (!routePrimary && !routeSecondary) return null

        val primaryHere = !routePrimary || !primary.isAlignedRight == isLeft
        val secondaryHere = secondary != null && (!routeSecondary || !secondary.isAlignedRight == isLeft)
        return LyricPresentation(
            primary = when {
                primaryHere -> if (routePrimary) primary else splitLine(primary, false)
                secondaryHere -> rowPlaceholder(primary)
                else -> null
            },
            secondary = when {
                secondaryHere -> if (routeSecondary) secondary else splitLine(secondary, true)
                primaryHere && secondary != null -> rowPlaceholder(secondary)
                else -> null
            },
            secondaryContent = presentation.secondaryContent.takeIf { secondaryHere }
        )
    }

    /** Membership belongs to the complete source line, independent of the playback position. */
    private fun participatesInOverlap(
        line: IRichLyricLine,
        songLines: List<IRichLyricLine>?
    ): Boolean {
        if (songLines !== cachedSongLines) {
            cachedSongLines = songLines
            overlapByLine.clear()
        }
        val agent = line.agentId() ?: return false
        if (line.end <= line.begin) return false
        return overlapByLine.getOrPut(VocalTiming(line.begin, line.end, agent)) {
            songLines.orEmpty().any { other ->
                other.end > other.begin && other.begin < line.end && line.begin < other.end &&
                        (!other.text.isNullOrBlank() || !other.words.isNullOrEmpty()) &&
                        other.agentId()?.let { it != agent } == true
            }
        }
    }

    // Keep the source row's timing and words for measurement; the assembler suppresses its text.
    private fun rowPlaceholder(reference: IRichLyricLine): IRichLyricLine =
        reference.toRichLine().copy(metadata = lyricMetadataOf(
            *(reference.metadata?.entries?.map { it.key to it.value }.orEmpty()).toTypedArray(),
            METADATA_LYRIC_ROW_PLACEHOLDER to "true"
        ))

    private fun IRichLyricLine.toRichLine() = RichLyricLine(
        begin = begin, end = end, duration = duration,
        isAlignedRight = isAlignedRight, metadata = metadata,
        text = text, words = words,
        secondary = secondary, secondaryWords = secondaryWords,
        translation = translation, translationWords = translationWords, roma = roma
    )
}
