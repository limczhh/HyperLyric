package com.lidesheng.hyperlyric.lyric.view.line

/** Each source word is revealed as one unit, without synthetic character timings. */
enum class WordRevealAnimation {
    NONE,
    FADE_IN,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_RIGHT,
    SLIDE_LEFT,
    ZOOM_IN,
    BOUNCE_IN,
    FLIP_IN,
    WIPE_IN;

    fun progress(positionMs: Double, begin: Long, end: Long, durationMs: Int): Float {
        if (positionMs < begin) return 0f
        if (this == NONE) return 1f
        val duration = minOf(durationMs.toLong(), end - begin)
        if (duration <= 0L) return 1f
        return ((positionMs - begin) / duration).toFloat().coerceIn(0f, 1f)
    }
}
