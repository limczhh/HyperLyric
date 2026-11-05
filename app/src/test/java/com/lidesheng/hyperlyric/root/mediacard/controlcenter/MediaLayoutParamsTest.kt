package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class MediaLayoutParamsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun staleLinearParamsInsideNestedFrameAreRepairedBeforeMeasure() {
        val header = LinearLayout(context)
        val frame = FrameLayout(context)
        val child = View(context)
        header.addView(frame, LinearLayout.LayoutParams(200, 100))
        frame.addView(child, FrameLayout.LayoutParams(48, 40))
        // setLayoutParams accepts this, but FrameLayout.onMeasure then crashes.
        child.layoutParams = LinearLayout.LayoutParams(48, 40).apply {
            setMargins(3, 5, 7, 9)
            marginStart = 11
            marginEnd = 13
        }
        MediaLayoutParams.validateTree(header)
        val repaired = child.layoutParams as FrameLayout.LayoutParams
        assertEquals(48, repaired.width)
        assertEquals(40, repaired.height)
        assertEquals(5, repaired.topMargin)
        assertEquals(9, repaired.bottomMargin)
        assertEquals(11, repaired.marginStart)
        assertEquals(13, repaired.marginEnd)
        header.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        assertEquals(40, child.measuredHeight)
        MediaLayoutParams.validateTree(header)
        assertSame(repaired, child.layoutParams)
    }

    @Test
    fun restoreConvertsSnapshotForCurrentParentWithoutMutatingSnapshot() {
        val frame = FrameLayout(context)
        val child = View(context)
        frame.addView(child)
        val snapshot = LinearLayout.LayoutParams(48, 40).apply { topMargin = 12 }
        MediaLayoutParams.assign(child, snapshot)
        assertTrue(child.layoutParams is FrameLayout.LayoutParams)
        assertNotSame(snapshot, child.layoutParams)
        assertEquals(12, (child.layoutParams as FrameLayout.LayoutParams).topMargin)
        assertEquals(48, snapshot.width)
    }

    @Test
    fun nativeConstraintParametersAndGoneChildrenKeepTheirGeometry() {
        val parent = ConstraintLayout(context)
        val child = View(context).apply { visibility = View.GONE }
        val params = ConstraintLayout.LayoutParams(80, 80).apply {
            startToStart = 0
            endToEnd = 0
            dimensionRatio = "1:1"
        }
        parent.addView(child, params)
        MediaLayoutParams.validateTree(parent)
        assertSame(params, child.layoutParams)
        assertEquals("1:1", params.dimensionRatio)
        assertEquals(0, params.endToEnd)
    }
}
