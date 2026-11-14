/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmllDotsGeometryTest {

    @Test
    fun contentWidthReservesScaledGroupBetweenPaddings() {
        val expected = 2f * AmllInterludeTimeline.DOT_PADDING_X_EM +
            AmllInterludeTimeline.DOT_GROUP_WIDTH_EM * AmllInterludeTimeline.BREATHE_MAX_SCALE
        assertEquals(expected, AmllInterludeTimeline.CONTENT_WIDTH_EM, 1e-6f)
    }

    @Test
    fun dotsStayInsideContentBoxForEveryScale() {
        for (step in 0..25) {
            val scale = 1f + step * 0.01f
            val left = leftEdgeEm(0, scale)
            val right = rightEdgeEm(AmllInterludeTimeline.DOT_COUNT - 1, scale)
            assertTrue("左边界越界 @$scale: $left", left >= 0f)
            assertTrue(
                "右边界越界 @$scale: $right",
                right <= AmllInterludeTimeline.CONTENT_WIDTH_EM,
            )
        }
    }

    @Test
    fun paddingsAreExactAtPeakScale() {
        val peak = AmllInterludeTimeline.BREATHE_MAX_SCALE
        assertEquals(AmllInterludeTimeline.DOT_PADDING_X_EM, leftEdgeEm(0, peak), 1e-5f)
        assertEquals(
            AmllInterludeTimeline.CONTENT_WIDTH_EM - AmllInterludeTimeline.DOT_PADDING_X_EM,
            rightEdgeEm(AmllInterludeTimeline.DOT_COUNT - 1, peak),
            1e-5f,
        )
    }

    private fun leftEdgeEm(index: Int, scale: Float): Float =
        AmllInterludeTimeline.dotCenterXEm(index, scale) - AmllInterludeTimeline.dotRadiusEm(scale)

    private fun rightEdgeEm(index: Int, scale: Float): Float =
        AmllInterludeTimeline.dotCenterXEm(index, scale) + AmllInterludeTimeline.dotRadiusEm(scale)
}
