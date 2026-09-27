@file:Suppress("unused")

package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import java.util.Locale

internal const val METADATA_KEY_AGENT_TYPE = "amll:agent-type"

/** Metadata keys that carry one vocal lane identity, in priority order. */
internal val AGENT_METADATA_KEYS = listOf(
    "agent",
    "amll:agent",
    "vocal",
    "amll:vocal"
)

/** Metadata keys that carry the declared type of a vocal lane identity. */
internal val AGENT_TYPE_METADATA_KEYS = listOf(
    METADATA_KEY_AGENT_TYPE,
    "agent:type",
    "agentType",
    "vocal:type"
)

/** AMLL declares a shared chorus lane under this type; it never identifies one singer. */
private const val GROUP_AGENT_TYPE = "group"

internal fun IRichLyricLine.agentId(): String? = AGENT_METADATA_KEYS
    .asSequence()
    .mapNotNull { key -> metadata?.getString(key)?.trim() }
    .firstOrNull { it.isNotEmpty() }

internal fun IRichLyricLine.agentType(): String? = AGENT_TYPE_METADATA_KEYS
    .asSequence()
    .mapNotNull { key -> metadata?.getString(key)?.trim()?.lowercase(Locale.ROOT) }
    .firstOrNull { it.isNotEmpty() }

private fun IRichLyricLine.performerId(): String? {
    val agent = agentId() ?: return null
    return agent.takeUnless { agentType() == GROUP_AGENT_TYPE }
}

private fun IRichLyricLine.hasRenderableLyric(): Boolean =
    !text.isNullOrBlank() || !words.isNullOrEmpty()

/**
 * Song-scoped duet detection for the whole lyric list of one song.
 *
 * The renderer reports an independent duet row only while two vocal lanes overlap at the current
 * position, so any verdict sampled from a lyric View expires together with the line that produced
 * it. Callers that must hold one presentation for the complete song compute the verdict here once
 * and keep it until the song changes.
 */
internal object DuetLyricPolicy {

    fun hasDuet(lines: List<IRichLyricLine>?): Boolean {
        if (lines.isNullOrEmpty()) return false
        return countsMultiplePerformers(lines) || hasOverlappingDistinctAgents(lines)
    }

    /** True when the whole song alternates between at least two individual singers. */
    private fun countsMultiplePerformers(lines: List<IRichLyricLine>): Boolean {
        val performers = HashSet<String>(4)
        lines.forEach { line ->
            if (!line.hasRenderableLyric()) return@forEach
            val performer = line.performerId() ?: return@forEach
            performers.add(performer)
            if (performers.size >= 2) return true
        }
        return false
    }

    /** True when two different vocal identities are timed over each other. */
    private fun hasOverlappingDistinctAgents(lines: List<IRichLyricLine>): Boolean {
        if (lines.size < 2) return false
        val furthestEndByAgent = HashMap<String, Long>(4)
        lines.sortedBy { it.begin }.forEach { line ->
            if (!line.hasRenderableLyric()) return@forEach
            val agent = line.agentId() ?: return@forEach
            val overlapsOtherAgent = furthestEndByAgent.any { (otherAgent, furthestEnd) ->
                otherAgent != agent && line.begin < furthestEnd
            }
            if (overlapsOtherAgent) return true
            val furthestEnd = furthestEndByAgent[agent]
            if (furthestEnd == null || furthestEnd < line.end) {
                furthestEndByAgent[agent] = line.end
            }
        }
        return false
    }
}
