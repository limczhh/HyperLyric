/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameDeltaTrackerTest {

    @Test
    fun firstFrameDoesNotAdvance() {
        val tracker = FrameDeltaTracker()
        assertEquals(0L, tracker.deltaNanos(1_000L))
        assertEquals(16L, tracker.deltaNanos(1_016L))
    }

    @Test
    fun rebaseDropsTheSpanCoveredByTheSample() {
        val tracker = FrameDeltaTracker()
        tracker.deltaNanos(1_000L)
        tracker.rebase(1_100L)
        assertEquals(16L, tracker.deltaNanos(1_116L))
    }

    @Test
    fun rebaseWithoutAnchorIsIgnored() {
        val tracker = FrameDeltaTracker()
        tracker.rebase(5_000L)
        assertEquals(0L, tracker.deltaNanos(1_000L))
    }

    @Test
    fun staleFrameKeepsTheAnchor() {
        val tracker = FrameDeltaTracker()
        tracker.deltaNanos(1_000L)
        tracker.rebase(1_100L)
        assertEquals(0L, tracker.deltaNanos(1_050L))
        assertEquals(16L, tracker.deltaNanos(1_116L))
    }

    @Test
    fun resetRestoresFirstFrameSemantics() {
        val tracker = FrameDeltaTracker()
        tracker.deltaNanos(1_000L)
        tracker.reset()
        assertEquals(0L, tracker.deltaNanos(2_000L))
    }
}
