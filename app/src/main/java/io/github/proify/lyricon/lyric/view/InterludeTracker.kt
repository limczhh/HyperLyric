/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.view

internal class InterludeTracker(private val minGapMs: Long = 8000) {

    fun evaluate(
        posMs: Long,
        matches: List<TimedLine>,
        current: Interlude?
    ): Interlude? {
        current?.let { if (posMs in (it.start + 1) until it.end) return it }

        if (matches.isEmpty()) return null
        val cur = matches.last()
        val next = cur.next ?: return null

        if (next.begin - cur.end <= minGapMs) return null
        if (posMs <= cur.end || posMs >= next.begin) return null

        return Interlude(cur.end, next.begin)
    }

    data class Interlude(val start: Long, val end: Long) {
        val duration get() = end - start
    }
}
