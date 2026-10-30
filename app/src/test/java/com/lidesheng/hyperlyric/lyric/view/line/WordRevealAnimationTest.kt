package com.lidesheng.hyperlyric.lyric.view.line

import org.junit.Assert.assertEquals
import org.junit.Test

class WordRevealAnimationTest {
    @Test
    fun noAnimationAppearsAtTheWordStartAndSeekingBackHidesIt() {
        val animation = WordRevealAnimation.NONE
        assertEquals(0f, animation.progress(999.0, 1000, 2000, 200), 0f)
        assertEquals(1f, animation.progress(1000.0, 1000, 2000, 200), 0f)
        assertEquals(1f, animation.progress(3000.0, 1000, 2000, 200), 0f)
        assertEquals(0f, animation.progress(800.0, 1000, 2000, 200), 0f)
    }

    @Test
    fun animationUsesSystemDurationWithoutExtendingPastTheWordEnd() {
        for (animation in WordRevealAnimation.entries.filter { it != WordRevealAnimation.NONE }) {
            assertEquals(0f, animation.progress(1000.0, 1000, 2000, 200), 0f)
            assertEquals(0.5f, animation.progress(1100.0, 1000, 2000, 200), 0f)
            assertEquals(1f, animation.progress(1200.0, 1000, 2000, 200), 0f)
            assertEquals(0.5f, animation.progress(1020.0, 1000, 1040, 200), 0f)
            assertEquals(1f, animation.progress(1040.0, 1000, 1040, 200), 0f)
        }
    }
}
