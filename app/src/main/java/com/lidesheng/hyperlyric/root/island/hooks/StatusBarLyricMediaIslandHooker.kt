package com.lidesheng.hyperlyric.root.island.hooks

import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Hides only the native media island matching the current status-bar lyric source package. */
internal object StatusBarLyricMediaIslandHooker {
    private const val TAG = "StatusBarLyricMediaIslandHooker"
    private const val CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
    private const val CAPABILITY = "island.status_bar_lyric.native_media_suppression"

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        val controllerClass = classLoader.loadClass(CONTROLLER_CLASS)
        controllerClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            module.managedHook(
                executable = constructor,
                capability = "$CAPABILITY.controller_init",
                hooker = ControllerInitializedHook(),
            )
        }

        var upsertHookCount = 0
        var removalHookInstalled = false
        controllerClass.declaredMethods.forEach { method ->
            when {
                method.name in UPSERT_METHODS &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0].name.endsWith(".DynamicIslandData") &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType -> {
                    method.isAccessible = true
                    module.managedHook(
                        executable = method,
                        capability = "$CAPABILITY.${method.name}",
                        hooker = UpsertHook(),
                    )
                    upsertHookCount++
                }

                method.name == "removeDynamicIslandView" &&
                        method.parameterCount == 2 &&
                        method.parameterTypes[0] == String::class.java &&
                        method.parameterTypes[1] == Boolean::class.javaPrimitiveType -> {
                    method.isAccessible = true
                    module.managedHook(
                        executable = method,
                        capability = "$CAPABILITY.remove",
                        hooker = RemoveHook(),
                    )
                    removalHookInstalled = true
                }
            }
        }
        if (upsertHookCount == 0 || !removalHookInstalled) {
            HookLogger.w(
                TAG,
                "状态栏歌词原生媒体岛抑制不完整: upsertHooks=$upsertHookCount, " +
                        "removeHook=$removalHookInstalled"
            )
        }
    }

    private class ControllerInitializedHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            chain.thisObject?.let(StatusBarLyricMediaIslandCoordinator::onControllerReady)
            return result
        }
    }

    private class UpsertHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject ?: return chain.proceed()
            val data = chain.args.getOrNull(0) ?: return chain.proceed()
            val expanded = chain.args.getOrNull(1) as? Boolean ?: false
            val wasPresentationActive =
                StatusBarLyricMediaIslandCoordinator.isPresentationActive()
            if (wasPresentationActive && StatusBarLyricMediaIslandCoordinator.shouldSuppressUpsert(
                    controller,
                    data,
                    expanded,
                )
            ) {
                return null
            }

            val result = chain.proceed()
            if (wasPresentationActive ||
                StatusBarLyricMediaIslandCoordinator.isPresentationActive()
            ) {
                StatusBarLyricMediaIslandCoordinator.onUpsertApplied(controller, data, expanded)
            }
            return result
        }
    }

    private class RemoveHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject
            val key = chain.args.getOrNull(0) as? String
            val result = chain.proceed()
            if (controller != null && key != null) {
                StatusBarLyricMediaIslandCoordinator.onNativeRemoval(controller, key)
            }
            return result
        }
    }

    private val UPSERT_METHODS = setOf("addDynamicIslandView", "updateDynamicIslandView")
}

/**
 * Tracks native controller entries so a matching media island can be removed as soon as the
 * current playback has available lyrics, then restored from its latest payload afterwards.
 */
internal object StatusBarLyricMediaIslandCoordinator {
    private data class Entry(
        val key: String,
        val packageName: String,
        val data: Any,
        val expanded: Boolean,
        var suppressed: Boolean,
    )

    private data class Action(
        val controller: Any,
        val entry: Entry,
        val remove: Boolean,
    )

    private data class SuppressionPolicy(
        val behavior: Int,
        val ownerPackageName: String?,
    ) {
        val targetPackage: String?
            get() = ownerPackageName.takeIf {
                behavior != RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE
            }
    }

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val entriesByController = WeakHashMap<Any, MutableMap<String, Entry>>()
    private val internalRemovals = WeakHashMap<Any, MutableSet<String>>()

    @Volatile
    private var suppressionPolicy = SuppressionPolicy(
        behavior = RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
        ownerPackageName = null,
    )

