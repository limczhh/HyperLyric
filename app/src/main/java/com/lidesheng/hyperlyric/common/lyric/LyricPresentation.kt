package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine

/**
 * The host-side presentation boundary for one lyric frame.
 *
 * A source may provide more than two active lines, but the Super Island renderer intentionally
 * consumes at most one primary row and one secondary row. The source data itself stays untouched.
 */
internal data class LyricPresentation(
    val primary: IRichLyricLine?,
    val secondary: IRichLyricLine? = null,
    val secondaryContent: LyricSecondaryContent? = null
)

/**
 * Resolves generic rich-lyric data into the two rows available to the host renderer.
 *
 * This does not parse a source format. Providers publish already parsed lines and optional
 * metadata; the host only chooses which of those lines to display and applies the configured
 * two-agent alignment convention.
 */
internal object LyricPresentationResolver {
    private val AGENT_METADATA_KEYS = listOf("agent", "amll:agent")

    fun resolve(
        activeLines: List<IRichLyricLine>,
        nextLine: IRichLyricLine?,
        songLines: List<IRichLyricLine>?,
        settings: LyricContentDisplaySettings,
        autoDuet: Boolean
    ): LyricPresentation {
        val alignedLines = alignAgents(
            lines = activeLines,
            songLines = songLines,
            enabled = autoDuet
        )
        val visibleLines = alignedLines.filter { hasLyricContent(it) }
        val primary = visibleLines.firstOrNull()
            ?: alignedLines.firstOrNull()
            ?: return LyricPresentation(null)
        val overlappingLine = visibleLines
            .drop(1)
            .firstOrNull { hasLyricContent(it) }
        val selectedContent = settings.preferredContentFor(
            line = primary,
            nextLine = nextLine,
            songLines = songLines,
            overlappingLine = overlappingLine
        )
        return LyricPresentation(
            primary = primary,
            secondary = overlappingLine.takeIf {
                selectedContent == LyricSecondaryContent.OVERLAPPING_LINE
            },
            secondaryContent = selectedContent
        )
    }

    private fun alignAgents(
        lines: List<IRichLyricLine>,
        songLines: List<IRichLyricLine>?,
        enabled: Boolean
    ): List<IRichLyricLine> {
        if (!enabled) return lines

        val agents = buildList {
            (songLines.orEmpty() + lines).forEach { line ->
                val agent = line.agentId() ?: return@forEach
                if (agent !in this) add(agent)
            }
        }
        if (agents.size < 2) return lines

        // Keep the first singer on the normal/left side and group subsequent agents on the
        // opposite side. This works with arbitrary provider IDs instead of assuming v1/v2.
        val rightByAgent = agents.withIndex().associate { indexed ->
            indexed.value to (indexed.index > 0)
        }
        return lines.map { line ->
            val right = line.agentId()?.let { rightByAgent[it] } ?: return@map line
            line.withAlignment(right)
        }
    }

    private fun IRichLyricLine.agentId(): String? = AGENT_METADATA_KEYS
        .asSequence()
        .mapNotNull { key -> metadata?.getString(key)?.trim() }
        .firstOrNull { it.isNotEmpty() }

    private fun IRichLyricLine.withAlignment(isAlignedRight: Boolean): IRichLyricLine {
        if (this.isAlignedRight == isAlignedRight) return this
        return RichLyricLine(
            begin = begin,
            end = end,
            duration = duration,
            isAlignedRight = isAlignedRight,
            metadata = metadata,
            text = text,
            words = words,
            secondary = secondary,
            secondaryWords = secondaryWords,
            translation = translation,
            translationWords = translationWords,
            roma = roma
        )
    }
}

private fun hasLyricContent(line: IRichLyricLine?): Boolean =
    line != null && (!line.text.isNullOrBlank() || !line.words.isNullOrEmpty())
