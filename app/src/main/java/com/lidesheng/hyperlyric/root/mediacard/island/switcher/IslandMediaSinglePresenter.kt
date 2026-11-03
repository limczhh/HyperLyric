package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import com.lidesheng.hyperlyric.root.utils.HookLogger
import java.lang.reflect.Method
import java.lang.reflect.Field
import java.util.IdentityHashMap

/** Swaps expanded content only. No add/update/remove call reaches the island state machine. */
internal class IslandMediaSinglePresenter(
    private val runGuarded: (() -> Unit) -> Unit = { it() },
) {
    private data class Presentation(
        val fake: Any?,
        val preview: IslandMediaExpandedContent,
        val extras: Bundle,
    )

    private class Pending(
        val observer: ViewTreeObserver,
        val listener: ViewTreeObserver.OnPreDrawListener,
        var preview: IslandMediaExpandedContent?,
        var onIdle: (() -> Unit)? = null,
    )

    private val presentations = IdentityHashMap<Any, Presentation>()
    private val pending = IdentityHashMap<Any, Pending>()
    private val methods = HashMap<Pair<Class<*>, String>, Method>()
    private val reboundFields = HashMap<Class<*>, Field>()

    fun owns(real: Any): Boolean = presentations.containsKey(real) || pending.containsKey(real)

    fun displayedContent(real: Any): IslandMediaExpandedContent? =
        presentations[real]?.preview?.takeIf { isPresented(real, it.view, "View") }

    fun isBusy(real: Any): Boolean {
        val phase = nativeInteraction(real) ?: if (method(real, "isAnimating", 0).invoke(real) == true) {
            "island_animation"
        } else null
        if (real in pending) HookLogger.dState(
            stateId = "IslandMediaCardSwitcher.native_phase", tag = "IslandMediaCardSwitcher", state = phase ?: "idle",
        ) { "单岛原生交互状态: phase=${phase ?: "idle"}" }
        return phase != null
    }

    private fun nativeInteraction(real: Any): String? {
        fun signal(target: Any, name: String): Boolean =
            get(target, name)?.let { get(it, "Value") == true } == true
        if (signal(real, "IslandAppAnimRunning")) return "app_animation"
        if (signal(real, "IslandFreeformAnimRunning")) return "freeform_animation"
        // The return-to-island lookup still uses this target after the launch animation ends.
        // Keep it until the app/freeform state itself exits, including its return transition.
        when (get(real, "State")?.javaClass?.simpleName) {
            "AppExpanded", "SubAppExpanded" -> return "app_window"
            "MiniWindowExpanded", "SubMiniWindowExpanded" -> return "freeform_window"
        }
        val fake = get(real, "FakeView") ?: return null
        // Both supplied plugin generations clear tracking at UP, then retain needResetState
        // until the FakeView rebound completes/cancels. The ordinary island animator is idle here.
        if (signal(fake, "TrackingToOpenMW")) return "minibar_tracking"
        val rebounding = reboundFields.getOrPut(fake.javaClass) {
            fake.javaClass.getDeclaredField("needResetState").apply { isAccessible = true }
        }.getBoolean(fake)
        return if (rebounding) "minibar_rebound" else null
    }

    fun whenIdle(real: Any, action: () -> Unit) {
        if (real !in pending) defer(real, presentations[real]?.preview)
        pending.getValue(real).onIdle = action
    }

    fun render(real: Any, preview: IslandMediaExpandedContent?) {
        if (preview == null) {
            restore(real)
            return
        }
        if (get(real, "State")?.javaClass?.simpleName != "Expanded") return
        if (isBusy(real)) {
            defer(real, preview)
            return
        }
        val queued = cancelPending(real)
        renderNow(real, preview)
        queued?.preview?.let(::closeIfUnused)
        queued?.onIdle?.invoke()
    }

    private fun renderNow(real: Any, preview: IslandMediaExpandedContent) {
        val current = get(real, "CurrentIslandData") ?: return
        val fake = get(real, "FakeView")
        val previous = presentations[real]
        if (previous?.preview === preview &&
            previous.extras === preview.extras && previous.fake === fake &&
            isPresented(real, preview.view, "View") &&
            (fake == null || isPresented(fake, preview.fakeView, "FakeView"))
        ) return
        // Native data remains canonical. This argument supplies only the two visual children.
        if (previous != null && previous.fake !== fake) restoreFake(previous)
        val views = current.javaClass.getDeclaredConstructor().newInstance()
        set(views, "View", preview.view)
        set(views, "FakeView", preview.fakeView)
        val state = Presentation(fake, preview, preview.extras)
        presentations[real] = state
        bind(real, views, "View")
        fake?.let { bind(it, views, "FakeView") }
        refreshMiniWindowCapability(real)
        previous?.preview?.takeIf { it !== preview }?.let(::closeIfUnused)
    }

    fun restore(real: Any) {
        val state = presentations[real]
        if (state == null && real !in pending) return
        if ((real as View).isAttachedToWindow && nativeInteraction(real) != null) {
            // Native launch/rebound callbacks must observe one unchanged target until completion.
            defer(real, null)
            return
        }
        // Retain the visual children until the native transition finishes.
        if (!real.isAttachedToWindow) {
            val queued = cancelPending(real)
            finishRestore(real)
            queued?.preview?.let(::closeIfUnused)
            queued?.onIdle?.invoke()
        } else if (state != null || pending.containsKey(real)) defer(real, null)
    }

    private fun finishRestore(real: Any) {
        val state = presentations.remove(real) ?: return
        try {
            bind(real, get(real, "CurrentIslandData"), "View", restoring = true)
            restoreFake(state)
            refreshMiniWindowCapability(real)
        } finally {
            closeIfUnused(state.preview)
        }
    }

    private fun restoreFake(state: Presentation) {
        val fake = state.fake ?: return
        bind(fake, get(fake, "CurrentIslandData"), "FakeView", restoring = true)
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
            finishRestore(real)
            queued?.preview?.let(::closeIfUnused)
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

    private fun cancelPending(real: Any): Pending? {
        val task = pending.remove(real) ?: return null
        if (task.observer.isAlive) task.observer.removeOnPreDrawListener(task.listener)
        return task
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
                if (!view.isAttachedToWindow || !isBusy(real)) {
                    val task = cancelPending(real)
                    val next = task?.preview
                    if (next != null && view.isAttachedToWindow &&
                        get(real, "State")?.javaClass?.simpleName == "Expanded"
                    ) {
                        renderNow(real, next)
                    } else finishRestore(real)
                    next?.let(::closeIfUnused)
                    task?.onIdle?.invoke()
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
        val targetView = data?.let { get(it, viewName) } as? View
        if (targetView == null || isPresented(content, targetView, viewName)) return
        // A recreated native host may already own this player. An old host must not steal it back.
        if (restoring && targetView.parent != null) return
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
