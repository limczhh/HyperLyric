package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.view.View
import android.widget.FrameLayout
import org.robolectric.RuntimeEnvironment
import org.junit.After
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedMiniBarDrawableTest {
    @After fun cleanup() = IslandMediaMiniBarIndicator.clear()

    class NativeContent(val bar: View, var canSlide: Boolean, val fake: NativeContent? = null) {
        fun getMiniBar(): View = bar
        fun getMiniBarVisible(): Boolean = canSlide
        fun getFakeView(): NativeContent? = fake
        fun getExpandedViewHeight(): Int = 200
        fun getMiniBarMarginBottom(): Float = 10f
        fun getCurrentIslandData(): Any? = null
    }

    private fun hostBar(visibility: Int = View.VISIBLE): View {
        val context = RuntimeEnvironment.getApplication()
        return View(context).apply {
            background = nativeBar()
            this.visibility = visibility
            FrameLayout(context).addView(this, FrameLayout.LayoutParams(120, 8).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            })
            (parent as View).apply {
                measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
                layout(0, 0, 300, 300)
            }
        }
    }

    @Test fun anInformationalOverlayDoesNotChangeNativeVisibilityOrMiniWindowCapability() {
        val window = View(RuntimeEnvironment.getApplication())
        val bar = hostBar(View.GONE)
        val original = bar.background
        val params = bar.layoutParams
        val real = NativeContent(bar, canSlide = false)
        IslandMediaMiniBarIndicator.update(window, real, intArrayOf(2, 0))
        assertEquals(View.GONE, bar.visibility)
        assertFalse(real.getMiniBarVisible())
        assertFalse(bar.isClickable)
        assertFalse(bar.isFocusable)
        assertSame(params, bar.layoutParams)
        assertEquals(1, (bar.parent as FrameLayout).childCount)
        assertEquals(Color.TRANSPARENT, (bar.background as android.graphics.drawable.ColorDrawable).color)
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        (bar.parent as View).draw(Canvas(bitmap))
        assertEquals(Color.WHITE, bitmap.getPixel(110, 186))
        assertEquals(0x33ffffff, bitmap.getPixel(190, 186))
        IslandMediaMiniBarIndicator.clear()
        assertSame(original, bar.background)
        assertEquals(View.GONE, bar.visibility)
    }

    @Test fun nativeCapabilityChangesRemainIndependentFromTheIndicatorAcrossSwitches() {
        val window = View(RuntimeEnvironment.getApplication())
        val bar = hostBar()
        val content = NativeContent(bar, canSlide = true)
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(3, 0))
        content.canSlide = false
        bar.visibility = View.GONE
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(3, 1))
        assertFalse(content.canSlide)
        assertEquals(View.GONE, bar.visibility)
        content.canSlide = true
        bar.visibility = View.VISIBLE
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(3, 0))
        assertTrue(content.canSlide)
        assertEquals(View.VISIBLE, bar.visibility)
    }

    @Test fun replacingTheNativeBackgroundRestoresTheNewBackgroundWithoutChangingVisibility() {
        val window = View(RuntimeEnvironment.getApplication())
        val bar = hostBar(View.GONE)
        val content = NativeContent(bar, canSlide = false)
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(2, 0))
        val newBackground = nativeBar()
        bar.background = newBackground
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(2, 1))
        IslandMediaMiniBarIndicator.clear()
        assertSame(newBackground, bar.background)
        assertEquals(View.GONE, bar.visibility)
    }

    @Test fun realAndTransitionBarsRetainNativeAnimationAndInputProperties() {
        val real = NativeContent(hostBar().apply { alpha = 0.4f; scaleX = 0.8f }, canSlide = true,
            fake = NativeContent(hostBar(View.GONE), canSlide = false))
        IslandMediaMiniBarIndicator.update(View(RuntimeEnvironment.getApplication()), real, intArrayOf(3, 1))
        assertEquals(0.4f, real.bar.alpha, 0f)
        assertEquals(0.8f, real.bar.scaleX, 0f)
        assertEquals(View.VISIBLE, real.bar.visibility)
        assertEquals(View.GONE, real.fake!!.bar.visibility)
        assertTrue(real.canSlide)
        assertFalse(real.fake.canSlide)
    }

    private fun nativeBar() = GradientDrawable().apply {
        setColor(0x33ffffff)
        cornerRadius = 4f
        setBounds(0, 0, 120, 8)
    }
    private fun render(count: Int, selected: Int): Bitmap {
        val bar = SegmentedMiniBarDrawable(nativeBar()).apply {
            setBounds(0, 0, 120, 8)
            update(count, selected)
        }
        return Bitmap.createBitmap(120, 8, Bitmap.Config.ARGB_8888).also { bar.draw(Canvas(it)) }
    }

    @Test fun unselectedSegmentsKeepTheNativeColorWhileSelectedIsBrighter() {
        val bitmap = render(3, 1)
        assertEquals(0x33ffffff, bitmap.getPixel(15, 4))
        assertEquals(0x33ffffff, bitmap.getPixel(104, 4))
        val selected = bitmap.getPixel(60, 4)
        assertEquals(255, Color.alpha(selected))
        assertEquals(255, Color.red(selected))
        assertEquals(255, Color.green(selected))
        assertEquals(255, Color.blue(selected))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(39, 4))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(80, 4))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(35, 4))
    }

    @Test fun selectedSegmentUsesTheMediaActionColor() {
        val selectedColor = Color.rgb(120, 210, 90)
        val drawable = SegmentedMiniBarDrawable(nativeBar()).apply {
            setBounds(0, 0, 120, 8)
            update(3, 1, selectedColor)
        }
        val bitmap = Bitmap.createBitmap(120, 8, Bitmap.Config.ARGB_8888)
        drawable.draw(Canvas(bitmap))
        assertEquals(selectedColor, bitmap.getPixel(60, 4))
        assertEquals(0x33ffffff, bitmap.getPixel(15, 4))
    }

    @Test fun oneSessionKeepsTheNativeSingleLine() {
        val actual = render(1, 0)
        val expected = Bitmap.createBitmap(120, 8, Bitmap.Config.ARGB_8888)
        nativeBar().draw(Canvas(expected))
        assertTrue(expected.sameAs(actual))
    }

    @Test fun changingTheSelectionDoesNotMoveTheSegmentsOrGaps() {
        val first = render(6, 0)
        val last = render(6, 5)
        for (x in 0 until 120) {
            assertEquals(Color.alpha(first.getPixel(x, 4)) == 0, Color.alpha(last.getPixel(x, 4)) == 0)
        }
        assertTrue(Color.alpha(first.getPixel(8, 4)) > Color.alpha(last.getPixel(8, 4)))
        assertTrue(Color.alpha(last.getPixel(112, 4)) > Color.alpha(first.getPixel(112, 4)))
    }
}
