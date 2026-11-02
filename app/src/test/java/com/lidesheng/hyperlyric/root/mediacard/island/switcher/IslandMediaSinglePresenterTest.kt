package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.os.Bundle
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class IslandMediaSinglePresenterTest {
    class Data {
        var key: String? = null
        var priority: Int? = 1
        var properties: Int? = 0
        var tickerData: String? = null
        var view: View? = null
        var fakeView: View? = null
        var extras = Bundle()
    }

    class Content(var currentIslandData: Data?, val fakeView: Content? = null) {
        var reparentCount = 0
        var miniBarVisible = true
        val dynamicIslandEventCoordinator = Events()
        fun updateExpandedView(data: Data?, reuse: Boolean, continuation: Any?) { reparentCount++ }
    }

    class Events {
        var capabilityUpdates = 0
        fun updateMiniBar(real: Content) {
            capabilityUpdates++
            val supported = real.currentIslandData?.extras?.getString("miui.pkg.name") == "A"
            real.miniBarVisible = supported
            real.fakeView?.miniBarVisible = supported
        }
        fun addDynamicIslandView(data: Data, expanded: Boolean) { error("Must not create an island") }
        fun updateDynamicIslandView(data: Data, expanded: Boolean) { error("Must not update the island state") }
        fun removeDynamicIslandView(key: String) { error("Must not remove an island") }
    }

    private fun data(key: String): Data = Data().apply {
        this.key = key
        tickerData = "compact:$key"
        view = View(RuntimeEnvironment.getApplication())
        fakeView = View(RuntimeEnvironment.getApplication())
        extras.putString("miui.pkg.name", key)
        extras.putString("miui.key", key)
        extras.putString("miui.pending.intent", "launch:$key")
    }
    private fun content(data: Data) = Content(data, Content(data))

    @Test fun browsingPreservesNativeIdentityAndCompactContentWhileSwitchingBothExpandedViews() {
        val native = data("A")
        val preview = data("B")
        val real = content(native)
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview)
        val shown = real.currentIslandData!!
        assertNotSame(native, shown)
        assertNotSame(preview, shown)
        assertEquals("A", shown.key)
        assertEquals("compact:A", shown.tickerData)
        assertEquals("A", shown.extras.getString("miui.key"))
        assertEquals("B", shown.extras.getString("miui.pkg.name"))
        assertSame(preview.view, shown.view)
        assertSame(preview.fakeView, shown.fakeView)
        assertSame(shown, real.fakeView!!.currentIslandData)
        assertEquals("A", native.extras.getString("miui.pkg.name"))
        assertEquals("B", preview.extras.getString("miui.key"))
        assertFalse(real.miniBarVisible)
        assertFalse(real.fakeView.miniBarVisible)
        presenter.restoreAll()
        assertSame(native, real.currentIslandData)
        assertSame(native, real.fakeView.currentIslandData)
        assertTrue(real.miniBarVisible)
        assertTrue(real.fakeView.miniBarVisible)
    }

    @Test fun continuousUpdatesDuringSimultaneousPlaybackDoNotReparentCardsOrRepublishIslands() {
        val native = data("A").apply { extras.putBoolean("playing", true) }
        val first = data("B").apply { extras.putBoolean("playing", true) }
        val real = content(native)
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, first)
        repeat(100) { index ->
            val update = data("B").apply {
                view = first.view
                fakeView = first.fakeView
                extras.putBoolean("playing", true)
                extras.putString("song", "song:$index")
            }
            presenter.render(real, update)
        }
        assertEquals(1, real.reparentCount)
        assertEquals(1, real.fakeView!!.reparentCount)
        assertEquals(1, real.dynamicIslandEventCoordinator.capabilityUpdates)
        assertEquals("A", native.key)
        assertEquals("compact:A", native.tickerData)
        presenter.restoreAll()
        assertSame(native, real.currentIslandData)
        assertEquals(2, real.reparentCount)
    }

    @Test fun nativeUpdatesWhileBrowsingArePreservedOnCollapse() {
        val original = data("A")
        val real = content(original)
        val preview = data("B")
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview)
        val latestNative = data("A").apply { tickerData = "new song" }
        real.currentIslandData = latestNative
        real.fakeView!!.currentIslandData = latestNative
        presenter.render(real, preview)
        assertEquals("new song", real.currentIslandData!!.tickerData)
        presenter.restoreAll()
        assertSame(latestNative, real.currentIslandData)
        assertSame(latestNative, real.fakeView.currentIslandData)
    }

    @Test fun nativeRecreationIsNeverOverwrittenByRestoringAnOldBrowsingCard() {
        val real = content(data("A"))
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, data("B"))
        val recreated = data("C")
        real.currentIslandData = recreated
        real.fakeView!!.currentIslandData = recreated
        presenter.restoreAll()
        assertSame(recreated, real.currentIslandData)
        assertSame(recreated, real.fakeView.currentIslandData)
        assertFalse(presenter.owns(real))
    }

    @Test fun aChangedLaunchIntentRefreshesCapabilityWithoutReparentingTheSameViews() {
        val real = content(data("A"))
        val preview = data("B")
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview)
        val next = data("B").apply {
            view = preview.view
            fakeView = preview.fakeView
            extras.putString("miui.pending.intent", "new activity")
        }
        presenter.render(real, next)
        assertEquals(2, real.dynamicIslandEventCoordinator.capabilityUpdates)
        assertEquals(1, real.reparentCount)
        assertEquals(1, real.fakeView!!.reparentCount)
    }
}
