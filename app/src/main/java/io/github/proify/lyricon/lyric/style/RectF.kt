package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class RectF(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
)
