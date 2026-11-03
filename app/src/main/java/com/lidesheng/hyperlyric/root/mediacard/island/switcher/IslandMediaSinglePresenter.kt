package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import java.util.IdentityHashMap

/** Swaps expanded content only. No add/update/remove call reaches the island state machine. */
internal class IslandMediaSinglePresenter(
    private val runGuarded: (() -> Unit) -> Unit = { it() },
) {
    private data class Presentation(
        val native: Any,
        val fake: Any?,
        val fakeNative: Any?,
        val preview: IslandMediaExpandedContent,
        val extras: Bundle,
        val displayed: Any,
    )

    private class Pending(
        val observer: ViewTreeObserver,
        val listener: ViewTreeObserver.OnPreDrawListener,
        var preview: IslandMediaExpandedContent?,
    )

    private val presentations = IdentityHashMap<Any, Presentation>()
    private val pending = IdentityHashMap<Any, Pending>()
    private val methods = HashMap<Pair<Class<*>, String>, Method>()

    fun owns(real: Any): Boolean = presentations.containsKey(real) || pending.containsKey(real)

    fun render(real: Any, preview: IslandMediaExpandedContent?) {
        if (preview == null) {
            restore(real)
            return
        }
        if (get(real, "State")?.javaClass?.simpleName != "Expanded") return
        if (method(real, "isAnimating", 0).invoke(real) == true) {
            defer(real, preview)
            return
        }
        val queued = cancelPending(real)
        renderNow(real, preview)
        queued?.let(::closeIfUnused)
    }

    private fun renderNow(real: Any, preview: IslandMediaExpandedContent) {
        val current = get(real, "CurrentIslandData") ?: return
        val fake = get(real, "FakeView")
        val previous = presentations[real]
        if (previous?.displayed === current && previous.preview === preview &&
            previous.extras === preview.extras && previous.fake === fake &&
            isPresented(real, preview.view, "View") &&
            (fake == null || get(fake, "CurrentIslandData") === previous.displayed &&
                isPresented(fake, preview.fakeView, "FakeView"))
        ) return
        // Native media updates can replace only the payload, retaining the browsing Views.
        // Save the newer native payload without restoring/reinserting the other expanded child.
        if (previous != null && previous.fake !== fake) restoreFake(previous)
        val native = if (previous != null && current === previous.displayed) previous.native else current
        val fakeCurrent = fake?.let { get(it, "CurrentIslandData") }
        val fakeNative = if (previous != null && previous.fake === fake && fakeCurrent === previous.displayed) {
            previous.fakeNative
        } else fakeCurrent
        val displayed = native.javaClass.getDeclaredConstructor().newInstance()
        listOf("Key", "Priority", "Properties", "TickerData").forEach { name ->
            set(displayed, name, get(native, name))
        }
        val extras = Bundle(get(native, "Extras") as? Bundle ?: Bundle()).apply {
            putAll(preview.extras)
            putString("miui.key", get(native, "Key") as? String)
        }
        set(displayed, "Extras", extras)
        set(displayed, "View", preview.view)
        set(displayed, "FakeView", preview.fakeView)
        val state = Presentation(native, fake, fakeNative, preview, preview.extras, displayed)
        presentations[real] = state
        bind(real, displayed, "View")
        fake?.let { bind(it, displayed, "FakeView") }
        if (miniWindowTargetChanged(current, displayed)) refreshMiniWindowCapability(real)
        previous?.preview?.takeIf { it !== preview }?.let(::closeIfUnused)
    }

    fun restore(real: Any) {
        val state = presentations[real]
        state?.let { restoreMetadata(real, it) }
        // Restore native identity before collapse; retain its current children until the
        // transition finishes. Reparenting/detaching inside onStateChange disrupts that animation.
        if (!(real as View).isAttachedToWindow) {
            val queued = cancelPending(real)
            finishRestore(real)
            queued?.let(::closeIfUnused)
        } else if (state != null || pending.containsKey(real)) defer(real, null)
    }

    private fun restoreMetadata(real: Any, state: Presentation) {
        var restored = false
        // A native update may have replaced our projection already. Keep that newer payload.
        if (get(real, "CurrentIslandData") === state.displayed) {
            set(real, "CurrentIslandData", state.native)
            restored = true
        }
        state.fake?.let { fake ->
            if (get(fake, "CurrentIslandData") === state.displayed) {
                set(fake, "CurrentIslandData", state.fakeNative)
                restored = true
            }
        }
        if (restored) refreshMiniWindowCapability(real)
    }

    private fun finishRestore(real: Any) {
        val state = presentations.remove(real) ?: return
        try {
            val current = get(real, "CurrentIslandData")
            bind(real, if (current === state.displayed) state.native else current, "View", restoring = true)
            restoreFake(state)
        } finally {
            closeIfUnused(state.preview)
        }
    }

    private fun restoreFake(state: Presentation) {
        val fake = state.fake ?: return
        val current = get(fake, "CurrentIslandData")
        bind(fake, if (current === state.displayed) state.fakeNative else current, "FakeView", restoring = true)
    }

    fun release(preview: IslandMediaExpandedContent) {
        preview.retire()
        val owners = (presentations.keys + pending.keys).toSet().filter {
            presentations[it]?.preview === preview || pending[it]?.preview === preview
        }
        owners.forEach(::restore)
        closeIfUnused(preview)
    }

    /** Failure recovery runs synchronously, without scheduling an animation task first. */
    fun restoreAll() {
        (presentations.keys + pending.keys).toSet().forEach { real ->
            val queued = cancelPending(real)
            presentations[real]?.let { restoreMetadata(real, it) }
            finishRestore(real)
            queued?.let(::closeIfUnused)
        }
    }

    fun forgetAll() {
        val previews = (presentations.values.map { it.preview } + pending.values.mapNotNull { it.preview }).toSet()
        pending.keys.toList().forEach(::cancelPending)
        presentations.clear()
        previews.forEach { it.close() }
    }

    private fun closeIfUnused(preview: IslandMediaExpandedContent) {
        if (preview.retired && presentations.values.none { it.preview === preview } &&
            pending.values.none { it.preview === preview }
        ) {
            preview.close()
        }
    }

    private fun cancelPending(real: Any): IslandMediaExpandedContent? {
        val task = pending.remove(real) ?: return null
        if (task.observer.isAlive) task.observer.removeOnPreDrawListener(task.listener)
        return task.preview
    }

    private fun defer(real: Any, preview: IslandMediaExpandedContent?) {
        pending[real]?.let { task ->
            val old = task.preview
            task.preview = preview
            old?.takeIf { it !== preview }?.let(::closeIfUnused)
            return
        }
        val view = real as View
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            runGuarded {
                if (!view.isAttachedToWindow || method(real, "isAnimating", 0).invoke(real) != true) {
                    val next = cancelPending(real)
                    if (next != null && view.isAttachedToWindow &&
                        get(real, "State")?.javaClass?.simpleName == "Expanded"
                    ) {
                        renderNow(real, next)
                    } else finishRestore(real)
                    next?.let(::closeIfUnused)
                }
            }
            true
        }
        pending[real] = Pending(observer, listener, preview)
        observer.addOnPreDrawListener(listener)
        // Request one frame for an idle host; ongoing transitions provide their own frames.
        view.invalidate()
    }

    private fun bind(content: Any, data: Any?, viewName: String, restoring: Boolean = false) {
        val before = get(content, "CurrentIslandData")
        val previousView = before?.let { get(it, viewName) }
        val targetView = data?.let { get(it, viewName) } as? View
        set(content, "CurrentIslandData", data)
        // Native media updates may replace CurrentIslandData while deliberately keeping the
        // expanded child. Inspect the actual parent instead of treating that payload as the View tree.
        if (isPresented(content, targetView, viewName) || targetView == null && previousView == null) return
        // A recreated native host may already own this player. An old host must not steal it back.
        if (restoring && targetView?.parent != null) return
        // Both supplied plugin generations keep this suspend ABI, but the actual real/fake
        // updateExpandedView bodies are synchronous and never access their continuation.
        method(content, "updateExpandedView", 3).invoke(content, data, false, null)
    }

    private fun isPresented(content: Any, view: View?, viewName: String): Boolean {
        view ?: return false
        val expanded = get(content, if (viewName == "View") "ExpandedView" else "FakeExpandedView") ?: return false
        var parent = view.parent
        while (parent != null) {
            if (parent === expanded) return true
            parent = parent.parent
        }
        return false
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
