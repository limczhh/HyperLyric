package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf

/** Marks a line whose original lyric was moved into the secondary rendering row. */
const val METADATA_SWAPPED_ORIGINAL = "swappedOriginal"

/** Applies the source-independent primary/secondary content transformations. */
object LyricSecondaryContentTransformer {
    fun apply(
        line: IRichLyricLine,
        settings: LyricContentDisplaySettings,
        onlySecondary: Boolean,
        swapSecondary: Boolean,
        songLines: List<IRichLyricLine>? = null
    ): IRichLyricLine {
        if (!onlySecondary && !swapSecondary) return line

        // Preserve a source-provided secondary lane as the generic "other" secondary content.
        // It is intentionally not part of the user-sortable translation/Roma/next-line list.
        val hasSourceSecondary = !line.secondary.isNullOrBlank() ||
                !line.secondaryWords.isNullOrEmpty()
        val selectedContent = if (hasSourceSecondary) {
            null
        } else {
            line.metadata
                ?.getString(METADATA_RESOLVED_SECONDARY_CONTENT)
                ?.let(LyricSecondaryContent::fromPreferenceValue)
                ?.takeUnless { it == LyricSecondaryContent.NEXT_LINE }
                ?: settings.preferredSecondaryContentFor(
                    line = line,
                    songLines = songLines
                )
        }
        val selectedText = if (hasSourceSecondary) line.secondary else selectedContent?.textOf(line)
        val selectedWords = if (hasSourceSecondary) {
            line.secondaryWords
        } else {
            selectedContent?.wordsOf(line)
        }
        if (selectedText.isNullOrBlank() && selectedWords.isNullOrEmpty()) return line

        val originalText = line.text
        val originalWords = line.words
        val metadata = if (swapSecondary) {
            lyricMetadataOf(
                *(line.metadata?.entries?.map { it.key to it.value } ?: emptyList()).toTypedArray(),
                METADATA_SWAPPED_ORIGINAL to "true"
            )
        } else if (hasSourceSecondary) {
            lyricMetadataOf(
                *(line.metadata?.entries?.filter {
                    it.key != METADATA_RESOLVED_SECONDARY_CONTENT
                }?.map { it.key to it.value } ?: emptyList()).toTypedArray()
            )
        } else {
            line.metadata
        }
        return RichLyricLine(
            begin = line.begin,
            end = line.end,
            duration = line.duration,
            isAlignedRight = line.isAlignedRight,
            metadata = metadata,
            text = selectedText,
            words = selectedWords,
            secondary = originalText.takeIf { swapSecondary },
            secondaryWords = originalWords.takeIf { swapSecondary },
            translation = null,
            translationWords = null,
            roma = null
        )
    }
}
