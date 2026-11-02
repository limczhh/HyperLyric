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
import java.util.function.Function

/** Owns the media source and native card lifetimes in SystemUI, independently of the plugin. */
internal object IslandMediaSessionCoordinator {
    private const val TAG = "IslandMediaCardSwitcher"
    private const val PREFIX = "com.android.systemui.statusbar.notification.mediaisland."
    private val main = Handler(Looper.getMainLooper())
    private val constructingCard = ThreadLocal<Boolean>()
    private val ownedControllers = WeakHashMap<Any, String?>()
    private val binderTemplates = WeakHashMap<Any, Pair<Constructor<*>, List<Any?>>>()
    private var runtime: Runtime? = null
    // Boot-class interfaces and native View tags keep the persistent media runtime reachable
    // when lyric/plugin hooks are replaced by a new module class loader during hot reload.
    private const val BRIDGE_TAG = 0x7e484c01
    private const val REFRESH_TAG = 0x7e484c02
    private val windows = WeakHashMap<View, Boolean>()
    private val bridge = Function<Array<Any?>, Any?> { args ->
        when (args[0]) {
            "ready" -> isRunning()
            "single" -> runtime?.let { !it.failed && !it.multi } == true
            "pages" -> pageState(args[1] as String)?.let { intArrayOf(it.first, it.second) }
            "switch" -> switchRelative(args[2] as String, args[3] as Float)
            "prepareUpdate" -> runtime?.prepareUpdate(args[1]!!, args[2]!!, args[3] as Boolean)
            "consumePromotion" -> runtime?.nativePromotions?.remove(args[1] as String)
            "watch" -> { windows[args[1] as View] = true; null }
            "expanded" -> { runtime?.expandedKey = args[1] as? String; null }
            "presentation" -> { runtime?.onPresentationChanged(args[1] as String, args[2] as String); null }
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
        window.setTag(BRIDGE_TAG, function)
        function.apply(arrayOf("watch", window))
        return function
    }

    fun setRefresh(window: View, refresh: Runnable?) {
        (window.getTag(REFRESH_TAG) as? Runnable)?.let(window::removeCallbacks)
        window.setTag(REFRESH_TAG, refresh)
    }
    private fun notifyPagesChanged() {
        windows.keys.toList().forEach { window ->
            (window.getTag(REFRESH_TAG) as? Runnable)?.let(window::post)
        }
    }

    fun hook(module: XposedModule, loader: ClassLoader) {
        if (!MediaCardRuntimeConfig.current.islandExpanded.cardSwitcherEnabled) return
        runCatching {
            val controller = loader.loadClass(PREFIX + "MiuiIslandMediaControllerImpl")
            val binder = loader.loadClass(PREFIX + "MiuiIslandMediaViewBinderImpl")
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
                        if (ownedControllers.containsKey(chain.thisObject)) {
                            chain.args.take(2).forEach { holder ->
                                (holder?.let { read(it, "player") } as? View)?.setTag(BRIDGE_TAG, bridge)
                            }
                        }
                        return chain.proceed()
                    }
                })
            }
            controller.declaredMethods.filter {
                it.name.startsWith("removeDynamicIslandView") && it.parameterCount == 2 &&
                    it.parameterTypes[0] == String::class.java
            }.forEach { method ->
                module.managedHook(method, "media.island_switcher.remove_key", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val displayKey = ownedControllers[chain.thisObject] ?: return chain.proceed()
                        val args = chain.args.toTypedArray()
                        args[0] = displayKey
                        return chain.proceed(args)
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
                                listeners.remove(nativeListener)
                                HookLogger.d(TAG, "超级岛媒体会话接管完成: mode=${state.multi}")
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
        val card = state.cards["single"] ?: return null
        if (card.islandKey != currentKey) return null
        val selectedKey = card.key
        val index = keys.indexOf(selectedKey)
        return if (index >= 0) keys.size to index else null
    }

    private fun switchRelative(currentKey: String, dx: Float): Boolean {
        val state = runtime ?: return false
        if (state.multi || state.failed) return false
        val pages = state.pages()
        val card = state.cards["single"] ?: return false
        if (card.islandKey != currentKey) return false
        val selectedKey = card.key
        val index = pages.indexOfFirst { it.first == selectedKey }
        if (index < 0) return false
        val target = (index + if (dx < 0f) 1 else -1).coerceIn(0, pages.lastIndex)
        if (target == index) return false
        return state.guard {
            state.singleSelection.select(pages[target].first)
            state.sync()
            notifyPagesChanged()
            HookLogger.d(TAG, "单岛切换媒体会话: from=$currentKey, to=${pages[target].first}")
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
        private val accessor = Accessor()
        private val policy = NotificationMediaPlaybackPolicy(accessor)
        private val entries = LinkedHashMap<String, Any>()
        private val observers = LinkedHashMap<String, Pair<MediaSession.Token, NotificationMediaPlaybackObserver>>()
        val cards = LinkedHashMap<String, Card>()
        val selection = NotificationMediaSelectionCoordinator(accessor,
            { policy.orderedKeys(entries.keys.toList()) }, {}, limit)
        val singleSelection = IslandMediaSingleSelection()
        private val binder = required(original, "dynamicMediaViewBinder")
        private val binderTemplate = synchronized(binderTemplates) { binderTemplates[binder] }
            ?: error("Island binder constructor unavailable")
        private val factory = NotificationMediaNativeFactory(notification, binder) {
            error("Island holders must use the native factory")
        }
        val nativePromotions = LinkedHashMap<String, Long>()
        private var pendingPromotion: String? = null
        var expandedKey: String? = null
        private var listener: Any? = null
        var failed = false
            private set
        private val restoreSingle = Runnable { guard { sync() } }

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
                        (args?.getOrNull(0) as? String)?.let { key -> runOnMain { guard { remove(key); sync() } } }
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
                    val sort = read(notification, "mediaSorUtils") ?: read(notification, "mediaSortUtils")
                    val sorted = sort?.let { read(it, "mediaDataList") } as? Iterable<*>
                    sorted?.forEach { value ->
                        val data = value?.let(accessor::sortData) ?: return@forEach
                        val key = accessor.notificationKey(data) ?: return@forEach
                        if (accessor.isActive(data)) entries.putIfAbsent(key, data)
                    }
                    if (entries.isEmpty()) {
                        (read(manager, "mediaEntries") as? Map<*, *>)?.forEach { (key, data) ->
                            if (key is String && data != null && accessor.isActive(data)) entries[key] = data
                        }
                    }
                    policy.seed(entries.map { it.key to it.value })
                    selection.seed(entries.map { it.key to it.value })
                    entries.forEach { (key, data) -> observe(key, data) }
                    selection.onActivityOrderChanged()
                    pendingPromotion = policy.orderedKeys(entries.keys.toList()).firstOrNull {
                        accessor.isPlaying(entries.getValue(it)) == true
                    }
                    sync()
                }
            }
        }

        private fun update(key: String, oldKey: String?, data: Any) {
            if (failed) return
            if (oldKey != null && oldKey != key) remove(oldKey)
            // A session callback is authoritative. Only tokenless media has to rely on
            // MediaData's delayed playback flag for native island promotion.
            if (multi && accessor.sessionToken(data) == null && accessor.isPlaying(data) == true &&
                entries[key]?.let(accessor::isPlaying) != true
            ) pendingPromotion = key
            policy.onMediaDataLoaded(key, oldKey, data)
            selection.onMediaDataLoaded(key, oldKey, data)
            if (accessor.isActive(data)) {
                entries[key] = data
                observe(key, data)
            } else remove(key)
            selection.onActivityOrderChanged()
            sync(setOf(key))
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
                        if (multi && playing) pendingPromotion = key
                        selection.onActivityOrderChanged()
                        sync()
                    }
                }
            })
            observers[key] = token to observer
            val playing = controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
            policy.onPlaybackSignal(key, playing)
            if (multi && playing) pendingPromotion = key
            observer.bind(controller)
        }

        private fun remove(key: String) {
            entries.remove(key)
            nativePromotions.remove(key)
            observers.remove(key)?.second?.clear()
            policy.onMediaDataRemoved(key)
            selection.onMediaDataRemoved(key)
        }

        fun prepareUpdate(window: Any, data: Any, expanded: Boolean): Boolean {
            if (multi || failed) return expanded
            val card = cards["single"] ?: return expanded
            if (card.data() !== data) return expanded
            val displayKey = card.islandKey ?: return expanded
            method(data, "setKey", 1).invoke(data, displayKey)
            val current = method(window, "getCurrentExpandedState", 0).invoke(window)
            val currentData = current?.let { method(it, "getCurrentIslandData", 0).invoke(it) }
            val currentKey = currentData?.let { method(it, "getKey", 0).invoke(it) }
            val currentState = current?.let { method(it, "getState", 0).invoke(it) }
            return expanded || currentKey == displayKey && currentState?.javaClass?.simpleName == "Expanded"
        }

        fun pages(): List<Pair<String, Any>> = selection.snapshot(limit).entries

        fun onPresentationChanged(displayKey: String, state: String) {
            if (multi || failed) return
            val card = cards["single"] ?: return
            if (card.islandKey != displayKey) return
            if (state == "Expanded") {
                singleSelection.setExpanded(true)
            } else if (state in setOf("BigIsland", "SmallIsland", "ShowOnceBigIsland", "Hidden", "Deleted")) {
                singleSelection.setExpanded(false)
                val first = pages().firstOrNull()?.first
                if (card.key != first) {
                    HookLogger.d(TAG, "收起单岛，恢复首位会话: browsed=${card.key}, first=$first")
                    // Native state changes are dispatched within its event iteration. Rebinding
                    // after that dispatch avoids recursively changing the same state machine.
                    main.removeCallbacks(restoreSingle)
                    main.post(restoreSingle)
                }
            }
            // App/MiniWindow transitions keep the browsed intent until native launch completes.
        }

        fun sync(changed: Set<String> = emptySet()) {
            if (failed) return
            val snapshot = selection.snapshot(if (multi) Int.MAX_VALUE else limit)
            val visible = if (multi) {
                val priority = linkedSetOf<String>()
                expandedKey?.takeIf(entries::containsKey)?.let(priority::add)
                snapshot.entries.forEach { priority += it.first }
                val kept = priority.take(limit).toSet()
                snapshot.entries.filter { it.first in kept }
            } else {
                val selected = singleSelection.selectedKey(snapshot.entries.map { it.first })
                snapshot.entries.filter { it.first == selected }
            }
            val next = visible.associate { (key, data) -> (if (multi) key else "single") to (key to data) }
            cards.keys.filter { it !in next }.toList().forEach { cards.remove(it)?.close() }
            if (!multi && next.isEmpty()) singleSelection.setExpanded(false)
            next.forEach { (slot, entry) ->
                var card = cards[slot]
                if (card == null) {
                    card = createCard()
                    cards[slot] = card
                }
                if (card.key != entry.first || entry.first in changed ||
                    !multi && read(card.controller, "currentKey") == null
                ) {
                    card.bind(entry.first, entry.second)
                }
            }
            if (multi) pendingPromotion?.let { key ->
                cards[key]?.let { card ->
                    nativePromotions[key] = System.currentTimeMillis()
                    card.promote()
                }
                pendingPromotion = null
            }
            HookLogger.dState(
                stateId = "$TAG.pages", tag = TAG,
                state = "${visible.map { it.first }}/${snapshot.selectedKey}/${entries.size}",
            ) {
                "媒体岛展示更新: mode=${if (multi) "multi" else "single"}, " +
                    "sessions=${entries.size}, pages=${visible.map { it.first }}, " +
                    "selected=${snapshot.selectedKey}, max=$limit"
            }
            notifyPagesChanged()
        }

        fun guard(action: () -> Unit): Boolean {
            if (failed) return false
            return runCatching(action).onFailure { error ->
                failed = true
                listener?.let { runCatching { method(manager, "removeListener", 1).invoke(manager, it) } }
                cards.values.toList().forEach { runCatching { it.close() } }
                cards.clear()
                observers.values.forEach { it.second.clear() }
                observers.clear()
                @Suppress("UNCHECKED_CAST")
                (required(notification, "topMediaListeners") as MutableSet<Any>).add(nativeListener)
                val top = read(notification, "topMediaData")
                runCatching { method(nativeListener, "onMediaDataChanged", 1).invoke(nativeListener, top) }
                HookLogger.w(TAG, "超级岛媒体卡片更新失败，恢复原生展示", error)
            }.isSuccess
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
                ownedControllers[controller] = null
                method(controller, "resetPlayerHolder", 0).invoke(controller)
                listOf("miuiPlayerHolder", "miuiDummyPlayerHolder").forEach { name ->
                    val holder = required(controller, name)
                    (required(holder, "player") as View).setTag(BRIDGE_TAG, bridge)
                }
                return Card(controller, callback, native, registrations, single = !multi)
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
        private val single: Boolean,
    ) {
        var islandKey: String? = null
            private set
        var key: String? = null
            private set
        fun data(): Any? = read(controller, "dynamicIslandData")
        fun bind(nextKey: String, data: Any) {
            native.prepareBind(data)
            val previous = key
            if (islandKey == null || !single) islandKey = nextKey
            ownedControllers[controller] = islandKey
            if (previous != nextKey) {
                if (!single) previous?.let { old ->
                    method(controller, "removeDynamicIslandView", 2).invoke(controller, old, true)
                }
                // Existing islands can browse paused sessions through the update path. A
                // missing island keeps the native add path and its playback eligibility check.
                if (!single || read(controller, "currentKey") != null) {
                    field(controller, "currentKey").set(controller, nextKey)
                }
            }
            key = nextKey
            method(callback, "onMediaDataChanged", 1).invoke(callback, data)
        }
        fun promote() {
            val payload = data() ?: return
            val plugin = required(controller, "dynamicIslandPluginController")
            val content = plugin.javaClass.methods.first {
                it.name.startsWith("getContent") && it.parameterCount == 0
            }.invoke(plugin) ?: return
            // Let the plugin's normal update path refresh time and run its priority/state machine.
            method(content, "updateDynamicIslandView", 2).invoke(content, payload, false)
            HookLogger.d(TAG, "请求原生媒体岛排序更新: key=$key")
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
