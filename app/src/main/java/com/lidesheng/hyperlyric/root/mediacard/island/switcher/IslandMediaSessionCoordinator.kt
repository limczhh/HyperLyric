package com.lidesheng.hyperlyric.root.mediacard.island.switcher

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaDataAccessor
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaNativeFactory
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaPlaybackObserver
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaPlaybackPolicy
import com.lidesheng.hyperlyric.root.mediacard.notification.switcher.NotificationMediaSelectionCoordinator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.WeakHashMap
import java.util.Collections
import java.util.function.Function

/** Tracks sessions; single-island browsing owns binders, multi-island mode owns controllers. */
internal object IslandMediaSessionCoordinator {
    private const val TAG = "IslandMediaCardSwitcher"
    private const val PREFIX = "com.android.systemui.statusbar.notification.mediaisland."
    private val main = Handler(Looper.getMainLooper())
    private val constructingCard = ThreadLocal<Boolean>()
    private val ownedControllers = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val binderTemplates = WeakHashMap<Any, Pair<Constructor<*>, List<Any?>>>()
    private var runtime: Runtime? = null
    // Boot-class interfaces and native View tags keep the persistent media runtime reachable
    // when lyric/plugin hooks are replaced by a new module class loader during hot reload.
    private const val BRIDGE_TAG = 0x7e484c01
    private const val REFRESH_TAG = 0x7e484c02
    private const val RETIRED_WINDOW_TAG = 0x7e484c04
    private val windows = WeakHashMap<View, Boolean>()
    private val bridge = Function<Array<Any?>, Any?> { args ->
        when (args[0]) {
            "ready" -> isRunning()
            "single" -> runtime?.let { !it.failed && !it.multi } == true
            "pages" -> pageState(args[1] as String)?.let { intArrayOf(it.first, it.second) }
            "switch" -> switchRelative(args[1] as String, args[2] as Int)
            "render" -> { runtime?.let { state -> state.guard { state.renderSingle(args[1]!!) } }; null }
            "rank" -> runtime?.nativeRank(args[1] as String)
            "consumeOrder" -> runtime?.takeNativeOrder(args[1] as String)
            "added" -> { runtime?.let { state -> state.guard { state.onNativeAdded(args[1]!!) } }; null }
            "published" -> { runtime?.onNativePublished(args[1]!!); null }
            "watch" -> {
                val window = args[1] as View
                if (runtime?.acceptsWindow(window) == true) {
                    windows[window] = true
                    true
                } else false
            }
            "expanded" -> { runtime?.onExpandedChanged(args[1] as? String); null }
            "presentation" -> { runtime?.onPresentationChanged(args[1]!!, args[2] as String); null }
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun connect(window: View, data: Any? = null): Function<Array<Any?>, Any?>? {
        val player = data?.let { value ->
            value.javaClass.methods.firstOrNull { it.name == "getView" && it.parameterCount == 0 }?.invoke(value)
        } as? View
        val hostBridge = player?.getTag(BRIDGE_TAG) ?: window.getTag(BRIDGE_TAG)
        val function = hostBridge as? Function<Array<Any?>, Any?> ?: return null
        if (function.apply(arrayOf("watch", window)) != true) return null
        window.setTag(BRIDGE_TAG, function)
        return function
    }

    fun setRefresh(window: View, refresh: Runnable?) {
        (window.getTag(REFRESH_TAG) as? Runnable)?.let(window::removeCallbacks)
        window.setTag(REFRESH_TAG, refresh)
    }
    fun requestRefresh(window: View) {
        (window.getTag(REFRESH_TAG) as? Runnable)?.let {
            window.removeCallbacks(it)
            window.post(it)
        }
    }

    private fun notifyPagesChanged() = windows.keys.toList().forEach(::requestRefresh)

    fun onWindowDestroyed(window: View) {
        val tracked = windows.remove(window) != null
        window.setTag(RETIRED_WINDOW_TAG, true)
        setRefresh(window, null)
        IslandMediaMiniBarIndicator.update(window, null, null)
        if (tracked) runtime?.let { state -> state.guard { state.onWindowDestroyed() } }
    }

    fun hook(module: XposedModule, loader: ClassLoader) {
        if (!MediaCardRuntimeConfig.current.islandExpanded.cardSwitcherEnabled) return
        runCatching {
            val controller = loader.loadClass(PREFIX + "MiuiIslandMediaControllerImpl")
            val binder = loader.loadClass(PREFIX + "MiuiIslandMediaViewBinderImpl")
            val plugin = loader.loadClass("com.android.systemui.statusbar.notification.DynamicIslandPluginController")
            plugin.declaredMethods.filter {
                it.name in setOf("onPluginLoaded", "onPluginUnloaded") && !it.isBridge
            }.forEach { method ->
                module.managedHook(method, "media.island_switcher.plugin_lifecycle.${method.name}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        val owner = chain.thisObject ?: return result
                        runOnMain { runtime?.let { state -> state.guard { state.onPluginChanged(owner) } } }
                        return result
                    }
                })
            }
            binder.declaredConstructors.forEach { constructor ->
                module.managedHook(constructor, "media.island_switcher.binder_template", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        if (constructingCard.get() != true) chain.thisObject?.let {
                            synchronized(binderTemplates) {
                                binderTemplates[it] = constructor to chain.args.toList()
                            }
                        }
                        return result
                    }
                })
            }
            controller.declaredMethods.filter {
                it.name == "addDynamicIslandView" && it.parameterCount == 3
            }.forEach { method ->
                module.managedHook(method, "media.island_switcher.card_publish", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val instance = chain.thisObject ?: return chain.proceed()
                        val state = runtime
                        if (instance in ownedControllers ||
                            state?.multi == false && state.original === chain.thisObject
                        ) {
                            chain.args.take(2).forEach { holder ->
                                (holder?.let { read(it, "player") } as? View)?.let { player ->
                                    player.setTag(BRIDGE_TAG, bridge)
                                }
                            }
                        }
                        val rebuilding = instance in ownedControllers && read(instance, "currentKey") == null
                        if (rebuilding) {
                            // Native reinflation clears currentKey and rejects paused sessions on
                            // its add path. Owned multi-island cards use the native update path.
                            field(instance, "currentKey").set(instance, chain.args[2]?.let { read(it, "notificationKey") })
                        }
                        val result = chain.proceed()
                        if (rebuilding) state?.guard { state.onNativeRebuilt(instance) }
                        return result
                    }
                })
            }
            controller.declaredConstructors.forEach { constructor ->
                module.managedHook(constructor, "media.island_switcher.source", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        if (constructingCard.get() == true) return chain.proceed()
                        val args = chain.args.toList()
                        val notification = args.firstOrNull {
                            it?.javaClass?.simpleName == "MiuiMediaNotificationControllerImpl"
                        }
                        @Suppress("UNCHECKED_CAST")
                        val listeners = notification?.let { read(it, "topMediaListeners") } as? MutableSet<Any>
                        val previous = listeners?.toSet().orEmpty()
                        val result = chain.proceed()
                        val original = chain.thisObject ?: return result
                        if (notification != null && listeners != null) {
                            val nativeListener = (listeners - previous).singleOrNull()
                            if (nativeListener != null) runCatching {
                                val state = Runtime(original, constructor, args, notification, nativeListener)
                                state.start()
                                runtime = state
                                if (state.multi) listeners.remove(nativeListener)
                                listOf("miuiPlayerHolder", "miuiDummyPlayerHolder").forEach { name ->
                                    (read(original, name)?.let { read(it, "player") } as? View)?.setTag(BRIDGE_TAG, bridge)
                                }
                                HookLogger.d(TAG, "媒体会话观察已启动: mode=${state.multi}, nativeLifecycle=${!state.multi}")
                            }.onFailure { HookLogger.w(TAG, "超级岛媒体会话接管失败，保留原生行为", it) }
                        }
                        return result
                    }
                })
            }
        }.onFailure { HookLogger.w(TAG, "超级岛媒体会话接口不可用", it) }
    }

    private fun isRunning(): Boolean = runtime?.failed == false

    private fun pageState(currentKey: String): Pair<Int, Int>? {
        val state = runtime ?: return null
        if (state.multi || state.failed) return null
        val keys = state.pages().map { it.first }
        if (state.nativeKey() != currentKey) return null
        val selectedKey = state.singleSelection.selectedKey(keys)
        val index = keys.indexOf(selectedKey)
        return if (index >= 0) keys.size to index else null
    }

    private fun switchRelative(currentKey: String, step: Int): Boolean {
        val state = runtime ?: return false
        if (state.multi || state.failed) return false
        val pages = state.pages()
        if (state.nativeKey() != currentKey) return false
        val selectedKey = state.singleSelection.selectedKey(pages.map { it.first })
        val index = pages.indexOfFirst { it.first == selectedKey }
        if (index < 0) return false
        val target = (index + step).coerceIn(0, pages.lastIndex)
        if (target == index) return false
        return state.guard {
            state.singleSelection.select(pages[target].first)
            state.sync()
            HookLogger.d(TAG, "单岛切换媒体会话: from=$selectedKey, to=${pages[target].first}")
        }
    }

    private class Runtime(
        val original: Any,
        val constructor: Constructor<*>,
        val arguments: List<Any?>,
        val notification: Any,
        val nativeListener: Any,
    ) {
        val multi = MediaCardRuntimeConfig.current.islandExpanded.cardSwitcherMode ==
            RootConstants.ISLAND_MEDIA_CARD_SWITCHER_MODE_MULTI
        private val limit = MediaCardRuntimeConfig.current.islandExpanded.cardSwitcherMaxCount
        private val manager = required(notification, "mediaDataManager")
        private val plugin = required(original, "dynamicIslandPluginController")
        private var pluginContent: Any? = null
        private var nativePublishedContent: Any? = null
        private val accessor = Accessor()
        private val sort = read(notification, "mediaSorUtils") ?: read(notification, "mediaSortUtils")
        private val policy = NotificationMediaPlaybackPolicy(accessor)
        private val entries = LinkedHashMap<String, Any>()
        private val observers = LinkedHashMap<String, Pair<MediaSession.Token, NotificationMediaPlaybackObserver>>()
        val cards = LinkedHashMap<String, Card>()
        val selection = NotificationMediaSelectionCoordinator(accessor,
            { policy.orderedKeys(nativeOrder()) }, {}, limit)
        val singleSelection = IslandMediaSingleSelection()
        private val binder = required(original, "dynamicMediaViewBinder")
        private val binderTemplate = synchronized(binderTemplates) { binderTemplates[binder] }
            ?: error("Island binder constructor unavailable")
        private val factory = NotificationMediaNativeFactory(notification, binder) {
            error("Island holders must use the native factory")
        }
        private var nativeOrderRanks = emptyMap<String, Long>()
        private var orderTrigger: String? = null
        private var publishedOrder = emptyList<String>()
        private var orderDirty = true
        private var expandedKey: String? = null
        private var listener: Any? = null
        var failed = false
            private set
        private val singlePresenter = IslandMediaSinglePresenter { action -> guard(action) }
        private var singleCard: IslandMediaBrowsingCard? = null
        private val dirtyKeys = linkedSetOf<String>()
        private var seeded = false
        private var syncScheduled = false
        private var lastOrderTime = 0L
        private val syncTask = Runnable {
            syncScheduled = false
            val changed = dirtyKeys.toSet()
            dirtyKeys.clear()
            guard {
                if (pluginContent == null) onPluginChanged(plugin)
                sync(changed)
            }
        }

        private fun requestSync(changedKey: String? = null) {
            if (failed) return
            changedKey?.let(dirtyKeys::add)
            if (!seeded || syncScheduled) return
            syncScheduled = true
            main.post(syncTask)
        }

        fun start() {
            method(manager, "removeListener", 1)
            val add = method(manager, "addListener", 1)
            val listenerType = add.parameterTypes.single()
            check(listenerType.isInterface)
            val proxy = Proxy.newProxyInstance(listenerType.classLoader, arrayOf(listenerType)) { proxy, m, args ->
                when (m.name) {
                    "onMediaDataLoaded" -> {
                        val key = args?.getOrNull(0) as? String
                        val oldKey = args?.getOrNull(1) as? String
                        val data = args?.getOrNull(2)
                        if (key != null && data != null) runOnMain {
                            guard { update(key, oldKey, data) }
                        }
                        null
                    }
                    "onMediaDataRemoved" -> {
                        (args?.getOrNull(0) as? String)?.let { key -> runOnMain { guard { remove(key); requestSync() } } }
                        null
                    }
                    "equals" -> proxy === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "$TAG.MediaDataListener"
                    else -> null
                }
            }
            add.invoke(manager, proxy)
            listener = proxy
            // Seed only after construction returns, on the same thread as MediaData callbacks.
            main.post {
                guard {
                    val sorted = sort?.let { read(it, "mediaDataList") } as? Iterable<*>
                    sorted?.forEach { value ->
                        val data = value?.let(accessor::sortData) ?: return@forEach
                        val key = accessor.sortKey(value) ?: return@forEach
                        if (accessor.isActive(data)) entries.putIfAbsent(key, data)
                    }
                    if (entries.isEmpty()) {
                        (read(manager, "mediaEntries") as? Map<*, *>)?.forEach { (key, data) ->
                            if (key is String && data != null && accessor.isActive(data)) entries[key] = data
                        }
                    }
                    val initial = (nativeOrder() + entries.keys).distinct().mapNotNull { key ->
                        entries[key]?.let { key to it }
                    }
                    policy.seed(initial)
                    selection.seed(initial)
                    entries.forEach { (key, data) -> observe(key, data) }
                    selection.onActivityOrderChanged()
                    seeded = true
                    onPluginChanged(plugin)
                    requestSync()
                }
            }
        }

        fun acceptsWindow(window: View): Boolean {
            if (window.getTag(RETIRED_WINDOW_TAG) == true) return false
            val content = readPluginContent(plugin) ?: return false
            return content.javaClass.classLoader === window.javaClass.classLoader
        }

        fun onPluginChanged(owner: Any) {
            if (owner !== plugin) return
            val content = readPluginContent(plugin)
            if (content === pluginContent) return
            // The old plugin has already been unloaded. Release the saved projection
            // without calling back into its destroyed event/animation coordinator.
            singlePresenter.forgetAll()
            singleSelection.setExpanded(false)
            releaseSingleCard()
            windows.keys.toList().filterNot(::acceptsWindow).forEach { window ->
                setRefresh(window, null)
                window.setTag(BRIDGE_TAG, null)
                windows.remove(window)
            }
            IslandMediaMiniBarIndicator.clear()
            pluginContent = content
            if (content == null) {
                nativePublishedContent = null
                cards.values.forEach { it.publishedContent = null }
                return
            }
            if (multi) {
                orderDirty = true
                entries.keys.forEach(dirtyKeys::add)
            } else if (nativeKey() != null && nativePublishedContent !== content) {
                // currentKey is set only after the stock playing gate has accepted an island.
                // Replay that payload with Add: OS 4's lazy proxy drops Update until a window exists.
                read(original, "dynamicIslandData")?.let { data ->
                    method(content, "addDynamicIslandView", 2).invoke(content, data, false)
                }
            }
            requestSync()
            HookLogger.d(TAG, "超级岛插件已就绪，重新同步媒体会话: mode=${if (multi) "multi" else "single"}")
        }

        private fun update(key: String, oldKey: String?, data: Any) {
            if (failed) return
            if (oldKey != null && oldKey != key) {
                singleSelection.replaceKey(oldKey, key)
                remove(oldKey)
            }
            policy.onMediaDataLoaded(key, oldKey, data)
            selection.onMediaDataLoaded(key, oldKey, data)
            if (accessor.isActive(data)) {
                entries[key] = data
                observe(key, data)
            } else remove(key)
            selection.onActivityOrderChanged()
            requestSync(key)
        }

        private fun observe(key: String, data: Any) {
            val token = accessor.sessionToken(data) as? MediaSession.Token
            if (observers[key]?.first == token) return
            observers.remove(key)?.second?.clear()
            token ?: return
            val controller = MediaController(required(original, "context") as Context, token)
            val observer = NotificationMediaPlaybackObserver({}, { _, playing ->
                runOnMain {
                    guard {
                        policy.onPlaybackSignal(key, playing)
                        selection.onActivityOrderChanged()
                        requestSync()
                    }
                }
            })
            observers[key] = token to observer
            val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            policy.onPlaybackSignal(key, playing)
            observer.bind(controller)
        }

        private fun remove(key: String) {
            entries.remove(key)
            nativeOrderRanks = nativeOrderRanks - key
            observers.remove(key)?.second?.clear()
            policy.onMediaDataRemoved(key)
            selection.onMediaDataRemoved(key)
            // Release removed sessions immediately; queued content updates must not keep an
            // obsolete controller eligible for reinflation before the next reconciliation.
            if (multi) cards.remove(key)?.close()
            else if (singleCard?.key == key) releaseSingleCard()
        }

        fun nativeRank(key: String): Long? = if (multi && !failed) nativeOrderRanks[key] else null

        fun takeNativeOrder(key: String): Map<String, Long>? {
            if (failed || !multi || key != orderTrigger) return null
            orderTrigger = null
            return nativeOrderRanks
        }

        fun onNativePublished(data: Any) {
            if (failed) return
            val content = readPluginContent(plugin) ?: return
            if (multi) {
                val key = method(data, "getKey", 0).invoke(data) as? String ?: return
                cards[key]?.takeIf { it.data() === data }?.publishedContent = content
            } else if (read(original, "dynamicIslandData") === data) {
                nativePublishedContent = content
                singleCard?.let { card ->
                    val player = read(original, "miuiPlayerHolder")?.let { read(it, "player") }
                    if (player !== card.templatePlayer) requestSync(card.key)
                }
            }
        }

        fun onWindowDestroyed() {
            pluginContent = null
            nativePublishedContent = null
            singlePresenter.forgetAll()
            singleSelection.setExpanded(false)
            if (multi) {
                cards.values.forEach { it.publishedContent = null }
                orderDirty = true
            } else releaseSingleCard()
            requestSync()
        }

        fun onNativeAdded(data: Any) {
            if (!multi || failed) return
            val key = method(data, "getKey", 0).invoke(data) as? String ?: return
            if (cards[key]?.data() !== data) return
            // Older plugins dispatch Add at pre-draw; apply ranks again only once the
            // actual native content exists, rather than guessing a delay or polling.
            orderDirty = true
            requestSync()
        }

        fun onNativeRebuilt(controller: Any) {
            if (!multi || failed) return
            val card = cards.values.firstOrNull { it.controller === controller } ?: return
            orderDirty = true
            requestSync()
            HookLogger.d(TAG, "原生媒体岛内容已重建: key=${card.key}")
        }

        fun onExpandedChanged(key: String?) {
            if (expandedKey == key) return
            val collapsed = expandedKey != null && key == null
            expandedKey = key
            // Release the pin at collapse, then let the native state machine restore the activity order.
            if (multi) {
                if (collapsed) orderDirty = true
                requestSync()
            }
        }

        fun nativeKey(): String? = read(original, "currentKey") as? String

        private fun nativeOrder(): List<String> = (sort?.let { read(it, "mediaDataList") } as? Iterable<*>)
            ?.mapNotNull { it?.let(accessor::sortKey) }.orEmpty()

        fun pages(): List<Pair<String, Any>> {
            if (multi) return selection.snapshot(limit).entries
            val all = selection.snapshot().entries
            val selected = singleSelection.selectedKey(all.map { it.first })
            val priority = linkedSetOf<String>()
            selected?.let(priority::add)
            all.forEach { priority += it.first }
            val kept = priority.take(limit).toSet()
            return all.filter { it.first in kept }
        }

        fun renderSingle(real: Any) {
            if (multi) return
            val current = method(real, "getCurrentIslandData", 0).invoke(real)
            val key = current?.let { method(it, "getKey", 0).invoke(it) }
            if (key != nativeKey()) {
                singlePresenter.restore(real)
                return
            }
            if (method(real, "getState", 0).invoke(real)?.javaClass?.simpleName == "Expanded") {
                singleSelection.setExpanded(true)
            }
            val preview = singleCard?.content
            singlePresenter.render(real, preview)
        }

        fun resetSingle() {
            if (multi) return
            singleSelection.setExpanded(false)
            releaseSingleCard()
        }

        fun onPresentationChanged(real: Any, state: String) {
            if (failed) return
            val current = method(real, "getCurrentIslandData", 0).invoke(real)
            val key = current?.let { method(it, "getKey", 0).invoke(it) } as? String
            val collapsed = when (state) {
                "BigIsland", "SmallIsland", "ShowOnceBigIsland", "Hidden", "Deleted" -> true
                else -> false
            }
            if (multi) {
                if (key !in cards) return
                if (state == "Expanded") onExpandedChanged(key)
                else if (collapsed && key == expandedKey) onExpandedChanged(null)
                return
            }
            if (key != nativeKey() && !singlePresenter.owns(real)) return
            if (state == "Expanded") {
                singleSelection.setExpanded(true)
            } else if (collapsed) {
                // Restore identity now; the presenter releases Views after the native transition.
                guard { resetSingle() }
            }
            // App/MiniWindow transitions keep the browsed intent until native launch completes.
        }

        private fun syncSingle(changed: Set<String>) {
            val pages = pages()
            val selected = singleSelection.selectedKey(pages.map { it.first })
            val nativeOnly = pages.size == 1 && selected == nativeKey()
            // Opening uses the already-bound native card. Inflate/bind a browsing card only
            // after a swipe, then retain it when swiping back for symmetric cover animations.
            if (!singleSelection.isBrowsing || selected == null || nativeOnly) {
                releaseSingleCard()
            } else {
                val data = pages.first { it.first == selected }.second
                val holder = required(original, "miuiPlayerHolder")
                if (singleCard?.templatePlayer !== required(holder, "player")) releaseSingleCard()
                val card = singleCard ?: createBrowsingCard().also { singleCard = it }
                if (card.key != selected || selected in changed) card.bind(selected, data)
            }
            HookLogger.dState(
                stateId = "$TAG.single", tag = TAG,
                state = "${nativeKey()}/$selected/${pages.map { it.first }}",
            ) {
                "单岛展开会话更新: native=${nativeKey()}, selected=$selected, pages=${pages.map { it.first }}"
            }
            notifyPagesChanged()
        }

        fun sync(changed: Set<String> = emptySet()) {
            if (failed) return
            if (!multi) {
                syncSingle(changed)
                return
            }
            val snapshot = selection.snapshot(Int.MAX_VALUE)
            val priority = linkedSetOf<String>()
            expandedKey?.takeIf(entries::containsKey)?.let(priority::add)
            snapshot.entries.forEach { priority += it.first }
            val kept = priority.take(limit).toSet()
            val visible = snapshot.entries.filter { it.first in kept }
            val next = visible.associate { (key, data) -> key to data }
            cards.keys.filter { it !in next }.toList().forEach { cards.remove(it)?.close() }
            next.forEach { (key, data) ->
                val card = cards.getOrPut(key) { createCard() }
                if (card.key != key || key in changed) card.bind(key, data)
                card.ensurePublished()
            }
            val order = visible.map { it.first }
            if (orderDirty || order != publishedOrder) {
                lastOrderTime = maxOf(System.currentTimeMillis(), lastOrderTime) + order.size
                nativeOrderRanks = order.withIndex().associate { (index, key) -> key to lastOrderTime - index }
                orderTrigger = order.firstOrNull()
                // Finish native Add/bind work before one Update reconciles the complete media order.
                if (order.isEmpty() || cards.getValue(order.first()).publish()) {
                    publishedOrder = order
                    orderDirty = false
                }
            }
            HookLogger.dState(
                stateId = "$TAG.pages", tag = TAG,
                state = "${visible.map { it.first }}/${snapshot.selectedKey}/${entries.size}",
            ) {
                "媒体岛展示更新: mode=multi, " +
                    "sessions=${entries.size}, pages=${visible.map { it.first }}, " +
                    "selected=${snapshot.selectedKey}, max=$limit"
            }
            notifyPagesChanged()
        }

        fun guard(action: () -> Unit): Boolean {
            if (failed) return false
            return runCatching(action).onFailure { error ->
                failed = true
                main.removeCallbacks(syncTask)
                syncScheduled = false
                dirtyKeys.clear()
                listener?.let { runCatching { method(manager, "removeListener", 1).invoke(manager, it) } }
                runCatching { singlePresenter.restoreAll() }
                runCatching { releaseSingleCard() }
                cards.values.toList().forEach { runCatching { it.close() } }
                cards.clear()
                observers.values.forEach { it.second.clear() }
                observers.clear()
                if (multi) {
                    @Suppress("UNCHECKED_CAST")
                    (required(notification, "topMediaListeners") as MutableSet<Any>).add(nativeListener)
                    val top = read(notification, "topMediaData")
                    runCatching { method(nativeListener, "onMediaDataChanged", 1).invoke(nativeListener, top) }
                }
                HookLogger.w(TAG, "超级岛媒体卡片更新失败，恢复原生展示", error)
            }.isSuccess
        }

        private fun releaseSingleCard() {
            val card = singleCard ?: return
            singleCard = null
            singlePresenter.release(card.content)
        }

        private fun createBrowsingCard(): IslandMediaBrowsingCard {
            constructingCard.set(true)
            var native: NotificationMediaNativeFactory.Controller? = null
            try {
                native = factory.createIslandBinder(binderTemplate.first, binderTemplate.second)
                val context = required(original, "context") as Context
                val config = read(original, "notificationConfigManager")
                fun holder(field: String, layout: String): Any {
                    val template = required(original, field)
                    val id = context.resources.getIdentifier(layout, "layout", context.packageName)
                    check(id != 0) { "Native media island layout unavailable: $layout" }
                    return factory.createIslandHolder(template, context, id, config)
                }
                val real = holder("miuiPlayerHolder", "miui_media_session_island")
                val dummy = holder("miuiDummyPlayerHolder", "miui_media_session_island_dummy")
                val player = required(real, "player") as View
                val fake = required(dummy, "player") as View
                val layout = required(original, "normalLayoutIsland")
                val apply = layout.javaClass.methods.first {
                    it.name == "applyTo" && it.parameterCount == 1 && it.parameterTypes[0].isInstance(player)
                }
                listOf(player, fake).forEach {
                    apply.invoke(layout, it)
                    it.setTag(BRIDGE_TAG, bridge)
                }
                method(native.instance, "attach", 2).invoke(native.instance, real, dummy)
                val templatePlayer = required(required(original, "miuiPlayerHolder"), "player") as View
                HookLogger.d(TAG, "单岛浏览绑定器已创建，保留原生媒体岛控制器")
                return IslandMediaBrowsingCard(native, templatePlayer, player, fake)
            } catch (error: Throwable) {
                native?.let {
                    if (read(it.instance, "holder") != null) {
                        runCatching { method(it.instance, "detach", 0).invoke(it.instance) }
                    }
                    it.close()
                }
                throw error
            } finally {
                constructingCard.remove()
            }
        }

        private fun createCard(): Card {
            constructingCard.set(true)
            val registrations = mutableListOf<() -> Unit>()
            var native: NotificationMediaNativeFactory.Controller? = null
            @Suppress("UNCHECKED_CAST")
            val topListeners = required(notification, "topMediaListeners") as MutableSet<Any>
            val before = topListeners.toSet()
            try {
                native = factory.createIslandBinder(binderTemplate.first, binderTemplate.second)
                val args = arguments.toMutableList()
                constructor.parameterTypes.forEachIndexed { index, type ->
                    when {
                        type.isInstance(binder) -> args[index] = native.instance
                        type.isInterface && type.simpleName in setOf("ConfigurationController", "StatusBarStateController") -> {
                            val delegate = requireNotNull(args[index])
                            args[index] = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, values ->
                                val result = m.invoke(delegate, *(values ?: emptyArray()))
                                if (m.name == "addCallback" && values?.size == 1) {
                                    val callback = values[0]
                                    registrations += { method(delegate, "removeCallback", 1).invoke(delegate, callback) }
                                }
                                result
                            }
                        }
                    }
                }
                constructor.isAccessible = true
                val controller = constructor.newInstance(*args.toTypedArray())
                val callback = (topListeners - before).single()
                topListeners.remove(callback)
                ownedControllers.add(controller)
                method(controller, "resetPlayerHolder", 0).invoke(controller)
                listOf("miuiPlayerHolder", "miuiDummyPlayerHolder").forEach { name ->
                    val holder = required(controller, name)
                    (required(holder, "player") as View).setTag(BRIDGE_TAG, bridge)
                }
                return Card(controller, callback, native, registrations)
            } catch (error: Throwable) {
                registrations.asReversed().forEach { runCatching(it) }
                native?.close()
                throw error
            } finally {
                topListeners.removeAll(topListeners - before)
                constructingCard.remove()
            }
        }
    }

    private class Card(
        val controller: Any,
        private val callback: Any,
        private val native: NotificationMediaNativeFactory.Controller,
        private val registrations: List<() -> Unit>,
    ) {
        var key: String? = null
            private set
        var publishedContent: Any? = null
        fun data(): Any? = read(controller, "dynamicIslandData")
        fun bind(nextKey: String, data: Any) {
            native.prepareBind(data)
            val previous = key
            if (previous != nextKey) {
                previous?.let { old ->
                    method(controller, "removeDynamicIslandView", 2).invoke(controller, old, true)
                }
                field(controller, "currentKey").set(controller, nextKey)
            }
            key = nextKey
            method(callback, "onMediaDataChanged", 1).invoke(callback, data)
        }
        fun ensurePublished() {
            val content = readPluginContent(required(controller, "dynamicIslandPluginController")) ?: return
            if (publishedContent === content) return
            val payload = data() ?: return
            method(content, "addDynamicIslandView", 2).invoke(content, payload, false)
        }

        fun publish(): Boolean {
            val payload = data() ?: return false
            val content = readPluginContent(required(controller, "dynamicIslandPluginController")) ?: return false
            // Let the plugin's normal update path refresh time and run its priority/state machine.
            method(content, "updateDynamicIslandView", 2).invoke(content, payload, false)
            return true
        }
        fun close() {
            try {
                method(callback, "onMediaDataChanged", 1).invoke(callback, null)
            } finally {
                // A failure during the first attach can precede topMediaData assignment, in
                // which case the native null callback has nothing to detach yet.
                (read(controller, "currentKey") as? String)?.let { remainingKey ->
                    runCatching {
                        method(controller, "removeDynamicIslandView", 2).invoke(controller, remainingKey, true)
                    }.onFailure { HookLogger.w(TAG, "清理未完成绑定的媒体岛失败", it) }
                }
                if (read(native.instance, "holder") != null) {
                    runCatching { method(native.instance, "detach", 0).invoke(native.instance) }
                        .onFailure { HookLogger.w(TAG, "解除媒体卡片绑定失败", it) }
                }
                registrations.asReversed().forEach { unregister ->
                    runCatching(unregister).onFailure { HookLogger.w(TAG, "注销媒体卡片回调失败", it) }
                }
                ownedControllers.remove(controller)
                native.close()
            }
        }
    }

    private class Accessor : NotificationMediaDataAccessor {
        override fun notificationKey(data: Any) = read(data, "notificationKey") as? String
        override fun sessionToken(data: Any) = read(data, "token")
        override fun isActive(data: Any) = read(data, "active") as? Boolean ?: true
        override fun isPlaying(data: Any) = read(data, "isPlaying") as? Boolean
        override fun sortKey(sortKey: Any) = read(sortKey, "key") as? String
        override fun sortData(sortKey: Any) = read(sortKey, "data")
    }

    private fun readPluginContent(plugin: Any): Any? = plugin.javaClass.methods.first {
        it.name.startsWith("getContent") && it.parameterCount == 0
    }.invoke(plugin)

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }
    private fun field(target: Any, name: String) = generateSequence(target.javaClass) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }.first { it.name == name }.apply { isAccessible = true }
    private fun read(target: Any, name: String): Any? = runCatching { field(target, name).get(target) }.getOrNull()
    private fun required(target: Any, name: String) = read(target, name)
        ?: error("${target.javaClass.simpleName}.$name unavailable")
    private fun method(target: Any, name: String, count: Int): Method = generateSequence(target.javaClass) { it.superclass }
        .flatMap { it.declaredMethods.asSequence() }.first { (it.name == name || name == "removeDynamicIslandView" && it.name.startsWith("removeDynamicIslandView\$")) && it.parameterCount == count }
        .apply { isAccessible = true }
}
