package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.graphics.drawable.Drawable
import android.view.View
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Changes only minibar presentation; miniBarVisible remains owned by the native capability check. */
internal object IslandMediaMiniBarIndicator {
    private const val TAG = "IslandMediaMiniBarIndicator"
    private val bars = WeakHashMap<View, BarState>()

    fun nativeVisibilityUpdated(owner: Any) {
        val bar = call(owner, "getMiniBar") as? View ?: return
        bars[bar]?.let {
            it.nativeVisibility = bar.visibility
            it.forcedVisible = false
        }
    }

    fun update(window: View, content: Any?, pages: IntArray?) {
        val active = LinkedHashMap<View, Any>()
        if (content != null && pages != null) {
            (call(content, "getMiniBar") as? View)?.let { active[it] = content }
            call(content, "getFakeView")?.let { fake ->
                (call(fake, "getMiniBar") as? View)?.let { active[it] = fake }
            }
        }
        bars.entries.toList().forEach { (bar, state) ->
            if (state.window.get() === window && bar !in active) {
                restore(bar, state)
                bars.remove(bar)
            }
        }
        if (pages == null || content == null) return
        active.forEach { (bar, owner) ->
            val background = bar.background ?: return@forEach
            var state = bars[bar]
            if (state == null || background !== state.native && background !== state.segmented) {
                val previous = state
                state = BarState(WeakReference(window), background, previous?.nativeVisibility ?: bar.visibility)
                    .also { it.forcedVisible = previous?.forcedVisible == true }
                bars[bar] = state
            }
            val drawable = if (pages[0] > 1) state.segmented else state.native
            if (bar.background !== drawable) {
                bar.background = drawable
                if (drawable === state.segmented) state.segmented.attached()
            }
            state.segmented.update(pages[0], pages[1])
            val canSlide = call(owner, "getMiniBarVisible") == true
            if (!canSlide && bar.visibility == View.GONE) {
                // Display an informational bar without changing the flag read by FakeView gestures.
                state.nativeVisibility = View.GONE
                state.forcedVisible = true
                bar.visibility = View.VISIBLE
                owner.javaClass.methods.first {
                    it.name.startsWith("updateMiniBarTranslation") && it.parameterCount == 1
                }.invoke(owner, content)
            }
        }
        HookLogger.dState(
            stateId = "$TAG.pages", tag = TAG, state = "${pages[0]}/${pages[1]}/${active.size}",
        ) { "媒体岛分段指示更新: pages=${pages[0]}, selected=${pages[1]}, bars=${active.size}" }
    }

    fun clear() {
        bars.entries.toList().forEach { (bar, state) -> restore(bar, state) }
        bars.clear()
    }

    private fun restore(bar: View, state: BarState) {
        if (bar.background === state.segmented) bar.background = state.native
        if (state.forcedVisible && bar.visibility == View.VISIBLE) bar.visibility = state.nativeVisibility
    }

    private class BarState(
        val window: WeakReference<View>,
        val native: Drawable,
        var nativeVisibility: Int,
    ) {
        val segmented = SegmentedMiniBarDrawable(native)
        var forcedVisible = false
    }

    private fun call(target: Any, name: String): Any? = target.javaClass.methods.first {
        it.name == name && it.parameterCount == 0
    }.apply { isAccessible = true }.invoke(target)
}
