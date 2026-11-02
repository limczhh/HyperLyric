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
    private val ownedControllers = WeakHashMap<Any, Boolean>()
    private val binderTemplates = WeakHashMap<Any, Pair<Constructor<*>, List<Any?>>>()
    private var runtime: Runtime? = null
    // Boot-class interfaces and native View tags keep the persistent media runtime reachable
    // when lyric/plugin hooks are replaced by a new module class loader during hot reload.
    private const val BRIDGE_TAG = 0x7e484c01
    private const val REFRESH_TAG = 0x7e484c02
    private const val PREVIEW_TAG = 0x7e484c03
    private val windows = WeakHashMap<View, Boolean>()
    private val bridge = Function<Array<Any?>, Any?> { args ->
        when (args[0]) {
            "ready" -> isRunning()
            "single" -> runtime?.let { !it.failed && !it.multi } == true
            "pages" -> pageState(args[1] as String)?.let { intArrayOf(it.first, it.second) }
            "switch" -> switchRelative(args[1] as String, args[2] as Float)
            "preview" -> (readPlayer(args[1]!!)?.getTag(PREVIEW_TAG) == true)
            "render" -> { runtime?.let { state -> state.guard { state.renderSingle(args[1]!!) } }; null }
            "published" -> { runtime?.onNativePublished(args[1]!!, args[2] as Boolean); null }
            "consumePromotion" -> runtime?.nativePromotions?.remove(args[1] as String)
            "watch" -> { windows[args[1] as View] = true; null }
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
        window.setTag(BRIDGE_TAG, function)
        function.apply(arrayOf("watch", window))
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
                        val instance = chain.thisObject ?: return chain.proceed()
                        val state = runtime
                        if (ownedControllers.containsKey(instance) ||
                            state?.multi == false && state.original === chain.thisObject
                        ) {
                            chain.args.take(2).forEach { holder ->
                                (holder?.let { read(it, "player") } as? View)?.let { player ->
                                    player.setTag(BRIDGE_TAG, bridge)
                                    if (ownedControllers[chain.thisObject] == true) player.setTag(PREVIEW_TAG, true)
                                }
                            }
                        }
                        val rebuilding = ownedControllers[instance] == false && read(instance, "currentKey") == null
                        if (rebuilding) {
                            // Native reinflation clears currentKey and rejects paused sessions on
                            // its add path. Owned multi-island cards use the native update path.
                            field(instance, "currentKey").set(instance, chain.args[2]?.let { read(it, "notificationKey") })
                        }
                        val result = chain.proceed()
                        if (rebuilding) state?.guard { state.onNativeRebuilt(instance) }
                        if (ownedControllers[chain.thisObject] == true) notifyPagesChanged()
                        return result
                    }
                })
            }
            controller.declaredMethods.filter {
                it.name.startsWith("removeDynamicIslandView") && it.parameterCount == 2 &&
                    it.parameterTypes[0] == String::class.java
            }.forEach { method ->
                module.managedHook(method, "media.island_switcher.suppress_preview_remove", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        // A browsing controller owns views only, never a native island.
                        if (ownedControllers[chain.thisObject] != true) return chain.proceed()
                        field(requireNotNull(chain.thisObject), "currentKey").set(chain.thisObject, null)
                        return null
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

    private fun switchRelative(currentKey: String, dx: Float): Boolean {
        val state = runtime ?: return false
        if (state.multi || state.failed) return false
        val pages = state.pages()
        if (state.nativeKey() != currentKey) return false
        val selectedKey = state.singleSelection.selectedKey(pages.map { it.first })
        val index = pages.indexOfFirst { it.first == selectedKey }
        if (index < 0) return false
        val target = (index + if (dx < 0f) 1 else -1).coerceIn(0, pages.lastIndex)
        if (target == index) return false
        return state.guard {
            state.singleSelection.select(pages[target].first)
            state.sync()
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
        private var expandedKey: String? = null
        private var listener: Any? = null
        var failed = false
            private set
        private val singlePresenter = IslandMediaSinglePresenter()
        private val dirtyKeys = linkedSetOf<String>()
        private var seeded = false
        private var syncScheduled = false
        private var lastPromotionTime = 0L
        private val syncTask = Runnable {
            syncScheduled = false
            val changed = dirtyKeys.toSet()
            dirtyKeys.clear()
            guard { sync(changed) }
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
                    if (multi) pendingPromotion = policy.orderedKeys(entries.keys.toList()).firstOrNull {
                        accessor.isPlaying(entries.getValue(it)) == true
                    }
                    seeded = true
                    requestSync()
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
                        if (multi && playing) pendingPromotion = key
                        selection.onActivityOrderChanged()
                        requestSync()
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
            // Release removed sessions immediately; queued content updates must not keep an
            // obsolete controller eligible for reinflation before the next reconciliation.
            if (multi) cards.remove(key)?.close()
            else if (cards["single"]?.key == key) {
                singlePresenter.restoreAll()
                cards.remove("single")?.close()
            }
        }

        fun onNativePublished(data: Any, wasUpdate: Boolean) {
            if (!multi || failed) return
            val key = method(data, "getKey", 0).invoke(data) as? String ?: return
            cards[key]?.takeIf { it.data() === data }?.let {
                it.lastPublishedData = data
                it.lastPublicationWasUpdate = wasUpdate
                it.publicationVersion++
            }
        }

        fun onNativeRebuilt(controller: Any) {
            if (!multi || failed) return
            val card = cards.values.firstOrNull { it.controller === controller } ?: return
            if (card.lastPublishedData !== card.data()) card.publish()
            // Rebuilding several paused/playing cards must not replace activity order with
            // configuration-callback order. Reconcile once after this callback batch.
            pendingPromotion = policy.orderedKeys(entries.keys.toList()).firstOrNull()
            requestSync()
            HookLogger.d(TAG, "原生媒体岛内容已重建: key=${card.key}")
        }

        fun onExpandedChanged(key: String?) {
            if (expandedKey == key) return
            expandedKey = key
            // Release the expanded-card pin immediately after collapse, including at the cap.
            if (multi) requestSync()
        }

        fun nativeKey(): String? = read(original, "currentKey") as? String

        fun pages(): List<Pair<String, Any>> {
            if (multi) return selection.snapshot(limit).entries
            val all = selection.snapshot().entries
            // The native top-media source remains the authority when several apps play together.
            val first = read(notification, "topMediaData")?.let(accessor::notificationKey)
            return (all.filter { it.first == first } + all.filter { it.first != first }).take(limit)
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
            val preview = cards["single"]?.data()
            singlePresenter.render(real, preview)
        }

        fun resetSingle() {
            if (multi) return
            singleSelection.setExpanded(false)
            singlePresenter.restoreAll()
            cards.remove("single")?.close()
        }

        fun onPresentationChanged(real: Any, state: String) {
            if (multi || failed) return
            val current = method(real, "getCurrentIslandData", 0).invoke(real)
            val key = current?.let { method(it, "getKey", 0).invoke(it) }
            if (key != nativeKey() && !singlePresenter.owns(real)) return
            if (state == "Expanded") {
                singleSelection.setExpanded(true)
            } else if (state in setOf("BigIsland", "SmallIsland", "ShowOnceBigIsland", "Hidden", "Deleted")) {
                // Restore content before native collapse animation, without publishing an island event.
                guard { resetSingle() }
            }
            // App/MiniWindow transitions keep the browsed intent until native launch completes.
        }

        private fun syncSingle(changed: Set<String>) {
            val pages = pages()
            val selected = singleSelection.selectedKey(pages.map { it.first })
            if (selected == null || selected == pages.firstOrNull()?.first) {
                singlePresenter.restoreAll()
                cards.remove("single")?.close()
            } else {
                val data = pages.first { it.first == selected }.second
                val card = cards.getOrPut("single") { createCard() }
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
            val promotionKey = pendingPromotion?.takeIf(next::containsKey)
            pendingPromotion = null
            val before = promotionKey?.let(cards::get)
            val publicationVersion = before?.publicationVersion
            if (promotionKey != null) {
                lastPromotionTime = maxOf(System.currentTimeMillis(), lastPromotionTime + 1)
                nativePromotions[promotionKey] = lastPromotionTime
            }
            next.forEach { (key, data) ->
                val card = cards.getOrPut(key) { createCard() }
                if (card.key != key || key in changed) card.bind(key, data)
            }
            promotionKey?.let { key ->
                val card = cards.getValue(key)
                // Existing-card content updates already carry the pending native reorder.
                // Creation/reinflation dispatches Add, so it still needs a following Update.
                if (publicationVersion == null || !card.lastPublicationWasUpdate ||
                    card.publicationVersion == publicationVersion
                ) card.publish()
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
                ownedControllers[controller] = !multi
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
        var publicationVersion = 0L
        var lastPublicationWasUpdate = false
        var lastPublishedData: Any? = null
        fun data(): Any? = read(controller, "dynamicIslandData")
        fun bind(nextKey: String, data: Any) {
            native.prepareBind(data)
            val previous = key
            if (previous != nextKey) {
                // Multi-island controllers keep native keys. Browsing controllers publish nothing.
                if (ownedControllers[controller] != true) previous?.let { old ->
                    method(controller, "removeDynamicIslandView", 2).invoke(controller, old, true)
                }
                field(controller, "currentKey").set(controller, nextKey)
            }
            key = nextKey
            method(callback, "onMediaDataChanged", 1).invoke(callback, data)
        }
        fun publish() {
            val payload = data() ?: return
            val plugin = required(controller, "dynamicIslandPluginController")
            val content = plugin.javaClass.methods.first {
                it.name.startsWith("getContent") && it.parameterCount == 0
            }.invoke(plugin) ?: return
            // Let the plugin's normal update path refresh time and run its priority/state machine.
            method(content, "updateDynamicIslandView", 2).invoke(content, payload, false)
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
                // Keep the weak preview marker until collection, so already-posted native
                // callbacks cannot publish an island after this browsing card is released.
                if (ownedControllers[controller] != true) ownedControllers.remove(controller)
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

    private fun readPlayer(data: Any): View? = method(data, "getView", 0).invoke(data) as? View

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
