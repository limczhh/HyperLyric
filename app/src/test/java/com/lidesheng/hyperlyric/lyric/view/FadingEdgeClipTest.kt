package com.lidesheng.hyperlyric.lyric.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], manifest = Config.NONE)
class FadingEdgeClipTest {
    private val longLine = RichLyricLine(text = "█".repeat(64), words = emptyList())

    @Test
    fun doubleLineMusicInfoClipsTextToTheScaledFadeEdge() {
        val view = RichLyricLineView(RuntimeEnvironment.getApplication()).apply {
            setMetadataLine(longLine, longLine)
            setRenderScale(0.9f)
        }
        assertScaledFadingEdge(view)
    }

    @Test
    fun independentSpaceGateSlotClipsTextToTheScaledFadeEdge() {
        val view = SpaceGateRichLyricLineView(RuntimeEnvironment.getApplication()).apply {
            main.spaceGateEnabled = false
            secondary.spaceGateEnabled = false
            setMetadataLine(longLine, longLine)
            setRenderScale(0.9f)
        }
        assertScaledFadingEdge(view)
    }

    private fun assertScaledFadingEdge(view: LinearLayout) {
        val width = 278
        val height = 140
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        val lines = listOf(view.getChildAt(0), view.getChildAt(1))
        lines.forEach {
            // 308 logical pixels at 90% cover 277.2 px inside a 278 px viewport.
            assertEquals(308, it.width)
            it.setFadingEdgeLength(15)
            it.isHorizontalFadingEdgeEnabled = true
        }
        val faded = draw(view)
        assertTrue(maxAlpha(faded, width - 20) > 0)
        assertEquals(0, maxAlpha(faded, width - 1))

        lines.forEach { it.isHorizontalFadingEdgeEnabled = false }
        assertTrue(maxAlpha(draw(view), width - 1) > 0)
    }

    private fun draw(view: View): Bitmap =
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }

    private fun maxAlpha(bitmap: Bitmap, x: Int): Int =
        (0 until bitmap.height).maxOf { Color.alpha(bitmap.getPixel(x, it)) }
}
