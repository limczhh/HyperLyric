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

    class NativeContent(val bar: View, val canSlide: Boolean, val fake: NativeContent? = null) {
        fun getMiniBar(): View = bar
        fun getMiniBarVisible(): Boolean = canSlide
        fun getFakeView(): NativeContent? = fake
        fun updateMiniBarTranslationForTest(real: NativeContent) { bar.translationY = 100f }
    }

    @Test fun anInformationalBarDoesNotGrantNativeMiniWindowCapability() {
        val context = RuntimeEnvironment.getApplication()
        val window = View(context)
        val bar = View(context).apply {
            background = nativeBar()
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(120, 8)
        }
        val original = bar.background
        val params = bar.layoutParams
        val real = NativeContent(bar, canSlide = false)
        IslandMediaMiniBarIndicator.update(window, real, intArrayOf(2, 0))
        assertEquals(View.VISIBLE, bar.visibility)
        assertFalse(real.getMiniBarVisible())
        assertFalse(bar.isClickable)
        assertFalse(bar.isFocusable)
        assertSame(params, bar.layoutParams)
        IslandMediaMiniBarIndicator.clear()
        assertSame(original, bar.background)
        assertEquals(View.GONE, bar.visibility)
    }

    @Test fun replacingTheNativeBackgroundPreservesTheInformationalVisibilityCleanup() {
        val context = RuntimeEnvironment.getApplication()
        val window = View(context)
        val bar = View(context).apply { background = nativeBar(); visibility = View.GONE }
        val content = NativeContent(bar, canSlide = false)
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(2, 0))
        val newBackground = nativeBar()
        bar.background = newBackground
        IslandMediaMiniBarIndicator.update(window, content, intArrayOf(2, 1))
        IslandMediaMiniBarIndicator.clear()
        assertSame(newBackground, bar.background)
        assertEquals(View.GONE, bar.visibility)
        assertFalse(content.getMiniBarVisible())
    }

    @Test fun realAndTransitionBarsShareThePageWhileRetainingNativeAnimationProperties() {
        val context = RuntimeEnvironment.getApplication()
        fun view() = View(context).apply { background = nativeBar(); alpha = 0.4f; scaleX = 0.8f }
        val real = NativeContent(view(), canSlide = true, fake = NativeContent(view(), canSlide = true))
        IslandMediaMiniBarIndicator.update(View(context), real, intArrayOf(3, 1))
        for (bar in listOf(real.bar, real.fake!!.bar)) {
            assertTrue(bar.background is SegmentedMiniBarDrawable)
            assertEquals(0.4f, bar.alpha, 0f)
            assertEquals(0.8f, bar.scaleX, 0f)
        }
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
        assertTrue(Color.alpha(selected) > 0x33)
        assertEquals(255, Color.red(selected))
        assertEquals(255, Color.green(selected))
        assertEquals(255, Color.blue(selected))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(39, 4))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(80, 4))
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
