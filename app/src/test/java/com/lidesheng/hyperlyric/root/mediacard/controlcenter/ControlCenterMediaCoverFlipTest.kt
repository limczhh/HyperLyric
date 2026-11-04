package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE, qualifiers = "port-mdpi")
class ControlCenterMediaCoverFlipTest {
    @Test
    fun differentTracksAreRecognizedEvenWithoutAUsefulMediaId() {
        for (id in listOf(null, "", "constant-id")) {
            val first = coverFlipMediaIdentity(id, "player", "First", "Artist", "Album")
            val second = coverFlipMediaIdentity(id, "player", "Second", "Artist", "Album")
            assertNotEquals(first, second)
            assertEquals(first, coverFlipMediaIdentity(id, "player", "First", "Artist", "Album"))
            assertNotEquals(first, coverFlipMediaIdentity(id, "other-player", "First", "Artist", "Album"))
        }
    }

    @Test
    fun separatorsInsideTrackNamesCannotCollide() {
        assertNotEquals(coverFlipMediaIdentity(null, "player", "a,b", "c", null),
            coverFlipMediaIdentity(null, "player", "a", "b,c", null))
    }

    @Test
    fun blankMetadataCanUseArtworkChangesAsAnIdentity() {
        assertNotEquals(coverFlipMediaIdentity(null, "player", null, null, null, 1L),
            coverFlipMediaIdentity(null, "player", null, null, null, 2L))
    }

    @Test
    fun portraitAndLandscapeKeepTheNativePerspectiveRatio() {
        val cover = View(RuntimeEnvironment.getApplication()).apply { cameraDistance = 1000f }
        val nativeDistance = cover.cameraDistance
        val geometry = ControlCenterMediaCoverFlipGeometry(cover, 96)
        for (size in listOf(324, 240, 96)) {
            cover.layout(0, 0, size, size)
            geometry.prepare()
            assertEquals(nativeDistance / 96f, cover.cameraDistance / size, 0.001f)
        }
        geometry.restore()
        assertEquals(nativeDistance, cover.cameraDistance, 0.01f)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun artworkDrawCannotReachTheControlsBelowAndRestoresTheCanvasClip() {
        val cover = View(RuntimeEnvironment.getApplication()).apply {
            layout(20, 30, 344, 354)
            rotationY = 45f
        }
        val geometry = ControlCenterMediaCoverFlipGeometry(cover, 96)
        val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val originalClip = Rect(canvas.clipBounds)
        geometry.draw(canvas) {
            assertEquals(Rect(20, 30, 344, 354), canvas.clipBounds)
            canvas.drawColor(Color.RED)
        }
        assertEquals(Color.RED, bitmap.getPixel(180, 180))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(180, 400))
        assertEquals(originalClip, canvas.clipBounds)
    }
}
