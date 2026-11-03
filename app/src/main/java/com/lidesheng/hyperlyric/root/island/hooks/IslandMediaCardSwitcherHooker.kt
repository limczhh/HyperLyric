package com.lidesheng.hyperlyric.root.island.hooks

import android.view.View
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.mediacard.island.switcher.IslandMediaSessionCoordinator
import com.lidesheng.hyperlyric.root.mediacard.island.switcher.IslandMediaMiniBarIndicator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

/** Plugin-side presentation only; SystemUI owns sessions and independent native card binders. */
internal object IslandMediaCardSwitcherHooker {
    private const val TAG = "IslandMediaCardSwitcher"
    private val watchedWindows = WeakHashMap<View, Boolean>()

    fun hook(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val controller = loader.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
            )
            val publicationNames = setOf("addDynamicIslandView", "updateDynamicIslandView")
            val publications = controller.declaredMethods.filter {
                it.name in publicationNames && it.parameterCount == 2 &&
                    it.parameterTypes[0].name.endsWith(".DynamicIslandData")
            }
            check(publications.map { it.name }.toSet() == publicationNames) {
                "Native island publication interfaces unavailable"
            }
            publications.forEach { method ->
                module.managedHook(method, "media.island_switcher.plugin.preview.${method.name}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val window = chain.thisObject?.let { call(it, "getView") } as? View
                            ?: return chain.proceed()
                        val data = chain.args.firstOrNull() ?: return chain.proceed()
                        val bridge = IslandMediaSessionCoordinator.connect(window, data) ?: return chain.proceed()
                        // Preview controllers bind independent native views. Their generated
                        // payload is consumed directly, never published as an actual island.
                        if (bridge.apply(arrayOf("preview", data)) == true) return null
                        val result = chain.proceed()
                        bridge.apply(arrayOf("published", data))
                        return result
                    }
                })
            }

            val type = loader.loadClass("miui.systemui.dynamicisland.window.DynamicIslandWindowView")
            val findContent = type.getMethod("getViewFromList", String::class.java)
            type.declaredMethods.first { it.name == "destroy" && it.parameterCount == 0 }.let { method ->
                module.managedHook(method, "media.island_switcher.plugin.window_destroy", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let(IslandMediaSessionCoordinator::onWindowDestroyed)
                        return result
                    }
                })
            }
            val pending = type.declaredMethods.first {
                it.name == "hasPendingMedia" && it.parameterCount == 1
            }
            module.managedHook(pending, "media.island_switcher.plugin.pending_media", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val data = chain.args.firstOrNull()
                    // Multi-island cards own different Views; never reuse another card's pending
                    // container. Single-island mode retains the native one-holder transition.
                    val window = chain.thisObject as? View
                    val bridge = window?.let { IslandMediaSessionCoordinator.connect(it, data) }
                    if (bridge?.apply(arrayOf("ready")) == true &&
                        bridge.apply(arrayOf("single")) != true && data != null &&
                        IslandProbeUtils.isMediaIsland(data)
                    ) return false
                    return chain.proceed()
                }
            })
            type.declaredMethods.filter {
                it.name in setOf(
                    "addDynamicIslandData", "updateDynamicIslandView", "handleContentViewAddOperate",
                    "handleContentViewUpdateOperate", "removeDynamicIslandData"
                )
            }.forEach { method ->
                module.managedHook(method, "media.island_switcher.plugin.refresh.${method.name}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        val window = chain.thisObject as? View
                        val data = chain.args.firstOrNull()?.takeIf {
                            it.javaClass.name.endsWith(".DynamicIslandData")
                        }
                        window?.let { IslandMediaSessionCoordinator.connect(it, data) }
                        refreshForWindow(window)
                        return result
                    }
                })
            }
            val base = loader.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
            )
            base.declaredMethods.first {
                it.name == "updateMiniBar" && it.parameterCount == 1
            }.let { method ->
                module.managedHook(method, "media.island_switcher.plugin.mini_bar", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        val real = chain.args.firstOrNull() ?: return result
                        val events = call(real, "getDynamicIslandEventCoordinator") ?: return result
                        val window = call(events, "getWindowView") as? View ?: return result
                        IslandMediaSessionCoordinator.connect(window, IslandProbeUtils.getCurrentIslandData(real))
                        refreshForWindow(window)
                        return result
                    }
                })
            }
            val animations = loader.loadClass("miui.systemui.dynamicisland.anim.DynamicIslandAnimationController")
            animations.declaredMethods.first {
                it.name == "onStateChange" && it.parameterCount == 1
            }.let { method ->
                module.managedHook(method, "media.island_switcher.plugin.presentation", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val real = chain.args.firstOrNull()
                        val data = IslandProbeUtils.getCurrentIslandData(real)
                        val events = call(real, "getDynamicIslandEventCoordinator")
                        val window = events?.let { call(it, "getWindowView") } as? View
                        val bridge = window?.let { IslandMediaSessionCoordinator.connect(it, data) }
                        val state = call(real, "getState")?.javaClass?.simpleName
                        if (real != null && state != null) {
                            bridge?.apply(arrayOf("presentation", real, state))
                        }
                        return chain.proceed()
                    }
                })
            }

            // These event bodies are deliberately hooked instead of tiny setters/dispatch wrappers
            // that R8 can inline into the native click/update callers.
            val events = loader.loadClass("miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator")
            events.declaredMethods.first { it.name == "handleEvent" && it.parameterCount == 2 }.let { method ->
                module.managedHook(method, "media.island_switcher.plugin.event_complete", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        val window = chain.thisObject?.let { call(it, "getWindowView") } as? View
                        val data = IslandProbeUtils.getCurrentIslandData(chain.args.getOrNull(1))
                        val bridge = window?.let { IslandMediaSessionCoordinator.connect(it, data) }
                        if (chain.args.firstOrNull()?.javaClass?.simpleName == "AddDynamicIsland" && data != null) {
                            bridge?.apply(arrayOf("added", data))
                        }
                        refreshForWindow(window)
                        return result
                    }
                })
            }
            val stateMethods = loader.loadClass("miui.systemui.dynamicisland.event.DynamicIslandState").methods
            val setTime = stateMethods.first { it.name == "setTime" && it.parameterCount == 1 }
            val setPostTime = stateMethods.first { it.name == "setPostTime" && it.parameterCount == 1 }
            val setUpdateOrder = stateMethods.first { it.name == "setUpdateOrder" && it.parameterCount == 1 }
            val updates = loader.loadClass("miui.systemui.dynamicisland.event.UpdateEventCoordinator")
            updates.declaredMethods.first { it.name == "handleAppEvent" && it.parameterCount == 3 }.let { method ->
                module.managedHook(method, "media.island_switcher.plugin.activity_order", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        if (chain.args.firstOrNull()?.javaClass?.simpleName != "UpdateDynamicIsland") {
                            return chain.proceed()
                        }
                        val owner = chain.thisObject ?: return chain.proceed()
                        val events = call(owner, "getDynamicIslandEventCoordinator") ?: return chain.proceed()
                        val window = call(events, "getWindowView") as? View ?: return chain.proceed()
                        val content = chain.args.getOrNull(1) ?: return chain.proceed()
                        val data = IslandProbeUtils.getCurrentIslandData(content) ?: return chain.proceed()
                        val key = key(data) ?: return chain.proceed()
                        val bridge = IslandMediaSessionCoordinator.connect(window, data) ?: return chain.proceed()
                        val rank = bridge.apply(arrayOf("rank", key)) as? Long ?: return chain.proceed()
                        val state = call(content, "getState") ?: return chain.proceed()
                        // Native template/metadata updates can rewrite these timestamps even
                        // without a playback transition. Keep the current rank without forcing a reorder.
                        setTime.invoke(state, rank)
                        setPostTime.invoke(state, rank)
                        val ranks = bridge.apply(arrayOf("consumeOrder", key)) as? Map<*, *>
                        var applied = 1
                        ranks?.forEach { (mediaKey, time) ->
                            if (mediaKey == key) return@forEach
                            val card = findContent.invoke(window, mediaKey) ?: return@forEach
                            val other = call(card, "getState") ?: return@forEach
                            setTime.invoke(other, time)
                            setPostTime.invoke(other, time)
                            applied++
                        }
                        if (ranks != null) setUpdateOrder.invoke(state, true)
                        val result = chain.proceed()
                        if (ranks != null) {
                            val winner = IslandProbeUtils.getCurrentIslandData(call(window, "getCurrentBigIslandState"))?.let(::key)
                            HookLogger.d(TAG, "原生媒体岛排序完成: order=${ranks.keys}, applied=$applied/${ranks.size}, first=$winner")
                        }
                        return result
                    }
                })
            }
        }.onFailure { HookLogger.w(TAG, "超级岛媒体卡片展示接口不可用", it) }
    }

    fun canSwitch(window: Any?): Boolean = (window as? View)?.let {
        IslandMediaSessionCoordinator.connect(it)?.apply(arrayOf("single")) == true
    } ?: false

    fun hasAdjacent(window: Any, data: Any, dx: Float): Boolean {
        val view = window as? View ?: return false
        val bridge = IslandMediaSessionCoordinator.connect(view, data) ?: return false
        val page = key(data)?.let { bridge.apply(arrayOf("pages", it)) } as? IntArray ?: return false
        return page[1] + pageStep(view, dx) in 0 until page[0]
    }

    fun switchToAdjacent(windowView: Any, currentData: Any, dx: Float): Boolean {
        val key = key(currentData) ?: return false
        val window = windowView as? View ?: return false
        val bridge = IslandMediaSessionCoordinator.connect(window) ?: return false
        return bridge.apply(arrayOf("switch", key, pageStep(window, dx))) == true
    }

    private fun pageStep(window: View, dx: Float): Int =
        (if (dx < 0f) 1 else -1) * (if (window.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1)

    fun refreshForWindow(windowView: Any?) {
        val view = windowView as? View ?: return
        IslandMediaSessionCoordinator.connect(view) ?: return
        // Native add/update uses coroutines. Refresh after the content is attached and expanded.
        if (watchedWindows.put(view, true) == null) {
            IslandMediaSessionCoordinator.setRefresh(view, Runnable { refresh(view) })
        }
        IslandMediaSessionCoordinator.requestRefresh(view)
    }

    private fun refresh(window: View) {
        val expanded = call(window, "getCurrentExpandedState")?.takeIf {
            call(it, "getState")?.javaClass?.simpleName == "Expanded"
        }
        val nativeData = IslandProbeUtils.getCurrentIslandData(expanded)
        val bridge = IslandMediaSessionCoordinator.connect(window, nativeData)
        if (expanded != null) bridge?.apply(arrayOf("render", expanded))
        val data = IslandProbeUtils.getCurrentIslandData(expanded)
        bridge?.apply(arrayOf("expanded", data?.let(::key)))
        val page = data?.let(::key)?.let { bridge?.apply(arrayOf("pages", it)) } as? IntArray
        IslandMediaMiniBarIndicator.update(window, expanded, page)
    }

    private fun key(data: Any): String? = call(data, "getKey") as? String
    private fun call(target: Any?, name: String): Any? = runCatching {
        target ?: return null
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }
            .apply { isAccessible = true }.invoke(target)
    }.getOrNull()
}
