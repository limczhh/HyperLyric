package com.lidesheng.hyperlyric.root.island.presentation

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.island.host.IslandViewRegistry
import java.util.WeakHashMap

/**
 * Observes registered Super Island projection attachment without owning presentation policy.
 *
 * Reconciliation runs synchronously from the attach callback. Android has completed the host's
 * own onAttachedToWindow() at this point but has not drawn the newly attached projection yet, so
 * current content and media time can be committed without exposing one stale/empty frame.
 */
internal class IslandHostAttachmentObserver(
    private val currentPresentationRevision: () -> Long,
    private val onHostAttached: (IslandViewRegistry.HostToken, Long) -> Unit
) {
    private val lock = Any()
    private val listeners = WeakHashMap<ViewGroup, View.OnAttachStateChangeListener>()

    fun observe(root: ViewGroup) {
        synchronized(lock) {
            if (listeners.containsKey(root)) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    val attachedRoot = view as? ViewGroup ?: return
                    val token = IslandViewRegistry.markAttached(attachedRoot) ?: return
                    val expectedRevision = currentPresentationRevision()
                    onHostAttached(token, expectedRevision)
                }

                override fun onViewDetachedFromWindow(view: View) {
                    (view as? ViewGroup)?.let(IslandViewRegistry::markDetached)
                }
            }
            listeners[root] = listener
            root.addOnAttachStateChangeListener(listener)
        }
    }

    fun stop(root: ViewGroup) {
        val listener = synchronized(lock) {
            listeners.remove(root)
        } ?: return
        root.removeOnAttachStateChangeListener(listener)
    }
}
