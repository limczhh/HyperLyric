package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class RainbowTextColor(
    var normal: IntArray = intArrayOf(),
    var background: IntArray = intArrayOf(),
    var highlight: IntArray = intArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RainbowTextColor

        if (!normal.contentEquals(other.normal)) return false
        if (!background.contentEquals(other.background)) return false
        if (!highlight.contentEquals(other.highlight)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = normal.contentHashCode()
        result = 31 * result + background.contentHashCode()
        result = 31 * result + highlight.contentHashCode()
        return result
    }
}
