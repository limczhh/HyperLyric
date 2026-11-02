package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.os.Bundle
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** Swaps expanded content only. No add/update/remove call reaches the island state machine. */
internal class IslandMediaSinglePresenter {
    private data class Presentation(
        val native: Any,
        val fake: Any?,
        val fakeNative: Any?,
        val preview: Any,
        val displayed: Any,
    )

    private val presentations = IdentityHashMap<Any, Presentation>()
    private val methods = HashMap<Pair<Class<*>, String>, Method>()

    fun owns(real: Any): Boolean = presentations.containsKey(real)

    fun render(real: Any, preview: Any?) {
        if (preview == null) {
            restore(real)
            return
        }
        val current = get(real, "CurrentIslandData") ?: return
        val fake = get(real, "FakeView")
        val previous = presentations[real]
        if (previous?.displayed === current && previous.preview === preview && previous.fake === fake) return
        val sameHost = previous?.displayed === current && previous.fake === fake
        if (previous != null && !sameHost) restore(real)
        val native = if (sameHost) previous.native else get(real, "CurrentIslandData") ?: return
        val fakeNative = if (sameHost) previous.fakeNative else fake?.let { get(it, "CurrentIslandData") }
        val displayed = native.javaClass.getDeclaredConstructor().newInstance()
        listOf("Key", "Priority", "Properties", "TickerData").forEach { name ->
            set(displayed, name, get(native, name))
        }
        val extras = Bundle(get(preview, "Extras") as Bundle).apply {
            putString("miui.key", get(native, "Key") as? String)
        }
        set(displayed, "Extras", extras)
        set(displayed, "View", get(preview, "View"))
        set(displayed, "FakeView", get(preview, "FakeView"))
        val state = Presentation(native, fake, fakeNative, preview, displayed)
        presentations[real] = state
        bind(real, displayed, "View")
        fake?.let { bind(it, displayed, "FakeView") }
        if (miniWindowTargetChanged(current, displayed)) refreshMiniWindowCapability(real)
    }

    fun restore(real: Any) {
        val state = presentations.remove(real) ?: return
        var restored = false
        // A native update may have replaced our projection already. Keep that newer payload.
        if (get(real, "CurrentIslandData") === state.displayed) {
            bind(real, state.native, "View")
            restored = true
        }
        state.fake?.let { fake ->
            if (get(fake, "CurrentIslandData") === state.displayed) {
                bind(fake, state.fakeNative, "FakeView")
                restored = true
            }
        }
        if (restored) refreshMiniWindowCapability(real)
    }

    fun restoreAll() = presentations.keys.toList().forEach(::restore)

    private fun bind(content: Any, data: Any?, viewName: String) {
        val before = get(content, "CurrentIslandData")
        val previousView = before?.let { get(it, viewName) }
        set(content, "CurrentIslandData", data)
        // Metadata and progress updates reuse the binder's existing views. Reparent only
        // when actually changing cards, rather than removing/adding children every callback.
        if (previousView === data?.let { get(it, viewName) }) return
        // Both supplied plugin generations keep this suspend ABI, but the actual real/fake
        // updateExpandedView bodies are synchronous and never access their continuation.
        method(content, "updateExpandedView", 3).invoke(content, data, false, null)
    }

    @Suppress("DEPRECATION")
    private fun miniWindowTargetChanged(before: Any, after: Any): Boolean {
        val old = get(before, "Extras") as? Bundle
        val new = get(after, "Extras") as? Bundle
        return listOf("miui.pkg.name", "miui.user.id", "miui.pending.intent", "miui.sbn").any {
            old?.get(it) != new?.get(it)
        }
    }

    private fun refreshMiniWindowCapability(real: Any) {
        val events = get(real, "DynamicIslandEventCoordinator") ?: return
        method(events, "updateMiniBar", 1).invoke(events, real)
        val extras = get(get(real, "CurrentIslandData") ?: return, "Extras") as? Bundle
        val pkg = extras?.getString("miui.pkg.name")
        val realCanSlide = get(real, "MiniBarVisible")
        val fakeCanSlide = get(real, "FakeView")?.let { get(it, "MiniBarVisible") }
        HookLogger.dState(
            stateId = "IslandMediaCardSwitcher.mini_window", tag = "IslandMediaCardSwitcher",
            state = "$pkg/$realCanSlide/$fakeCanSlide",
        ) { "单岛下拉能力已刷新: pkg=$pkg, real=$realCanSlide, fake=$fakeCanSlide" }
    }

    private fun get(target: Any, name: String): Any? = method(target, "get$name", 0).invoke(target)
    private fun set(target: Any, name: String, value: Any?) = method(target, "set$name", 1).invoke(target, value)
    private fun method(target: Any, name: String, count: Int): Method =
        methods.getOrPut(target.javaClass to name) {
            target.javaClass.methods.first { it.name == name && it.parameterCount == count }
                .apply { isAccessible = true }
        }
}
