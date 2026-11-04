package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class ControlCenterMediaCoverFlipRefreshTest {
    private class Card {
        private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val header = FrameLayout(activity)
        val cover = View(activity)
        var flipping = true
        var allowed = true
        var displayed: Bitmap? = null
        var applications = 0
        val refresh = ControlCenterMediaCoverFlipRefresh(header, { flipping }, { allowed }, { _, bitmap ->
            displayed = bitmap
            applications++
        })

        init {
            header.addView(cover, FrameLayout.LayoutParams(96, 96))
            activity.setContentView(header)
            assertTrue(header.isAttachedToWindow)
        }

        fun start(bitmap: Bitmap?) {
            refresh.update(bitmap)
            refresh.begin(cover, bitmap)
            refresh.afterStart()
        }

        fun frame(angle: Float, ongoing: Boolean = true) {
            cover.rotationY = angle
            flipping = ongoing
            header.viewTreeObserver.dispatchOnPreDraw()
        }
    }

    private fun artwork() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test
    fun artworkArrivingDuringFlipIsVisibleAtTheMidpoint() {
        val card = Card()
        val old = artwork()
        val latest = artwork()
        card.start(old)
        card.refresh.update(latest)
        card.frame(45f)
        assertEquals(0, card.applications)
        card.frame(95f)
        assertSame(latest, card.displayed)
        card.frame(180f, false)
        assertEquals(1, card.applications)
    }

    @Test
    fun artworkArrivingAfterMidpointDoesNotWaitForPlaybackOrSeekEvents() {
        val card = Card()
        val old = artwork()
        val latest = artwork()
        card.start(old)
        card.frame(95f)
        assertSame(old, card.displayed)
        card.refresh.update(latest)
        card.frame(140f)
        assertSame(latest, card.displayed)
        card.frame(180f, false)
        assertEquals(2, card.applications)
    }

    @Test
    fun finalArtworkIsFlushedEvenWhenNativeMidpointAndQueuedUpdateWereSkipped() {
        val card = Card()
        val latest = artwork()
        card.start(artwork())
        card.refresh.update(artwork())
        card.refresh.update(latest)
        card.frame(180f, false)
        assertSame(latest, card.displayed)
        assertEquals(1, card.applications)
        card.frame(180f, false)
        assertEquals(1, card.applications)
    }

    @Test
    fun aStaleDelayedFlipCannotReplaceTheLatestArtworkAndNullClearsIt() {
        val card = Card()
        val old = artwork()
        val latest = artwork()
        card.start(old)
        card.refresh.update(latest)
        card.frame(180f, false)
        card.flipping = true
        card.refresh.begin(card.cover, old)
        card.refresh.afterStart()
        card.frame(275f)
        assertSame(latest, card.displayed)
        card.refresh.update(null)
        card.frame(360f, false)
        assertNull(card.displayed)
    }

    @Test
    fun synchronousCompletionFlushesArtworkButClosingTheCardDoesNotPaint() {
        val card = Card()
        val latest = artwork()
        card.flipping = false
        card.start(latest)
        assertSame(latest, card.displayed)
        assertEquals(1, card.applications)
        card.flipping = true
        card.start(artwork())
        card.allowed = false
        card.frame(180f, false)
        assertSame(latest, card.displayed)
        assertEquals(1, card.applications)
    }
}
