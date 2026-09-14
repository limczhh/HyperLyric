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

        // Source-provided secondary content is a normal selectable lane. A resolved content type
        // wins so an explicit song-level choice can place translation/roma before background
        // vocals without throwing the source lane away.
        val hasSourceSecondary = settings.isEnabled(LyricSecondaryContent.BACKGROUND_VOCAL) &&
                (!line.secondary.isNullOrBlank() || !line.secondaryWords.isNullOrEmpty())
        val resolvedContent = line.metadata
            ?.getString(METADATA_RESOLVED_SECONDARY_CONTENT)
            ?.let(LyricSecondaryContent::fromPreferenceValue)
            ?.takeUnless {
                it == LyricSecondaryContent.NEXT_LINE ||
                        it == LyricSecondaryContent.OVERLAPPING_LINE
            }
            ?.takeIf(settings::isEnabled)
        val selectedContent = resolvedContent
            ?: LyricSecondaryContent.BACKGROUND_VOCAL.takeIf { hasSourceSecondary }
            ?: settings.preferredSecondaryContentFor(
                line = line,
                songLines = songLines
            )
        val selectedText = selectedContent?.textOf(line)
        val selectedWords = selectedContent?.wordsOf(line)
        if (selectedText.isNullOrBlank() && selectedWords.isNullOrEmpty()) return line

        val originalText = line.text
        val originalWords = line.words
        val metadata = if (swapSecondary) {
            lyricMetadataOf(
                *(line.metadata?.entries?.map { it.key to it.value } ?: emptyList()).toTypedArray(),
                METADATA_SWAPPED_ORIGINAL to "true"
            )
        } else if (selectedContent == LyricSecondaryContent.BACKGROUND_VOCAL) {
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
