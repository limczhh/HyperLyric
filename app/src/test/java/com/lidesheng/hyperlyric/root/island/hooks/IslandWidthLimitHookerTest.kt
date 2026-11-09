package com.lidesheng.hyperlyric.root.island.hooks

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class IslandWidthLimitHookerTest {
    private lateinit var activity: Activity
    private val roots = mutableListOf<ViewGroup>()

    @Before
    fun setUp() {
        LyriconDataBridge.clearState()
        LyriconDataBridge.updateLyricPackage(LYRIC_PACKAGE)
        LyriconDataBridge.updateLyric("Test lyric")
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        roots.forEach(IslandViewRegistry::unregister)
        roots.clear()
        LyriconDataBridge.clearState()
    }

    @Test
    fun injectedLyricIslandBecomesEligibleAfterInjection() {
        val root = newIsland(injectLyric = false)

        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))

        root.addView(RichLyricLineView(activity))
        IslandViewRegistry.refreshInjectedViewsFor(root)

        assertTrue(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))
    }

    @Test
    fun otherApplicationKeepsNativeWidthEvenWithInjectedViewEvidence() {
        val root = newIsland(islandPackage = OTHER_PACKAGE)

        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))
    }

    @Test
    fun nonMediaNotificationKeepsNativeWidth() {
        val root = newIsland(hasMediaPendingIntent = false)

        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))
    }

    @Test
    fun missingHostOrIslandDataKeepsNativeWidth() {
        val withoutHost = newIsland(registerHost = false)
        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(withoutHost))

        val withoutData = newIsland(hasIslandData = false)
        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(withoutData))
        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(null))
    }

    @Test
    fun mismatchedHostPackageKeepsNativeWidth() {
        val root = newIsland(hostPackage = OTHER_PACKAGE)

        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))
    }

    @Test
    fun missingLyricOwnerKeepsNativeWidth() {
        LyriconDataBridge.updateLyricPackage(null)
        val root = newIsland()

        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))
    }

    private fun newIsland(
        islandPackage: String = LYRIC_PACKAGE,
        hostPackage: String = islandPackage,
        hasMediaPendingIntent: Boolean = true,
        registerHost: Boolean = true,
        injectLyric: Boolean = true,
        hasIslandData: Boolean = true,
    ): TestIslandContentView {
        val root = TestIslandContentView(activity)
        roots += root
        if (hasIslandData) {
            root.data = TestIslandData(Bundle().apply {
                putString("miui.pkg.name", islandPackage)
                if (hasMediaPendingIntent) {
                    putParcelable("miui.pending.intent", mediaPendingIntent())
                }
            })
        }
        activity.setContentView(root)
        if (registerHost) {
            IslandViewRegistry.registerReal(root, hostPackage)
        }
        if (injectLyric) {
            root.addView(RichLyricLineView(activity))
        }
        IslandViewRegistry.refreshInjectedViewsFor(root)
        return root
    }

    private fun mediaPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            activity,
            0,
            Intent("com.lidesheng.hyperlyric.test.MEDIA"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    class TestIslandContentView(context: Context) : FrameLayout(context) {
        var data: Any? = null

        fun getCurrentIslandData(): Any? = data
    }

    class TestIslandData(private val extras: Bundle) {
        fun getExtras(): Bundle = extras
    }

    private companion object {
        const val LYRIC_PACKAGE = "com.example.music"
        const val OTHER_PACKAGE = "com.example.other"
    }
}
