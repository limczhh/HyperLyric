package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class ControlCenterMediaCoverShapeTest {
    class Header : LinearLayout(RuntimeEnvironment.getApplication()) {
        var coverRadius = 14f
    }

    @Test
    fun modalUsesOuterClipAndRestoresNativeOutlinesDuringPanelMorph() {
        val header = Header()
        val content = View(header.context).apply { clipToOutline = true }
        val parent = View(header.context).apply { clipToOutline = true }
        val cover = View(header.context).apply { clipToOutline = true }
        val shape = ControlCenterMediaCoverShape(header, content, parent, cover)

        shape.apply(outerHostClips = true, transitioning = false)
        assertFalse(content.clipToOutline)
        assertFalse(parent.clipToOutline)
        assertFalse(cover.clipToOutline)

        shape.prepareTransition()
        assertEquals(0f, header.coverRadius, 0f)
        header.coverRadius = 6f // An intermediate native spring frame.
        shape.apply(outerHostClips = true, transitioning = true)
        assertTrue(parent.clipToOutline)
        assertTrue(cover.clipToOutline)
        assertEquals(6f, header.coverRadius, 0f)

        shape.apply(outerHostClips = true, transitioning = false)
        assertFalse(cover.clipToOutline)
        shape.restore()
        assertTrue(content.clipToOutline)
        assertTrue(parent.clipToOutline)
        assertTrue(cover.clipToOutline)
        assertEquals(14f, header.coverRadius, 0f)
    }

    @Test
    fun panelRetainsContentClipAndDoesNotInventNativeCoverClipping() {
        val header = Header()
        val content = View(header.context).apply { clipToOutline = true }
        val parent = View(header.context)
        val cover = View(header.context)
        val shape = ControlCenterMediaCoverShape(header, content, parent, cover)

        shape.apply(outerHostClips = false, transitioning = false)
        assertTrue(content.clipToOutline)
        shape.apply(outerHostClips = false, transitioning = true)
        assertFalse(parent.clipToOutline)
        assertFalse(cover.clipToOutline)
        shape.restore()
        assertTrue(content.clipToOutline)
        assertFalse(parent.clipToOutline)
        assertFalse(cover.clipToOutline)
    }
}
