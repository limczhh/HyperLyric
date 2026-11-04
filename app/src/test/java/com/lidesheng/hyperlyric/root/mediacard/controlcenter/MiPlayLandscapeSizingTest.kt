package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.view.Gravity
import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.systemui.statusbar.notification.modal.ModalQSControlDetail
import com.android.systemui.statusbar.notification.modal.ModalWindowView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE, qualifiers = "w1000dp-h400dp-land-mdpi")
class MiPlayLandscapeSizingTest {
    @Test
    fun rightHandNextButtonReceivesTouchesAndModalWidthsAreRestored() {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(root)
        val window = ModalWindowView(context).apply { setPadding(32, 0, 32, 0) }
        root.addView(window, FrameLayout.LayoutParams(400, -1, Gravity.CENTER))
        val modal = ModalQSControlDetail(context)
        window.addView(modal, FrameLayout.LayoutParams(-1, -1))
        val card = FrameLayout(context)
        modal.addView(card, FrameLayout.LayoutParams(324, 240, Gravity.CENTER))
        val content = LinearLayout(context)
        card.addView(content, FrameLayout.LayoutParams(-1, -1))
        val next = View(context)
        content.addView(next, LinearLayout.LayoutParams(44, 44))
        var clicks = 0
        next.setOnClickListener { clicks++ }
        val sizing = MiPlayLandscapeSizing(content)
        sizing.prepare()
        root.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1000, 400)
        // Put Next at the right edge of the widened content, beyond the old
        // 400px modal window. Real touch dispatch must still reach that child.
        next.layout(content.width - 60, 30, content.width - 16, 74)
        assertTrue(window.width >= card.width + window.paddingLeft + window.paddingRight)
        val bounds = Rect()
        next.getDrawingRect(bounds)
        root.offsetDescendantRectToMyCoords(next, bounds)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 10, action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            assertTrue(root.dispatchTouchEvent(event))
            event.recycle()
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
        sizing.restore()
        assertEquals(400, window.layoutParams.width)
        assertEquals(324, card.layoutParams.width)
        assertEquals(-1, modal.layoutParams.width)
    }
}
