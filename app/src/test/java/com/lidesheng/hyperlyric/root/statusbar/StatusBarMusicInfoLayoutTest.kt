package com.lidesheng.hyperlyric.root.statusbar

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.view.MaxWidthFrameLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33, 36], manifest = Config.NONE)
class StatusBarMusicInfoLayoutTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var parent: LinearLayout
    private lateinit var host: StatusBarLyricHost

    @Before
    fun setup() {
        LyriconDataBridge.clearState()
        activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = activity.get()
        prefs = context.getSharedPreferences("statusbar-metadata", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putInt(RootConstants.KEY_HOOK_NO_LYRICS_LYRIC_BEHAVIOR, RootConstants.NO_LYRICS_LYRIC_BEHAVIOR_MUSIC_INFO)
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE, "title")
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE, "artist")
            .putBoolean(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND, true)
            .putBoolean(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED, false)
            .putInt(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH, 200)
            .putBoolean(RootConstants.KEY_HOOK_ANIM_ENABLE, false)
            .commit()
        LyriconDataBridge.updateMediaMetadata(LyricMediaMetadata(
            sourceId = "test", title = "A long music title for checking the island boundary",
            artist = "Artist", duration = 100_000L,
        ))
        root = FrameLayout(context).apply { id = View.generateViewId() }
        parent = LinearLayout(context)
        root.addView(parent, FrameLayout.LayoutParams(600, 40))
        val clock = TextView(context).apply { id = View.generateViewId(); text = "12:00" }
        parent.addView(clock, LinearLayout.LayoutParams(40, 40))
        context.setContentView(root)
        host = StatusBarLyricHost(root, clock, parent, root.id, clock.id)
        layout()
        // The real renderer publishes visibility after its first host layout.
        StatusBarLyricIslandRegionDispatcher.updateAdjustmentEnabled(true)
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(true)
    }

    @After
    fun cleanup() {
        host.releaseForHotReload()
        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(null)
        StatusBarLyricIslandRegionDispatcher.updateLyricsVisible(false)
        StatusBarLyricIslandRegionDispatcher.updateAdjustmentEnabled(false)
        LyriconDataBridge.clearState()
        activity.pause().stop().destroy()
    }

    @Test
    fun replacementMusicInfoAvoidsIslandAndExpandsWhenIslandDisappears() {
        checkIslandAvoidance(RootConstants.ISLAND_CONTENT_MODE_LYRIC)
    }

    @Test
    fun explicitMusicInfoAndProgressUpdatesUseTheSameIslandBoundary() {
        checkIslandAvoidance(RootConstants.ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO)
        prefs.edit()
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE, "elapsed")
            .putString(RootConstants.KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE, "progress_percent")
            .commit()
        host.render(prefs, force = true, suppressAnimation = true)
        layout()
        val wrapper = wrapper()
        val bounds = islandAt(wrapper, 30)
        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(bounds)
        host.updatePlaybackPosition(100_000L)
        host.refreshIslandWidth()
        layout()
        assertTrue(wrapper.width > 0)
        assertTrue(rightInWindow(wrapper) <= bounds.left)
    }

    private fun checkIslandAvoidance(mode: Int) {
        prefs.edit().putInt(RootConstants.KEY_HOOK_STATUS_BAR_LYRIC_CONTENT_MODE, mode).commit()
        host.render(prefs, force = true, suppressAnimation = true)
        layout()
        assertTrue(host.isShowingLyric())
        val wrapper = wrapper()
        val originalWidth = wrapper.width
        assertTrue("metadata width=$originalWidth, max=${wrapper.maxWidthPx}, desired=${wrapper.desiredWidthPx}", originalWidth > 70)
        val bounds = islandAt(wrapper, 70)
        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(bounds)
        host.refreshIslandWidth()
        layout()
        assertTrue("width=${wrapper.width}/$originalWidth height=${wrapper.height} max=${wrapper.maxWidthPx} desired=${wrapper.desiredWidthPx} laid=${wrapper.isLaidOut} bounds=${StatusBarLyricIslandRegionDispatcher.visibleIslandBounds()}", wrapper.width in 1 until originalWidth)
        assertTrue(rightInWindow(wrapper) <= bounds.left)

        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(islandAt(wrapper, 25))
        host.refreshIslandWidth()
        layout()
        assertTrue(wrapper.width in 1..25)

        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(null)
        host.refreshIslandWidth()
        layout()
        assertEquals(originalWidth, wrapper.width)
    }

    private fun wrapper() = (0 until parent.childCount)
        .map { parent.getChildAt(it) }.filterIsInstance<MaxWidthFrameLayout>().single()

    private fun islandAt(wrapper: View, available: Int): Rect {
        val location = IntArray(2)
        wrapper.getLocationInWindow(location)
        return Rect(location[0] + available, location[1], location[0] + available + 100,
            location[1] + wrapper.height)
    }

    private fun rightInWindow(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[0] + view.width
    }

    private fun layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 600, 40)
    }
}
