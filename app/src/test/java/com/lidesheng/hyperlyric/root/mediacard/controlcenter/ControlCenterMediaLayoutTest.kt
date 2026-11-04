package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE, qualifiers = "port-mdpi")
class ControlCenterMediaLayoutTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var content: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var meta: ConstraintLayout
    private lateinit var coverParent: ConstraintLayout
    private lateinit var cover: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var audio: View
    private lateinit var progress: View
    private lateinit var devices: View
    private lateinit var state: ControlCenterMediaLayout
    private var contentLimit = Int.MAX_VALUE
    private val views = linkedMapOf<String, View>()

    @Before
    fun setUp() {
        content = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val height = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.AT_MOST)
                    View.MeasureSpec.makeMeasureSpec(minOf(View.MeasureSpec.getSize(heightMeasureSpec), contentLimit),
                        View.MeasureSpec.AT_MOST) else heightMeasureSpec
                super.onMeasure(widthMeasureSpec, height)
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16) }
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        meta = ConstraintLayout(context)
        header.addView(meta, LinearLayout.LayoutParams(-1, -2))
        views["meta_data_info"] = meta
        coverParent = ConstraintLayout(context)
        add("coverParent", coverParent, meta, 80, 80)
        cover = ImageView(context)
        add("cover", cover, coverParent, 80, 80)
        title = TextView(context).apply { text = "Song title"; textSize = 20f }
        artist = TextView(context).apply { text = "Artist"; textSize = 14f }
        add("title", title)
        add("subtitle", artist)
        add("title_no_play_back_history", TextView(context).apply { visibility = View.GONE })
        audio = FrameLayout(context).apply { addView(View(context), FrameLayout.LayoutParams(48, 48)) }
        add("container_audio", audio, width = 0, height = 48)
        add("container_video", FrameLayout(context).apply { visibility = View.GONE }, width = 0, height = 48)
        // Use a native-style container whose children determine its wrap-content height.
        progress = FrameLayout(context).apply { addView(View(context), FrameLayout.LayoutParams(-1, 40)) }
        add("container_progress", progress, width = 0, height = 40)
        val volume = View(context)
        header.addView(volume, LinearLayout.LayoutParams(-1, 40).apply { topMargin = 16 })
        views["volume_bar_container"] = volume
        devices = View(context)
        content.addView(devices, LinearLayout.LayoutParams(-1, 70))
        state = ControlCenterMediaLayout.create(header, views::get)
    }

    private fun add(name: String, view: View, parent: ConstraintLayout = meta, width: Int = 0, height: Int = -2) {
        view.id = View.generateViewId()
        parent.addView(view, ConstraintLayout.LayoutParams(width, height))
        views[name] = view
    }

    private fun layout(width: Int = 360) {
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
    }

    @Test
    fun squareIsFullCardWidthAndMetadataStaysInsideItsBottom() {
        state.apply()
        layout()
        assertEquals(360, coverParent.width)
        assertEquals(coverParent.width, coverParent.height)
        assertEquals(360, cover.width)
        assertEquals(360, cover.height)
        assertTrue(title.top > coverParent.height / 2)
        assertTrue(title.bottom <= artist.top)
        assertTrue(artist.bottom < coverParent.bottom)
        assertEquals((360 - title.width) / 2, title.left)
        assertTrue(audio.top >= coverParent.bottom)
        assertTrue(progress.top >= audio.bottom)
        assertTrue(devices.top >= header.bottom)
        assertSame(meta, title.parent)
        assertSame(meta, artist.parent)
    }

    @Test
    fun resizingAndRepeatedOpensDoNotDuplicateTheTransitionOrListeners() {
        var clicks = 0
        audio.setOnClickListener { clicks++ }
        repeat(3) { state.apply(); layout(320 + it * 40) }
        assertEquals(400, cover.height)
        assertEquals(2, coverParent.childCount)
        audio.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun deviceHeightUsesMeasuredHeaderAndNativeListCanGrow() {
        state.apply()
        layout()
        assertEquals((900 - header.height - 8).coerceAtLeast(0),
            state.availableDeviceHeight(900, 8, 360))
        assertEquals(0, state.availableDeviceHeight(100, 8, 360))
        val before = content.height
        devices.layoutParams = devices.layoutParams.apply { height = 210 }
        layout()
        assertEquals(before + 140, content.height)
    }

    @Test
    fun expandedCardGrowsPastBothNative500dpCapsBeforeAnimationAndStaysExpanded() {
        var frameLimit = 500
        contentLimit = 500
        val frame = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                    minOf(frameLimit, View.MeasureSpec.getSize(heightMeasureSpec)), View.MeasureSpec.AT_MOST))
            }
        }
        frame.addView(content, FrameLayout.LayoutParams(-1, -2))
        // The real SupportLand class also gives its portrait header a fixed native height.
        header.layoutParams = header.layoutParams.apply { height = 219 }
        state = ControlCenterMediaLayout.create(header, views::get)
        val sizing = ControlCenterMediaPanelSizing(state, content, 500, 500,
            { contentLimit = it }, { frameLimit = it }, 70)
        val width = View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST)
        repeat(3) {
            assertTrue(sizing.prepare(360, height, windowLimit = 800))
            devices.layoutParams = devices.layoutParams.apply { this.height = minOf(210, sizing.availableDeviceHeight()) }
            frame.measure(width, height)
            frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
            assertTrue(state.isApplied)
            assertEquals(800, frameLimit)
            assertEquals(800, contentLimit)
            assertTrue(frame.height > 500)
            assertEquals(header.height + devices.height, frame.height)
            assertEquals(360, cover.height)
            assertTrue(cover.height < frame.height)
        }
        // A later native orientation/layout reset must not shrink the custom header.
        header.layoutParams = header.layoutParams.apply { this.height = 219 }
        assertTrue(sizing.prepare(360, height, windowLimit = 800))
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, header.layoutParams.height)
    }

    @Test
    fun onlyRealWindowShortageFallsBackAndAWindowResizeCanRetry() {
        var frameLimit = 500
        var nativeLimit = 500
        val sizing = ControlCenterMediaPanelSizing(state, content, 500, 500,
            { contentLimit = it }, { frameLimit = it }, 70, nativeLimits = { nativeLimit to nativeLimit })
        assertFalse(sizing.prepare(360, windowLimit = 400))
        val original = coverParent.layoutParams
        repeat(3) {
            assertFalse(sizing.prepare(360, windowLimit = 400))
            assertFalse(state.isApplied)
            assertSame(original, coverParent.layoutParams)
            assertEquals(500, contentLimit)
            assertEquals(500, frameLimit)
        }
        assertTrue(sizing.prepare(360, windowLimit = 800))
        assertTrue(state.isApplied)
        assertEquals(800, frameLimit)
        val config = Configuration(context.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        nativeLimit = 324
        assertFalse(sizing.prepare(360, windowLimit = 800))
        assertEquals(324, contentLimit)
        assertEquals(324, frameLimit)
    }

    @Test
    fun heightBudgetUsesTargetWidthInsteadOfPreviousMeasuredCardWidth() {
        state.apply()
        layout(320)
        state.availableDeviceHeight(900, 8, 400)
        assertEquals(400, header.measuredWidth)
        assertEquals(400, coverParent.measuredHeight)
    }

    @Test
    fun landscapeRestoresNativeBoundsColorsAndPadding() {
        val original = coverParent.layoutParams
        val titleColor = title.currentTextColor
        state.apply()
        layout()
        val config = Configuration(context.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        state.apply()
        assertFalse(state.isApplied)
        assertSame(original, coverParent.layoutParams)
        assertEquals(20, header.paddingLeft)
        assertEquals(16, header.paddingTop)
        assertEquals(titleColor, title.currentTextColor)
        assertEquals(View.GONE, coverParent.getChildAt(1).visibility)
    }

    @Test
    fun missingNativeViewFailsBeforeAnyMutation() {
        val original = coverParent.layoutParams
        assertThrows(IllegalArgumentException::class.java) {
            ControlCenterMediaLayout.create(header) { if (it == "subtitle") null else views[it] }
        }
        assertSame(original, coverParent.layoutParams)
        assertEquals(1, coverParent.childCount)
    }

    @Test
    fun blurArtworkIsIndependentAndClearsWhenPlaybackArtworkDisappears() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        cover.setImageBitmap(bitmap)
        state.apply()
        val transition = coverParent.getChildAt(1) as AlbumBlurTransitionView
        val blurredImage = transition.getChildAt(0) as ImageView
        assertNotSame(cover.drawable, blurredImage.drawable)
        assertSame(bitmap, (blurredImage.drawable as BitmapDrawable).bitmap)
        // Android can change the bitmap while retaining the ImageView's Drawable instance.
        val nextBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        cover.setImageBitmap(nextBitmap)
        state.syncArtwork()
        assertSame(nextBitmap, (blurredImage.drawable as BitmapDrawable).bitmap)
        cover.setImageDrawable(null)
        state.syncArtwork()
        assertNull(blurredImage.drawable)
    }
}
