package com.lidesheng.hyperlyric.root.island

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import java.util.WeakHashMap

internal object IslandViewRegistry {
    private val lock = Any()
    private val activeIslandPkgNames = WeakHashMap<ViewGroup, String>()
    private val injectedViewsByRoot = WeakHashMap<ViewGroup, MutableMap<View, Unit>>()

    fun register(view: ViewGroup, packageName: String) {
        synchronized(lock) {
            activeIslandPkgNames[view] = packageName
        }
    }

    fun unregister(view: ViewGroup) {
        synchronized(lock) {
            activeIslandPkgNames.remove(view)
            injectedViewsByRoot.remove(view)
        }
    }

    fun refreshInjectedViews(root: ViewGroup) {
        val indexedViews = WeakHashMap<View, Unit>()
        collectInjectedViews(root, indexedViews)
        synchronized(lock) {
            if (activeIslandPkgNames.containsKey(root)) {
                injectedViewsByRoot[root] = indexedViews
            }
        }
    }

    fun snapshotAttached(packageName: String? = null): List<Pair<ViewGroup, String>> {
        val result = mutableListOf<Pair<ViewGroup, String>>()
        synchronized(lock) {
            val iterator = activeIslandPkgNames.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val viewGroup = entry.key
                if (viewGroup.isAttachedToWindow) {
                    if (packageName == null || entry.value == packageName) {
                        result += viewGroup to entry.value
                    }
                } else {
                    iterator.remove()
                    injectedViewsByRoot.remove(viewGroup)
                }
            }
        }
        return result
    }

    fun snapshotAttachedInjectedViews(
        packageName: String? = null
    ): List<Pair<ViewGroup, List<View>>> {
        val result = mutableListOf<Pair<ViewGroup, List<View>>>()
        synchronized(lock) {
            val iterator = activeIslandPkgNames.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val root = entry.key
                if (!root.isAttachedToWindow) {
                    iterator.remove()
                    injectedViewsByRoot.remove(root)
                    continue
                }
                if (packageName != null && entry.value != packageName) continue

                val views = injectedViewsByRoot[root]
                    ?.keys
                    ?.filter { it.isAttachedToWindow }
                    .orEmpty()
                result += root to views
            }
        }
        return result
    }

    private fun collectInjectedViews(view: View, result: MutableMap<View, Unit>) {
        when (view) {
            is RichLyricLineView,
            is SpaceGateRichLyricLineView -> result[view] = Unit
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    collectInjectedViews(view.getChildAt(index), result)
                }
            }
        }
    }
}