    fun updateSuppressionPolicy(behavior: Int, ownerPackageName: String?) {
        val policy = SuppressionPolicy(
            behavior = behavior.coerceIn(
                RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_NONE,
                RootConstants.STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS,
            ),
            ownerPackageName = ownerPackageName?.trim()?.takeIf(String::isNotEmpty),
        )
        if (suppressionPolicy == policy) return
        suppressionPolicy = policy
        reconcile()
    }

    fun isPresentationActive(): Boolean = suppressionPolicy.targetPackage != null

    fun onControllerReady(controller: Any) {
        synchronized(lock) {
            entriesByController.getOrPut(controller) { mutableMapOf() }
        }
        reconcile()
    }

    /** Returns true when the native add/update call must be skipped. */
    fun shouldSuppressUpsert(controller: Any, data: Any, expanded: Boolean): Boolean {
        if (!isPresentationActive()) return false
        val media = IslandProbeUtils.extractMediaIslandInfo(data) ?: return false
        val key = readKey(data) ?: return false
        if (!shouldSuppressPackage(media.packageName)) return false

        val entry = Entry(
            key = key,
            packageName = media.packageName,
            data = data,
            expanded = expanded,
            suppressed = true,
        )
        synchronized(lock) {
            entriesByController.getOrPut(controller) { mutableMapOf() }[key] = entry
        }
        removeNativeIsland(controller, entry)
        return true
    }

    fun onUpsertApplied(controller: Any, data: Any, expanded: Boolean) {
        val key = readKey(data) ?: return
        val media = IslandProbeUtils.extractMediaIslandInfo(data)
        val currentNativeData = readIslandDataMap(controller)?.get(key)
        if (media != null && currentNativeData !== data) return
        if (media == null && currentNativeData != null &&
            IslandProbeUtils.extractMediaIslandInfo(currentNativeData) != null
        ) return

        val targetPackage = suppressionPolicy.targetPackage
        var entryToSuppress: Entry? = null
        synchronized(lock) {
            val tracked = entriesByController.getOrPut(controller) { mutableMapOf() }
            if (media == null || media.packageName != targetPackage) {
                tracked.remove(key)
            } else {
                val entry = Entry(
                    key = key,
                    packageName = media.packageName,
                    data = data,
                    expanded = expanded,
                    suppressed = false,
                )
                tracked[key] = entry
                if (shouldSuppressPackage(media.packageName)) {
                    entry.suppressed = true
                    entryToSuppress = entry
                }
            }
        }
        entryToSuppress?.let { removeNativeIsland(controller, it) }
    }

    fun onNativeRemoval(controller: Any, key: String) {
        synchronized(lock) {
            if (internalRemovals[controller]?.contains(key) == true) return
            entriesByController[controller]?.remove(key)
        }
    }

