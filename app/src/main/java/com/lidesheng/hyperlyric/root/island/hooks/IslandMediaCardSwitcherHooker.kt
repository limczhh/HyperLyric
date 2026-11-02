package com.lidesheng.hyperlyric.root.island.hooks

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.mediacard.island.switcher.IslandMediaSessionCoordinator
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaPageIndicator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

/** Plugin-side presentation only; SystemUI owns sessions and independent native card binders. */
internal object IslandMediaCardSwitcherHooker {
    private const val TAG = "IslandMediaCardSwitcher"
    private val watchedWindows = WeakHashMap<View, Boolean>()
    private val indicators = WeakHashMap<View, NotificationMediaPageIndicator>()

    fun prepareForHotReload() {
        watchedWindows.keys.toList().forEach { IslandMediaSessionCoordinator.setRefresh(it, null) }
        watchedWindows.clear()
        indicators.values.toList().forEach(NotificationMediaPageIndicator::detach)
        indicators.clear()
    }

    fun hook(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val type = loader.loadClass("miui.systemui.dynamicisland.window.DynamicIslandWindowView")
            val pending = type.declaredMethods.first {
                it.name == "hasPendingMedia" && it.parameterCount == 1
            }
            module.managedHook(pending, "island.media_switcher.pending_media", object : Hooker {
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
                module.managedHook(method, "island.media_switcher.refresh.${method.name}", object : Hooker {
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
            val events = loader.loadClass("miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator")
            events.declaredMethods.first {
                it.name == "dispatchEvent" && it.parameterCount == 2
            }.let { method ->
                module.managedHook(method, "island.media_switcher.activity_order", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        if (chain.args.firstOrNull()?.javaClass?.simpleName != "UpdateDynamicIsland") {
                            return chain.proceed()
                        }
                        val content = chain.args.getOrNull(1) ?: return chain.proceed()
                        val data = IslandProbeUtils.getCurrentIslandData(content)
                        val extras = data?.let { call(it, "getExtras") } as? Bundle
                        if (extras?.getBoolean(IslandMediaSessionCoordinator.ACTIVITY_ORDER_EXTRA) == true) {
                            extras.remove(IslandMediaSessionCoordinator.ACTIVITY_ORDER_EXTRA)
                            val state = call(content, "getState")
                            if (state != null) {
                                val now = System.currentTimeMillis()
                                // Use native update-order handling on both plugin generations.
                                // Tablets compare postTime; phones compare time, with native
                                // notification priorities still taking precedence on both.
                                mapOf("setTime" to now, "setPostTime" to now, "setUpdateOrder" to true)
                                    .forEach { (name, value) ->
                                        state.javaClass.methods.first {
                                            it.name == name && it.parameterCount == 1
                                        }.invoke(state, value)
                                    }
                            }
                        }
                        return chain.proceed()
                    }
                })
            }

            // Expansion can finish after the add/update coroutine has returned. Watch the native
            // state owner instead of depending on the next touch or media update to mount dots.
            val expandedHandler = loader.loadClass(
                "miui.systemui.dynamicisland.event.handler.ExpandedStateHandler"
            )
            expandedHandler.declaredMethods.first {
                it.name == "setCurrent" && it.parameterCount == 1
            }.let { method ->
                module.managedHook(method, "island.media_switcher.expanded_state", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        watchedWindows.keys.toList().forEach(::refreshForWindow)
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
        return if (dx < 0f) page[1] + 1 < page[0] else page[1] > 0
    }

    fun switchToAdjacent(windowView: Any, currentData: Any, dx: Float): Boolean {
        val key = key(currentData) ?: return false
        val controller = call(windowView, "getWindowViewController") ?: return false
        val bridge = (windowView as? View)?.let(IslandMediaSessionCoordinator::connect) ?: return false
        return bridge.apply(arrayOf("switch", controller, key, dx)) == true
    }

    fun refreshForWindow(windowView: Any?) {
        val view = windowView as? View ?: return
        IslandMediaSessionCoordinator.connect(view) ?: return
        // Native add/update uses coroutines. Refresh after the content is attached and expanded.
        watchedWindows[view] = true
        val refresh = Runnable { refresh(view) }
        IslandMediaSessionCoordinator.setRefresh(view, refresh)
        view.post(refresh)
    }

    private fun refresh(window: View) {
        val expanded = call(window, "getCurrentExpandedState")
        val data = IslandProbeUtils.getCurrentIslandData(expanded)
        val bridge = IslandMediaSessionCoordinator.connect(window, data)
        bridge?.apply(arrayOf("expanded", data?.let(::key)))
        val page = data?.let(::key)?.let { bridge?.apply(arrayOf("pages", it)) } as? IntArray
        val parent = expanded?.let { call(it, "getExpandedView") } as? ViewGroup
        if (page == null || page[0] <= 1 || parent == null) {
            indicators.remove(window)?.detach()
            return
        }
        val player = call(data, "getView") as? View ?: return
        val indicator = indicators.getOrPut(window) {
            // The plugin loader cannot resolve SystemUI's PageIndicator. Use the media player,
            // which is inflated by SystemUI and retains that component's class loader.
            NotificationMediaPageIndicator(player.javaClass.classLoader, player.context)
        }
        indicator.attachTo(parent)
        indicator.update(page[0], page[1], enabled = true)
        HookLogger.dState(
            stateId = "$TAG.indicator", tag = TAG, state = "${page[0]}/${page[1]}",
        ) { "单岛指示器更新: pages=${page[0]}, selected=${page[1]}" }
    }

    private fun key(data: Any): String? = call(data, "getKey") as? String
    private fun call(target: Any, name: String): Any? = runCatching {
        target.javaClass.methods.first { it.name == name && it.parameterCount == 0 }
            .apply { isAccessible = true }.invoke(target)
    }.getOrNull()
}
