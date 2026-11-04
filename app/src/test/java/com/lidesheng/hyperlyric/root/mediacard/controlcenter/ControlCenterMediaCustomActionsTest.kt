package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE, qualifiers = "port-mdpi")
class ControlCenterMediaCustomActionsTest {
    @Test
    fun fiveButtonsRetainTheMeasuredNativeGapAndIndependentBounds() = checkLayout(2)

    @Test
    fun oneCustomActionAlsoRetainsTheNativeGap() = checkLayout(1)

    @Test
    fun noCustomActionsStillReserveBothOuterSlots() = checkLayout(0)

    private fun checkLayout(customCount: Int) {
        val context = RuntimeEnvironment.getApplication()
        val row = ConstraintLayout(context)
        val native = List(3) { ImageView(context).apply { id = View.generateViewId() } }
        native.forEachIndexed { index, view ->
            row.addView(view, ConstraintLayout.LayoutParams(44, 44).apply {
                topToTop = 0
                bottomToBottom = 0
                startToStart = if (index == 0) 0 else -1
                startToEnd = if (index > 0) native[index - 1].id else -1
                endToEnd = if (index == 2) 0 else -1
                endToStart = if (index < 2) native[index + 1].id else -1
                horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_SPREAD_INSIDE
            })
        }
        layout(row, 160)
        val gap = native[1].left - native[0].right
        assertTrue(gap > 0)
        val session = MediaSession(context, "actions-layout")
        try {
            val controller = session.controller
            shadowOf(controller).setPackageName(context.packageName)
            val state = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
            repeat(customCount) { index ->
                state.addCustomAction(PlaybackState.CustomAction.Builder(
                    "custom-$index", "Custom $index", android.R.drawable.ic_media_play
                ).build())
            }
            shadowOf(controller).setPlaybackState(state.build())
            shadowOf(context.getSystemService(MediaSessionManager::class.java)).addController(controller)
            assertEquals(customCount, controller.playbackState!!.customActions.size)
            assertEquals(1, context.getSystemService(MediaSessionManager::class.java).getActiveSessions(null).size)
            assertNotNull(context.packageManager.getResourcesForApplication(controller.packageName)
                .getDrawable(android.R.drawable.ic_media_play, context.theme))
            val actions = ControlCenterMediaCustomActions(row, TextView(context), true)
            actions.captureNativeGeometry()
            assertTrue(actions.refresh())
            layout(row, actions.expandedWidth(row, 160))
            val ordered = (0 until row.childCount).map(row::getChildAt).sortedBy { it.left }
            assertEquals(5, ordered.size)
            ordered.zipWithNext { left, right -> assertEquals(gap, right.left - left.right) }
            assertEquals(native, ordered.subList(1, 4))
            assertEquals(row.width / 2, (native[1].left + native[1].right) / 2)
            assertEquals(customCount, ordered.filter { it !in native }.count { it.visibility == View.VISIBLE })
            ordered.filter { it !in native && it.visibility != View.VISIBLE }.forEach {
                assertEquals(View.INVISIBLE, it.visibility)
                assertFalse(it.isClickable)
                assertFalse(it.isFocusable)
                assertFalse(it.hasOnClickListeners())
            }
            ordered.forEach { assertEquals(44, it.width); assertEquals(44, it.height) }
            val icons = ordered.filter { it !in native }.map { (it as ImageView).drawable }
            repeat(5) { assertFalse(actions.refresh()) }
            ordered.filter { it !in native }.forEachIndexed { index, view ->
                assertSame(icons[index], (view as ImageView).drawable)
            }
            // Hide before collapse starts, and keep them hidden even if native
            // preparation or session updates rebuild the buttons mid-animation.
            val nativePositions = native.map { it.left }
            actions.setVisible(false)
            assertFalse(actions.refresh())
            layout(row, actions.expandedWidth(row, 160))
            assertEquals(nativePositions, native.map { it.left })
            ordered.filter { it !in native }.forEach { assertEquals(View.INVISIBLE, it.visibility) }
            actions.restore()
            assertTrue(actions.refresh())
            layout(row, actions.expandedWidth(row, 160))
            val rebuilt = (0 until row.childCount).map(row::getChildAt).filter { it !in native }
            rebuilt.forEach { assertEquals(View.INVISIBLE, it.visibility) }
            actions.setVisible(true)
            assertEquals(customCount, rebuilt.count { it.visibility == View.VISIBLE })
            assertEquals(nativePositions, native.map { it.left })
            actions.restore()
            layout(row, 160)
            assertEquals(3, row.childCount)
            assertEquals(gap, native[1].left - native[0].right)
            assertEquals(gap, native[2].left - native[1].right)
        } finally {
            session.release()
        }
    }

    private fun layout(row: View, width: Int) {
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(44, View.MeasureSpec.EXACTLY))
        row.layout(0, 0, width, 44)
    }
}