    private fun reconcile() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            reconcileOnMain()
        } else {
            mainHandler.post(::reconcileOnMain)
        }
    }

    private fun reconcileOnMain() {
        val targetPackage = suppressionPolicy.targetPackage
        captureVisibleOwnerEntries(targetPackage)
        val actions = mutableListOf<Action>()
        synchronized(lock) {
            entriesByController.entries.toList().forEach { (controller, entries) ->
                val iterator = entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next().value
                    val currentMedia = IslandProbeUtils.extractMediaIslandInfo(entry.data)
                    if (readKey(entry.data) != entry.key ||
                        currentMedia?.packageName != entry.packageName
                    ) {
                        iterator.remove()
                        continue
                    }

                    val shouldSuppress = targetPackage == entry.packageName
                    if (entry.suppressed != shouldSuppress) {
                        entry.suppressed = shouldSuppress
                        actions += Action(controller, entry, remove = shouldSuppress)
                    }
                }
            }
        }

        actions.forEach { action ->
            if (action.remove) {
                removeNativeIsland(action.controller, action.entry)
            } else {
                restoreNativeIsland(action.controller, action.entry)
            }
        }
    }

    private fun captureVisibleOwnerEntries(packageName: String?) {
        if (packageName == null) return
        val controllers = synchronized(lock) { entriesByController.keys.toList() }
        controllers.forEach { controller ->
            readIslandDataMap(controller).orEmpty().forEach { (mapKey, data) ->
                if (data == null) return@forEach
                val media = IslandProbeUtils.extractMediaIslandInfo(data) ?: return@forEach
                if (media.packageName != packageName) return@forEach
                val key = readKey(data) ?: mapKey?.toString() ?: return@forEach
                synchronized(lock) {
                    entriesByController.getOrPut(controller) { mutableMapOf() }[key] = Entry(
                        key = key,
                        packageName = media.packageName,
                        data = data,
                        expanded = false,
                        suppressed = false,
                    )
                }
            }
        }
    }

    private fun removeNativeIsland(controller: Any, entry: Entry) {
        runOnMain {
            if (!shouldSuppressPackage(entry.packageName) || !isCurrentEntry(controller, entry)) {
                return@runOnMain
            }
            val currentData = readIslandDataMap(controller)?.get(entry.key) ?: return@runOnMain
            if (IslandProbeUtils.extractMediaIslandInfo(currentData)?.packageName != entry.packageName) {
                return@runOnMain
            }
            synchronized(lock) {
                internalRemovals.getOrPut(controller) { mutableSetOf() }.add(entry.key)
            }
            try {
                val method = findMethod(controller.javaClass) { candidate ->
                    candidate.name == "removeDynamicIslandView" &&
                            candidate.parameterCount == 2 &&
                            candidate.parameterTypes[0] == String::class.java &&
                            candidate.parameterTypes[1] == Boolean::class.javaPrimitiveType
                } ?: return@runOnMain
                method.isAccessible = true
                method.invoke(controller, entry.key, false)
            } catch (error: Throwable) {
                HookLogger.w(
                    TAG,
                    "移除歌词对应的原生媒体岛失败: key=${entry.key}, reason=${error.message}"
                )
            } finally {
                synchronized(lock) {
                    internalRemovals[controller]?.let { keys ->
                        keys.remove(entry.key)
                        if (keys.isEmpty()) internalRemovals.remove(controller)
                    }
                }
            }
        }
    }

    private fun restoreNativeIsland(controller: Any, entry: Entry) {
        runOnMain {
            if (shouldSuppressPackage(entry.packageName) || entry.suppressed ||
                !isCurrentEntry(controller, entry) ||
                readKey(entry.data) != entry.key ||
                IslandProbeUtils.extractMediaIslandInfo(entry.data)?.packageName != entry.packageName ||
                readIslandDataMap(controller)?.containsKey(entry.key) == true
            ) return@runOnMain

            try {
                val method = findMethod(controller.javaClass) { candidate ->
                    candidate.name == "addDynamicIslandView" &&
                            candidate.parameterCount == 2 &&
                            candidate.parameterTypes[0].isAssignableFrom(entry.data.javaClass) &&
                            candidate.parameterTypes[1] == Boolean::class.javaPrimitiveType
                } ?: return@runOnMain
                method.isAccessible = true
                method.invoke(controller, entry.data, entry.expanded)
                val restoredData = readIslandDataMap(controller)?.get(entry.key)
                val restoredMedia = restoredData?.let(IslandProbeUtils::extractMediaIslandInfo)
                synchronized(lock) {
                    val tracked = entriesByController[controller] ?: return@synchronized
                    if (tracked[entry.key] !== entry) return@synchronized
                    if (restoredData != null &&
                        readKey(restoredData) == entry.key &&
                        restoredMedia?.packageName == entry.packageName
                    ) {
                        tracked[entry.key] = entry.copy(data = restoredData, suppressed = false)
                    } else {
                        tracked.remove(entry.key)
                    }
                }
            } catch (error: Throwable) {
                HookLogger.w(
                    TAG,
                    "恢复歌词对应的原生媒体岛失败: key=${entry.key}, reason=${error.message}"
                )
            }
        }
    }

    private fun isCurrentEntry(controller: Any, entry: Entry): Boolean = synchronized(lock) {
        entriesByController[controller]?.get(entry.key) === entry
    }

    private fun shouldSuppressPackage(packageName: String): Boolean =
        suppressionPolicy.targetPackage == packageName

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun readIslandDataMap(controller: Any): Map<*, *>? {
        var current: Class<*>? = controller.javaClass
        while (current != null) {
            val field = current.declaredFields.firstOrNull { it.name == "islandData" }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(controller) as? Map<*, *>
                }.getOrNull()
            }
            current = current.superclass
        }
        return null
    }

    private fun readKey(data: Any): String? = runCatching {
        data.javaClass.methods.firstOrNull {
            it.name == "getKey" && it.parameterCount == 0
        }?.invoke(data) as? String
    }.getOrNull()?.takeIf(String::isNotEmpty)

    private fun findMethod(type: Class<*>, matches: (Method) -> Boolean): Method? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull(matches)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private const val TAG = "StatusBarLyricMediaIslandCoordinator"
}
