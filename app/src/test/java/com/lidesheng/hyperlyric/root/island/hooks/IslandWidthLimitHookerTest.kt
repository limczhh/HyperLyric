package com.lidesheng.hyperlyric.root.island.hooks

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import com.lidesheng.hyperlyric.root.island.presentation.IslandPresentationCoordinator
import com.lidesheng.hyperlyric.root.island.presentation.IslandRenderPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
    fun firstRegistrationRecalculatesWidthForNewlyEligibleHost() {
        val root = newIsland(registerHost = false)
        assertFalse(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))
        assertEquals(0, root.widthRefreshCount)

        IslandViewRegistry.registerReal(root, LYRIC_PACKAGE)
        IslandViewRegistry.refreshInjectedViewsFor(root)

        assertTrue(
            IslandWidthLimitHooker.refreshIfNewlyEligible(
                contentView = root,
                wasEligible = false,
                enabled = true
            )
        )
        assertEquals(1, root.widthRefreshCount)
    }

    @Test
    fun alreadyEligibleHostDoesNotRecalculateWidthAgain() {
        val root = newIsland()
        assertTrue(IslandWidthLimitHooker.isInjectedLyricWidthTarget(root))

        assertFalse(
            IslandWidthLimitHooker.refreshIfNewlyEligible(
                contentView = root,
                wasEligible = true,
                enabled = true
            )
        )
        assertEquals(0, root.widthRefreshCount)
    }

    @Test
    fun disabledWidthLimitDoesNotRecalculateNewlyEligibleHost() {
        val root = newIsland(registerHost = false)
        IslandViewRegistry.registerReal(root, LYRIC_PACKAGE)
        IslandViewRegistry.refreshInjectedViewsFor(root)

        assertFalse(
            IslandWidthLimitHooker.refreshIfNewlyEligible(
                contentView = root,
                wasEligible = false,
                enabled = false
            )
        )
        assertEquals(0, root.widthRefreshCount)
    }

    @Test
    fun onRealSystemUpdateCompleteRecalculatesWidthForFirstEligibleBuild() {
        val root = newIsland(registerHost = false)
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("width-limit-first-build", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(RootConstants.KEY_HOOK_ENABLE, true)
            .putBoolean(RootConstants.KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT, true)
            .commit()
        installTestHookEntry(prefs)
        try {
            IslandPresentationCoordinator.updatePlaybackState(true)

            val result = IslandPresentationCoordinator.onRealSystemUpdateComplete(
                root = root,
                owner = IslandRenderPolicy.OwnerEvidence.Media(LYRIC_PACKAGE)
            )

            assertTrue(result.isTarget)
            assertFalse(result.mutation.layoutMayHaveChanged)
            assertEquals(1, root.widthRefreshCount)
        } finally {
            installTestHookEntry(null)
            prefs.edit().clear().commit()
        }
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

    /**
     * Installs a HookEntry whose prefs are backed by a Robolectric SharedPreferences, without
     * requiring the module runtime. Passing null removes the test instance again.
     */
    private fun installTestHookEntry(prefs: android.content.SharedPreferences?) {
        val hookEntryClass = Class.forName("com.lidesheng.hyperlyric.root.HookEntry")
        val instanceField = hookEntryClass.getDeclaredField("instance")
            .apply { isAccessible = true }
        if (prefs == null) {
            instanceField.set(null, null)
            return
        }
        val entry = hookEntryClass.getDeclaredConstructor().newInstance()
        hookEntryClass.getDeclaredField("_prefs")
            .apply { isAccessible = true }
            .set(entry, prefs)
        instanceField.set(null, entry)
    }

    class TestIslandContentView(context: Context) : FrameLayout(context) {
        var data: Any? = null

        var widthRefreshCount = 0
            private set

        fun getCurrentIslandData(): Any? = data

        fun updateBigIslandViewWidth() {
            widthRefreshCount++
        }
    }

    class TestIslandData(private val extras: Bundle) {
        fun getExtras(): Bundle = extras
    }

    private companion object {
        const val LYRIC_PACKAGE = "com.example.music"
        const val OTHER_PACKAGE = "com.example.other"
    }
}
