package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import java.util.Locale

internal const val METADATA_KEY_ALIGNMENT_RESOLVED = "hyperlyric:alignment-resolved"
internal const val METADATA_KEY_AGENT_TYPE = "amll:agent-type"

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
    private val AGENT_METADATA_KEYS = listOf(
        "agent",
        "amll:agent",
        "vocal",
        "amll:vocal"
    )
    private val AGENT_TYPE_METADATA_KEYS = listOf(
        METADATA_KEY_AGENT_TYPE,
        "agent:type",
        "agentType",
        "vocal:type"
    )

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
        // A timing collision alone is ambiguous: ordinary sources commonly use
        // end == next.begin for adjacent lines. Only a pair with explicit,
        // distinct vocal identities may occupy the independent duet row.
        val overlappingLine = visibleLines
            .drop(1)
            .firstOrNull { candidate ->
                hasLyricContent(candidate) && hasDistinctVocalIdentities(primary, candidate)
            }
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
        val hasExplicitAgentTypes = (songLines.orEmpty() + lines).any {
            it.agentType() != null
        }
        if (hasExplicitAgentTypes) {
            return alignTypedAgents(lines)
        }
        if (agents.size < 2) return lines

        // Keep the first singer on the normal/left side and group subsequent agents on the
        // opposite side. This works with arbitrary provider IDs instead of assuming v1/v2.
        val rightByAgent = agents.withIndex().associate { indexed ->
            indexed.value to (indexed.index > 0)
        }
        return lines.map { line ->
            if (line.metadata?.getBoolean(METADATA_KEY_ALIGNMENT_RESOLVED) == true) {
                return@map line
            }
            val right = line.agentId()?.let { rightByAgent[it] } ?: return@map line
            if (line.isAlignedRight) return@map line
            line.withAlignment(right)
        }
    }

    /**
     * AMLL declares `group` agents as a single singer and `other` agents as the first
     * alternating singer. This is deliberately host-side so the rule also applies to future
     * non-AMLL sources that publish the same metadata contract.
     */
    private fun alignTypedAgents(lines: List<IRichLyricLine>): List<IRichLyricLine> {
        var lastAgent: String? = null
        var lastRight = false
        return lines.map { line ->
            if (line.metadata?.getBoolean(METADATA_KEY_ALIGNMENT_RESOLVED) == true) {
                return@map line
            }
            val agent = line.agentId() ?: return@map line
            if (line.agentType() == "group") return@map line

            val right = when {
                lastAgent == null -> {
                    lastAgent = agent
                    lastRight = line.agentType() == "other"
                    lastRight
                }

                lastAgent == agent -> lastRight
                else -> {
                    lastAgent = agent
                    lastRight = !lastRight
                    lastRight
                }
            }
            if (line.isAlignedRight) line else line.withAlignment(right)
        }
    }

    private fun IRichLyricLine.agentId(): String? = AGENT_METADATA_KEYS
        .asSequence()
        .mapNotNull { key -> metadata?.getString(key)?.trim() }
        .firstOrNull { it.isNotEmpty() }

    private fun IRichLyricLine.agentType(): String? = AGENT_TYPE_METADATA_KEYS
        .asSequence()
        .mapNotNull { key -> metadata?.getString(key)?.trim()?.lowercase(Locale.ROOT) }
        .firstOrNull { it.isNotEmpty() }

    private fun hasDistinctVocalIdentities(
        primary: IRichLyricLine,
        candidate: IRichLyricLine
    ): Boolean {
        val primaryAgent = primary.agentId() ?: return false
        val candidateAgent = candidate.agentId() ?: return false
        return primaryAgent != candidateAgent
    }

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
