package com.lidesheng.hyperlyric.root.island.content

import com.lidesheng.hyperlyric.common.lyric.LyricPresentation
import com.lidesheng.hyperlyric.common.lyric.METADATA_KEY_ALIGNMENT_RESOLVED
import com.lidesheng.hyperlyric.common.lyric.agentId
import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf
import com.lidesheng.hyperlyric.lyric.view.METADATA_LYRIC_ROW_PLACEHOLDER
import com.lidesheng.hyperlyric.lyric.view.METADATA_NEXT_LINE_PREVIEW

/** Projects resolved vocal sides into one split slot without changing the original row order. */
internal object IslandSplitDuetPresentationResolver {
    fun resolve(
        presentation: LyricPresentation,
        isDuetTrack: Boolean,
        isLeft: Boolean,
        nextLine: IRichLyricLine? = null
    ): LyricPresentation? {
        if (!isDuetTrack) return null
        val sourcePrimary = presentation.primary ?: return null
        if (!sourcePrimary.hasResolvedVocalSide()) return null

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
        if (secondary != null && !secondary.hasResolvedVocalSide()) return null

        val primaryHere = !primary.isAlignedRight == isLeft
        val secondaryHere = secondary != null && !secondary.isAlignedRight == isLeft
        return LyricPresentation(
            primary = when {
                primaryHere -> primary
                secondaryHere -> rowPlaceholder(primary)
                else -> null
            },
            secondary = when {
                secondaryHere -> secondary
                primaryHere && secondary != null -> rowPlaceholder(secondary)
                else -> null
            },
            secondaryContent = presentation.secondaryContent.takeIf { secondaryHere }
        )
    }

    private fun IRichLyricLine.hasResolvedVocalSide(): Boolean =
        isAlignedRight || agentId() != null ||
                metadata?.getBoolean(METADATA_KEY_ALIGNMENT_RESOLVED) == true

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
