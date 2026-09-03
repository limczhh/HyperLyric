package com.lidesheng.hyperlyric.root.island.host

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.lyric.view.RichLyricLineView
import com.lidesheng.hyperlyric.lyric.view.SpaceGateRichLyricLineView
import java.util.WeakHashMap

internal object IslandViewRegistry {
    private val lock = Any()
    private val activeHosts = WeakHashMap<ViewGroup, HostRecord>()
    private val injectedViewsByRoot = WeakHashMap<ViewGroup, MutableMap<View, Unit>>()
    private var nextGeneration = 0L

    enum class AttachState {
        PENDING_ATTACH,
        ATTACHED
    }

    enum class HostKind {
        REAL,
        FAKE
    }

    data class HostToken(
        val root: ViewGroup,
        val packageName: String,
        val kind: HostKind,
        val moduleType: String?,
        val generation: Long
    )

    data class InjectedHostToken(
        val host: HostToken,
        val injectedViews: List<View>
    )

    private data class HostRecord(
        var packageName: String,
        var kind: HostKind,
        var moduleType: String?,
        var generation: Long,
        var attachState: AttachState
    )

    fun registerReal(view: ViewGroup, packageName: String): HostToken {
        return register(view, packageName, HostKind.REAL, moduleType = null)
    }

    fun registerFake(
        view: ViewGroup,
        packageName: String,
        moduleType: String?
    ): HostToken {
        return register(view, packageName, HostKind.FAKE, moduleType)
    }

    private fun register(
        view: ViewGroup,
        packageName: String,
        kind: HostKind,
        moduleType: String?
    ): HostToken {
        return synchronized(lock) {
            val existing = activeHosts[view]
            val record = if (existing == null ||
                existing.packageName != packageName ||
                existing.kind != kind ||
                existing.moduleType != moduleType
            ) {
                HostRecord(
                    packageName = packageName,
                    kind = kind,
                    moduleType = moduleType,
                    generation = ++nextGeneration,
                    attachState = if (view.isAttachedToWindow) {
                        AttachState.ATTACHED
                    } else {
                        AttachState.PENDING_ATTACH
                    }
                ).also { activeHosts[view] = it }
            } else {
                if (view.isAttachedToWindow) {
                    existing.attachState = AttachState.ATTACHED
                }
                existing
            }
            record.toToken(view)
        }
    }

    fun unregister(token: HostToken) {
        synchronized(lock) {
            val record = activeHosts[token.root] ?: return
            if (record.generation != token.generation ||
                record.packageName != token.packageName ||
                record.kind != token.kind ||
                record.moduleType != token.moduleType
            ) {
                return
            }
            activeHosts.remove(token.root)
            injectedViewsByRoot.remove(token.root)
        }
    }

    fun unregister(root: ViewGroup) {
        synchronized(lock) {
            activeHosts.remove(root)
            injectedViewsByRoot.remove(root)
        }
    }

    /**
     * Invalidates callbacks queued during the previous attachment while retaining
     * the weak host record for a possible reattach.
     */
    fun markDetached(root: ViewGroup) {
        synchronized(lock) {
            val record = activeHosts[root] ?: return
            if (record.attachState == AttachState.PENDING_ATTACH) return
            record.generation = ++nextGeneration
            record.attachState = AttachState.PENDING_ATTACH
            injectedViewsByRoot.remove(root)
        }
    }

    fun markAttached(root: ViewGroup): HostToken? {
        return synchronized(lock) {
            val record = activeHosts[root] ?: return@synchronized null
            record.attachState = AttachState.ATTACHED
            record.toToken(root)
        }
    }

    fun isCurrent(token: HostToken): Boolean {
        return synchronized(lock) {
            val record = activeHosts[token.root]
            record?.generation == token.generation &&
                    record.packageName == token.packageName &&
                    record.kind == token.kind &&
                    record.moduleType == token.moduleType
        }
    }

    fun tokenFor(root: ViewGroup): HostToken? {
        return synchronized(lock) {
            activeHosts[root]?.toToken(root)
        }
    }

    fun refreshInjectedViews(root: ViewGroup) {
        val indexedViews = WeakHashMap<View, Unit>()
        collectInjectedViews(root, indexedViews)
        synchronized(lock) {
            if (activeHosts.containsKey(root)) {
                injectedViewsByRoot[root] = indexedViews
            }
        }
    }

    fun snapshotAttached(
        packageName: String? = null,
        kind: HostKind? = null
    ): List<HostToken> {
        val result = mutableListOf<HostToken>()
        synchronized(lock) {
            activeHosts.entries.forEach { entry ->
                val viewGroup = entry.key
                val record = entry.value
                if (viewGroup.isAttachedToWindow) {
                    record.attachState = AttachState.ATTACHED
                    if ((packageName == null || record.packageName == packageName) &&
                        (kind == null || record.kind == kind)
                    ) {
                        result += record.toToken(viewGroup)
                    }
                }
            }
        }
        return result
    }

    fun snapshotAttachedInjectedViews(
        packageName: String? = null,
        kind: HostKind? = null
    ): List<InjectedHostToken> {
        val result = mutableListOf<InjectedHostToken>()
        synchronized(lock) {
            activeHosts.entries.forEach { entry ->
                val root = entry.key
                val record = entry.value
                if (!root.isAttachedToWindow) {
                    return@forEach
                }
                record.attachState = AttachState.ATTACHED
                if (packageName != null && record.packageName != packageName) return@forEach
                if (kind != null && record.kind != kind) return@forEach

                val views = injectedViewsByRoot[root]
                    ?.keys
                    ?.filter { it.isAttachedToWindow }
                    .orEmpty()
                result += InjectedHostToken(
                    host = record.toToken(root),
                    injectedViews = views
                )
            }
        }
        return result
    }

    private fun HostRecord.toToken(root: ViewGroup): HostToken {
        return HostToken(
            root = root,
            packageName = packageName,
            kind = kind,
            moduleType = moduleType,
            generation = generation
        )
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
