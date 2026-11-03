package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.function.Supplier

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

    class Expanded
    class BigIsland
    class MiniWindowExpanded
    class Signal(var value: Boolean = false)
    class Content(var currentIslandData: Data?, val fakeView: Content? = null) :
        FrameLayout(RuntimeEnvironment.getApplication()) {
        var state: Any = Expanded()
        var animationRunning = false
        val islandAppAnimRunning = Signal()
        val islandFreeformAnimRunning = Signal()
        val trackingToOpenMW = Signal()
        var needResetState = false
        fun isAnimating() = animationRunning
        var reparentCount = 0
        var miniBarVisible = true
        val dynamicIslandEventCoordinator = Events()
        val expandedView = FrameLayout(RuntimeEnvironment.getApplication())
        val fakeExpandedView = FrameLayout(RuntimeEnvironment.getApplication())
        init {
            addView(expandedView)
            addView(fakeExpandedView)
            fakeView?.let(::addView)
        }
        fun updateExpandedView(data: Data?, reuse: Boolean, continuation: Any?) {
            reparentCount++
            val parent = if (fakeView == null) fakeExpandedView else expandedView
            val view = if (fakeView == null) data?.fakeView else data?.view
            view?.let { (it.parent as? ViewGroup)?.removeView(it) }
            parent.removeAllViews()
            view?.let(parent::addView)
        }
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
    private fun Data.expanded() = IslandMediaExpandedContent(view!!, fakeView!!, extras)

    @Test fun browsingPreservesNativeIdentityAndCompactContentWhileSwitchingBothExpandedViews() {
        val native = data("A")
        val preview = data("B")
        val real = content(native)
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview.expanded())
        val shown = real.currentIslandData!!
        assertSame(native, shown)
        assertNotSame(preview, shown)
        assertEquals("A", shown.key)
        assertEquals("compact:A", shown.tickerData)
        assertEquals("A", shown.extras.getString("miui.key"))
        assertEquals("A", shown.extras.getString("miui.pkg.name"))
        assertSame(native.view, shown.view)
        assertSame(native.fakeView, shown.fakeView)
        assertSame(preview.view, real.expandedView.getChildAt(0))
        assertSame(preview.fakeView, real.fakeView!!.fakeExpandedView.getChildAt(0))
        assertSame(shown, real.fakeView.currentIslandData)
        assertEquals("A", native.extras.getString("miui.pkg.name"))
        assertEquals("B", preview.extras.getString("miui.key"))
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
        val browsing = first.expanded()
        presenter.render(real, browsing)
        repeat(3) { index ->
            first.view!!.tag = "song:$index"
            presenter.render(real, browsing)
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
        presenter.render(real, preview.expanded())
        val latestNative = data("A").apply { tickerData = "new song" }
        real.currentIslandData = latestNative
        real.fakeView!!.currentIslandData = latestNative
        presenter.render(real, preview.expanded())
        assertEquals("new song", real.currentIslandData!!.tickerData)
        presenter.restoreAll()
        assertSame(latestNative, real.currentIslandData)
        assertSame(latestNative, real.fakeView.currentIslandData)
    }

    @Test fun aNativePayloadUpdateDoesNotReinsertTheUnchangedFakeCard() {
        val original = data("A")
        val real = content(original)
        val preview = data("B")
        val browsing = preview.expanded()
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, browsing)
        // Native media updates can retain children and update the real/fake payloads separately.
        val latestNative = data("A").apply {
            view = original.view
            fakeView = original.fakeView
            tickerData = "new song"
        }
        real.currentIslandData = latestNative
        assertSame(browsing, presenter.displayedContent(real))
        presenter.render(real, browsing)
        assertEquals(1, real.reparentCount)
        assertEquals(1, real.fakeView!!.reparentCount)
        assertSame(preview.view, real.expandedView.getChildAt(0))
        assertSame(preview.fakeView, real.fakeView.fakeExpandedView.getChildAt(0))
        presenter.restoreAll()
        assertSame(latestNative, real.currentIslandData)
        assertSame(original, real.fakeView.currentIslandData)
    }

    @Test fun nativeRecreationIsNeverOverwrittenByRestoringAnOldBrowsingCard() {
        val real = content(data("A"))
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, data("B").expanded())
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
        presenter.render(real, preview.expanded())
        val next = data("B").apply {
            view = preview.view
            fakeView = preview.fakeView
            extras.putString("miui.pending.intent", "new activity")
        }
        presenter.render(real, next.expanded())
        assertEquals(2, real.dynamicIslandEventCoordinator.capabilityUpdates)
        assertEquals(1, real.reparentCount)
        assertEquals(1, real.fakeView!!.reparentCount)
    }

    @Test fun collapseRestoresIdentityBeforeAnimationButReleasesViewsOnlyAfterItEnds() {
        val native = data("A")
        val real = content(native)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(real)
        var releases = 0
        val selected = data("B")
        val preview = IslandMediaExpandedContent(selected.view!!, selected.fakeView!!, selected.extras) { releases++ }
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview)
        real.state = BigIsland()
        // onStateChange runs before the animation starts.
        presenter.release(preview)
        assertSame(native, real.currentIslandData)
        assertSame(selected.view, real.expandedView.getChildAt(0))
        assertEquals(0, releases)
        real.animationRunning = true
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(selected.view, real.expandedView.getChildAt(0))
        assertEquals(0, releases)
        real.animationRunning = false
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(native.view, real.expandedView.getChildAt(0))
        assertEquals(1, releases)
        assertFalse(presenter.owns(real))
    }

    @Test fun aNewExpansionCancelsTheOldBrowsingContentWithoutChangingViewsDuringItsAnimation() {
        val native = data("A")
        val real = content(native)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(real)
        var releases = 0
        val selected = data("B")
        val preview = IslandMediaExpandedContent(selected.view!!, selected.fakeView!!, selected.extras) { releases++ }
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview)
        real.state = BigIsland()
        presenter.release(preview)
        real.animationRunning = true
        real.state = Expanded()
        presenter.render(real, null)
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(selected.view, real.expandedView.getChildAt(0))
        assertSame(native, real.currentIslandData)
        real.animationRunning = false
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(native.view, real.expandedView.getChildAt(0))
        assertSame(native, real.currentIslandData)
        assertEquals(1, releases)
    }

    @Test fun nativeMinibarDragAndReboundKeepTheCurrentCardUntilBothFinish() {
        val real = content(data("A"))
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(real)
        val presenter = IslandMediaSinglePresenter()
        val current = data("A").expanded()
        val next = data("B").expanded()
        presenter.render(real, current)
        val beforeDrag = real.currentIslandData
        real.fakeView!!.trackingToOpenMW.value = true
        assertFalse(real.isAnimating())
        assertTrue(presenter.isBusy(real))
        presenter.render(real, next)
        var resumed = 0
        presenter.whenIdle(real) { resumed++ }
        real.fakeView.trackingToOpenMW.value = false
        real.fakeView.needResetState = true
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(beforeDrag, real.currentIslandData)
        assertSame(current.view, real.expandedView.getChildAt(0))
        assertEquals(0, resumed)
        real.fakeView.needResetState = false
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(next.view, real.expandedView.getChildAt(0))
        assertEquals(1, resumed)
    }

    @Test fun nativeOwnerFreeformKeepsItsViewsUntilCompletion() {
        val native = data("A")
        val real = content(native)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(real)
        var released = 0
        val selected = data("A")
        val preview = IslandMediaExpandedContent(selected.view!!, selected.fakeView!!, selected.extras) { released++ }
        val presenter = IslandMediaSinglePresenter()
        presenter.render(real, preview)
        val launchTarget = real.currentIslandData
        real.islandFreeformAnimRunning.value = true
        real.state = MiniWindowExpanded()
        presenter.release(preview)
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(launchTarget, real.currentIslandData)
        assertSame(launchTarget, real.fakeView!!.currentIslandData)
        assertSame(selected.view, real.expandedView.getChildAt(0))
        assertEquals(0, released)
        real.islandFreeformAnimRunning.value = false
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(launchTarget, real.currentIslandData)
        assertEquals(0, released)
        real.state = BigIsland()
        real.viewTreeObserver.dispatchOnPreDraw()
        assertSame(native, real.currentIslandData)
        assertSame(native.view, real.expandedView.getChildAt(0))
        assertEquals(1, released)
    }

    @Test fun browsedEligibilityIsScopedToTheQueryAndRestoresCanonicalDataEvenOnFailure() {
        val native = data("A")
        val real = content(native)
        val extras = Bundle().apply { putString("miui.pkg.name", "B") }
        val context = RuntimeEnvironment.getApplication()
        IslandMediaMiniWindowHooker.withEligibility(context, real, extras, Supplier<Any?> {
            assertEquals("B", real.currentIslandData!!.extras.getString("miui.pkg.name"))
            assertSame(native.view, real.currentIslandData!!.view)
            assertSame(native, real.fakeView!!.currentIslandData)
            real.dynamicIslandEventCoordinator.updateMiniBar(real)
            null
        })
        assertSame(native, real.currentIslandData)
        assertFalse(real.miniBarVisible)
        assertEquals(1, real.dynamicIslandEventCoordinator.capabilityUpdates)
        assertEquals("A", native.extras.getString("miui.pkg.name"))
        try {
            IslandMediaMiniWindowHooker.withEligibility(context, real, extras, Supplier<Any?> {
                error("Native policy failure")
            })
            fail("Expected policy failure")
        } catch (_: IllegalStateException) {
            assertSame(native, real.currentIslandData)
        }
    }
}
