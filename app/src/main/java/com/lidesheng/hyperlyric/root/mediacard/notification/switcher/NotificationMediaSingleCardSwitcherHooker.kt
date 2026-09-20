package com.lidesheng.hyperlyric.root.mediacard.notification.switcher

import android.os.Handler
import android.os.Looper
import android.media.session.MediaController
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.HookRuntimeRegistry
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostClasses
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaCoverStyleHooker
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaForegroundStyler
import com.lidesheng.hyperlyric.root.mediacard.progress.MediaProgressStyleHooker
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.LinkedHashMap
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Restores MIUI 14-style multi-session selection while retaining HyperOS 3's
 * native card implementation. The renderer keeps the stock card as page zero
 * and creates independent native card/controller pairs for additional pages.
 *
 * The selection store still mirrors MediaSortUtils and remains independent of
 * Views. Single-card mode uses MiuiMediaViewControllerImpl.bindMediaData(
 * MediaData); multi-card mode keeps each independently bound native page.
 */
internal object NotificationMediaSingleCardSwitcherHooker {
    private const val TAG = "NotificationMediaSingleCardSwitcher"
    private const val MIN_SWITCH_DISTANCE_DP = 48f
    private const val SWITCH_DISTANCE_FRACTION = 0.15f
    private const val DIRECTION_LOCK_RATIO = 1.2f
    private const val MAX_NATIVE_ATTACH_RETRIES = 8
    private const val MEDIA_DATA_MANAGER =
        "com.android.systemui.media.controls.domain.pipeline.MediaDataManager"
    private const val MEDIA_DATA_MANAGER_LISTENER =
        "com.android.systemui.media.controls.domain.pipeline.MediaDataManager\$Listener"

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val layoutStates = Collections.synchronizedMap(
        WeakHashMap<Any, ControllerState>()
    )
    private val viewStates = Collections.synchronizedMap(
        WeakHashMap<Any, ControllerState>()
    )
    private val headerStates = Collections.synchronizedMap(
        WeakHashMap<View, ControllerState>()
    )
    private val playerStates = Collections.synchronizedMap(
        WeakHashMap<View, ControllerState>()
    )
    private val hookedTouchMethods = Collections.synchronizedSet(mutableSetOf<Method>())
    private val uiModeRefreshInProgress = ThreadLocal<Boolean>()

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

        val layoutClass = loadClass(classLoader, NotificationMediaHostClasses.LAYOUT_CONTROLLER)
        val viewControllerClass = loadClass(
            classLoader,
            NotificationMediaHostClasses.VIEW_CONTROLLER
        )
        if (layoutClass == null || viewControllerClass == null) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "跳过单卡片横滑 Hook: HyperOS 3 媒体类不可用")
            return
        }
        val attach = findMethod(viewControllerClass, "attach") { it.parameterCount == 1 }
        val detach = findMethod(viewControllerClass, "detach") { it.parameterCount == 0 }
        val bind = findMethod(viewControllerClass, "bindMediaData") {
            it.parameterCount == 1
        }
        val fullAodStateChanged = findMethod(viewControllerClass, "onFullAodStateChanged") {
            it.parameterCount == 1 &&
                it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        val updateForegroundColors = findMethod(viewControllerClass, "updateForegroundColors") {
            it.parameterCount == 0
        }
        val headerClass = loadClass(classLoader, NotificationMediaHostClasses.MEDIA_HEADER_VIEW)
        val headerSetTranslation = headerClass?.let {
            findMethod(it, "setTranslation") {
                it.parameterCount == 1 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType
            }
        }
        val headerGetTranslation = headerClass?.let {
            findMethod(it, "getTranslation") {
                it.parameterCount == 0 &&
                    it.returnType == Float::class.javaPrimitiveType
            }
        }
        val installedHandles = mutableListOf<HookHandle>()
        var installed = false
        try {
            if (attach == null || detach == null || bind == null) {
                error(
                    "required methods unavailable: attach=${attach != null}, " +
                        "detach=${detach != null}, bind=${bind != null}"
                )
            }

            val constructors = layoutClass.declaredConstructors
            if (constructors.isEmpty()) error("layout controller has no constructor")
            constructors.forEach { constructor ->
                val handle = install(xposedModule, constructor, LayoutConstructorHook())
                    ?: error("constructor hook failed: ${constructor.name}")
                installedHandles += handle
            }

            val attachHandle = install(
                xposedModule,
                attach,
                ViewControllerHook(Action.ATTACH)
            ) ?: error("attach hook failed")
            installedHandles += attachHandle
            val detachHandle = install(
                xposedModule,
                detach,
                ViewControllerHook(Action.DETACH)
            ) ?: error("detach hook failed")
            installedHandles += detachHandle
            val bindHandle = install(
                xposedModule,
                bind,
                ViewControllerHook(Action.BIND)
            ) ?: error("bind hook failed")
            installedHandles += bindHandle

            fullAodStateChanged?.let { method ->
                val handle = install(
                    xposedModule,
                    method,
                    ViewControllerHook(Action.FULL_AOD)
                ) ?: error("Full AOD state hook failed")
                installedHandles += handle
            }

            updateForegroundColors?.let { method ->
                val handle = install(
                    xposedModule,
                    method,
                    ViewControllerHook(Action.FOREGROUND)
                ) ?: error("foreground color hook failed")
                installedHandles += handle
            }

            headerSetTranslation?.let {
                val handle = install(
                    xposedModule,
                    it,
                    HeaderTranslationHook(getter = false)
                ) ?: error("header setTranslation hook failed")
                installedHandles += handle
            }
            headerGetTranslation?.let {
                val handle = install(
                    xposedModule,
                    it,
                    HeaderTranslationHook(getter = true)
                ) ?: error("header getTranslation hook failed")
                installedHandles += handle
            }
            installed = true
        } catch (error: Exception) {
            HookLogger.e(TAG, "单卡片横滑 Hook 安装失败，准备回滚", error)
        } finally {
            if (!installed) {
                rollbackHooks(xposedModule, installedHandles)
                hookedClassLoaders.remove(classLoader)
            }
        }

        if (installed) {
            moduleForTouchHook = xposedModule
            NotificationMediaForegroundStyler.addAppliedListener(
                ::onForegroundColorsApplied
            )
            HookLogger.d(
                TAG,
                "HyperOS 3 单卡片横滑 Hook 已初始化: methods=${installedHandles.size}"
            )
        }
    }

    fun extraCardControllers(): Set<Any> {
        val states = synchronized(viewStates) { viewStates.values.toSet() }
        return states
            .flatMap { state -> state.extraCardControllers() }
            .toSet()
    }

    fun refreshForUiMode() {
        val states = synchronized(viewStates) { viewStates.values.toSet() }
        states.forEach { state -> state.refreshForUiMode() }
    }

    fun runWithUiModeRefreshGuard(block: () -> Unit) {
        val previous = uiModeRefreshInProgress.get()
        uiModeRefreshInProgress.set(true)
        try {
            block()
        } finally {
            if (previous == null) {
                uiModeRefreshInProgress.remove()
            } else {
                uiModeRefreshInProgress.set(previous)
            }
        }
    }

    private fun registerLayoutController(controller: Any, classLoader: ClassLoader) {
        if (layoutStates.containsKey(controller)) return

        val mediaDataManager = readField(controller, "mediaDataManager")
        val viewController = readField(controller, "mediaViewController")
        val sortUtils = readField(controller, "mediaSorUtils")
            ?: readField(controller, "mediaSortUtils")
        if (mediaDataManager == null || viewController == null || sortUtils == null) {
            HookLogger.w(TAG, "跳过媒体列表注册: 原生字段缺失")
            return
        }

        val bindMethod = findMethod(viewController.javaClass, "bindMediaData") {
            it.parameterCount == 1
        }
        if (bindMethod == null) {
            HookLogger.w(TAG, "跳过媒体列表注册: bindMediaData 不可用")
            return
        }

        val state = ControllerState(
            layoutController = controller,
            viewController = viewController,
            mediaDataManager = mediaDataManager,
            sortUtils = sortUtils,
            bindMethod = bindMethod,
            accessor = ReflectedMediaDataAccessor()
        )
        layoutStates[controller] = state
        viewStates[viewController] = state

        runCatching {
            state.seedFromNativeSort()
            registerMediaDataListener(state, classLoader)
        }.onFailure { error ->
            layoutStates.remove(controller)
            viewStates.remove(viewController)
            HookLogger.e(TAG, "注册 MediaData.Listener 失败", error)
        }
    }

    private fun registerMediaDataListener(
        state: ControllerState,
        classLoader: ClassLoader
    ) {
        val listenerClass = resolveListenerClass(state.mediaDataManager, classLoader)
            ?: error("MediaDataManager.Listener unavailable")
        val addListener = findMethod(state.mediaDataManager.javaClass, "addListener") {
            it.parameterCount == 1 && isCompatibleListenerParameter(it.parameterTypes[0], listenerClass)
        } ?: error("MediaDataManager.addListener unavailable")

        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "onMediaDataLoaded" -> {
                    val data = args.getOrNull(2)
                    val key = args.getOrNull(0) as? String
                        ?: data?.let(state.accessor::notificationKey)
                    if (key != null && data != null) {
                        state.onMediaDataLoaded(
                            key = key,
                            oldKey = args.getOrNull(1) as? String,
                            data = data
                        )
                    }
                    null
                }

                "onMediaDataRemoved" -> {
                    (args.getOrNull(0) as? String)?.let(state::onMediaDataRemoved)
                    null
                }

                "toString" -> "$TAG.Listener"
                "hashCode" -> System.identityHashCode(state)
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        }
        val proxy = Proxy.newProxyInstance(
            listenerClass.classLoader ?: classLoader,
            arrayOf(listenerClass),
            handler
        )
        addListener.invoke(state.mediaDataManager, proxy)
        state.listenerProxy = proxy
    }

    private fun resolveListenerClass(manager: Any, classLoader: ClassLoader): Class<*>? {
        runCatching { classLoader.loadClass(MEDIA_DATA_MANAGER_LISTENER) }
            .getOrNull()
            ?.takeIf(Class<*>::isInterface)
            ?.let { return it }

        var current: Class<*>? = manager.javaClass
        while (current != null) {
            current.declaredClasses.firstOrNull { clazz ->
                clazz.isInterface && clazz.simpleName == "Listener" &&
                    clazz.declaredMethods.any { it.name == "onMediaDataLoaded" }
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun isCompatibleListenerParameter(
        parameterClass: Class<*>,
        listenerClass: Class<*>
    ): Boolean {
        return parameterClass == listenerClass ||
            parameterClass.isAssignableFrom(listenerClass) ||
            listenerClass.isAssignableFrom(parameterClass)
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when (type) {
            Boolean::class.javaPrimitiveType -> false
            Byte::class.javaPrimitiveType -> 0.toByte()
            Short::class.javaPrimitiveType -> 0.toShort()
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Char::class.javaPrimitiveType -> '\u0000'
            else -> null
        }
    }

    private fun install(
        xposedModule: XposedModule,
        executable: Executable,
        hooker: Hooker
    ): HookHandle? {
        return try {
            executable.isAccessible = true
            xposedModule.managedHook(
                executable = executable,
                capability = "media.notification.switcher.${executable.name}",
                hooker = hooker,
            )
        } catch (error: Exception) {
            HookLogger.e(
                TAG,
                "安装单卡片横滑方法失败: ${executable.declaringClass.name}.${executable.name}",
                error
            )
            null
        }
    }

    private fun rollbackHooks(module: XposedModule, handles: List<HookHandle>) {
        handles.asReversed().forEach { handle ->
            try {
                HookRuntimeRegistry.forget(module, handle)
                handle.unhook()
            } catch (error: Exception) {
                HookLogger.e(TAG, "回滚单卡片横滑 Hook 失败", error)
            }
        }
    }

    private fun ensureTouchHook(xposedModule: XposedModule, player: View) {
        val method = findTouchDispatchMethod(player.javaClass) ?: run {
            HookLogger.w(TAG, "跳过横滑触摸 Hook: player.dispatchTouchEvent 不可用")
            return
        }
        synchronized(hookedTouchMethods) {
            if (!hookedTouchMethods.add(method)) return
        }

        var installed = false
        try {
            method.isAccessible = true
            xposedModule.managedHook(
                executable = method,
                capability = "media.notification.switcher.dispatch_touch",
                hooker = DispatchTouchHook(),
            )
            installed = true
        } catch (error: Exception) {
            HookLogger.e(TAG, "安装媒体卡片触摸分发 Hook 失败", error)
        } finally {
            if (!installed) hookedTouchMethods.remove(method)
        }
    }

    private fun findTouchDispatchMethod(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredMethods.firstOrNull { method ->
                method.name == "dispatchTouchEvent" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == MotionEvent::class.java &&
                    method.returnType == Boolean::class.javaPrimitiveType
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun loadClass(classLoader: ClassLoader, name: String): Class<*>? {
        return runCatching { classLoader.loadClass(name) }.getOrNull()
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        predicate: (Method) -> Boolean
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && predicate(it) }
                ?.apply { isAccessible = true }
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching { current.getDeclaredField(name) }
                .onSuccess { field ->
                    field.isAccessible = true
                }
                .getOrNull()
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun readField(target: Any?, name: String): Any? {
        target ?: return null
        return runCatching { findField(target.javaClass, name)?.get(target) }.getOrNull()
    }

    private enum class Action {
        ATTACH,
        DETACH,
        BIND,
        FULL_AOD,
        FOREGROUND
    }

    private class LayoutConstructorHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val controller = chain.thisObject ?: return result
            val classLoader = controller.javaClass.classLoader ?: return result
            runCatching {
                NotificationMediaSingleCardSwitcherHooker.registerLayoutController(
                    controller,
                    classLoader
                )
            }.onFailure { error ->
                HookLogger.e(TAG, "初始化媒体卡片选择器失败", error)
            }
            return result
        }
    }

    private class ViewControllerHook(
        private val action: Action
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val viewController = chain.thisObject ?: return chain.proceed()
            val state = NotificationMediaSingleCardSwitcherHooker.viewStates[viewController]

            val fullAodActive = if (action == Action.FULL_AOD) {
                chain.args.firstOrNull() as? Boolean
            } else {
                null
            }

            if (action == Action.DETACH) {
                state?.let { NotificationMediaSingleCardSwitcherHooker.removePlayer(it) }
                val result = chain.proceed()
                state?.onDetached()
                return result
            }

            val result = chain.proceed()
            when (action) {
                Action.ATTACH -> {
                    val holder = chain.args.firstOrNull() ?: return result
                    state?.let {
                        NotificationMediaSingleCardSwitcherHooker.attachPlayer(it, holder)
                    }
                }

                Action.BIND -> state?.onNativeBind(chain.args.firstOrNull())
                Action.DETACH -> Unit
                Action.FULL_AOD -> fullAodActive?.let { active ->
                    state?.onFullAodStateChanged(active)
                }
                Action.FOREGROUND -> {
                    if (uiModeRefreshInProgress.get() != true) {
                        state?.onNativeForegroundColorsUpdated()
                    }
                    NotificationMediaSingleCardSwitcherHooker.onForegroundColorsApplied(
                        viewController
                    )
                }
            }
            return result
        }
    }

    private class DispatchTouchHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.thisObject as? View ?: return chain.proceed()
            val event = chain.args.firstOrNull() as? MotionEvent ?: return chain.proceed()
            val state = NotificationMediaSingleCardSwitcherHooker.playerStates[view]
                ?: return chain.proceed()
            return state.dispatchTouchEvent(view, event, chain)
        }
    }

    private class HeaderTranslationHook(
        private val getter: Boolean
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val header = chain.thisObject as? View
            val state = header?.let { headerStates[it] }
            val result = chain.proceed()
            if (state == null) return result

            if (getter) {
                return state.headerTranslation() ?: result
            }

            val translation = chain.args.firstOrNull() as? Float ?: return result
            state.setHeaderTranslation(translation)
            return result
        }
    }

    private fun attachPlayer(state: ControllerState, holder: Any) {
        val player = state.attach(holder) ?: run {
            state.disableForFailure("原生媒体卡片 View 不可用")
            return
        }
        playerStates[player] = state
        state.ensureTouchHook(player)
    }

    private fun onForegroundColorsApplied(controller: Any) {
        val states = synchronized(viewStates) { viewStates.values.toSet() }
        states.forEach { state -> state.onForegroundColorsApplied(controller) }
    }

    private fun removePlayer(state: ControllerState) {
        state.player?.get()?.let(state::unregisterPlayer)
    }

    private class ControllerState(
        layoutController: Any,
        viewController: Any,
        val mediaDataManager: Any,
        sortUtils: Any,
        private val bindMethod: Method,
        val accessor: ReflectedMediaDataAccessor
    ) {
        private val layoutControllerRef = WeakReference(layoutController)
        private val viewControllerRef = WeakReference(viewController)
        private val sortUtilsRef = WeakReference(sortUtils)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val bindLock = Any()
        private val nativePlaybackObserver = NotificationMediaPlaybackObserver(
            ::onNativeMediaChanged
        )
        private val pendingMediaRefreshes = LinkedHashMap<String, Int>()
        private var bindingSelected = false
        private val seekBars = Collections.synchronizedMap(WeakHashMap<View, View>())

        var listenerProxy: Any? = null
        var player: WeakReference<View>? = null
            private set

        private var seekBar: WeakReference<View>? = null
        private var mediaHeader: WeakReference<View>? = null
        private var nativeAttachRetryScheduled = false
        private var nativeAttachRetryCount = 0
        private var touchDownX = 0f
        private var touchDownY = 0f
        private var touchSlop = 0
        private var touchThreshold = 0f
        private var touchIgnored = false
        private var touchDirectionDecided = false
        private var touchHorizontal = false
        private var touchConsumed = false
        private var switcherUnavailable = false
        private var fullAodActive = false
        private var pageIndicatorOrderLockGeneration: Int? = null
        private var pageIndicatorNeedsSync = true
        private var lastIndicatorPageCount = -1
        private var lastIndicatorSelectedIndex = -1
        private val pageCountLimit =
            MediaCardRuntimeConfig.current.notification.cardSwitcherMaxCount

        private val playbackPolicy = NotificationMediaPlaybackPolicy(
            accessor = accessor,
            onStableModeChanged = ::onPlaybackPolicySettled
        )

        private val selection = NotificationMediaSelectionCoordinator(
            accessor = accessor,
            nativeOrder = ::nativeOrder,
            nativeTopKey = ::nativeTopKey,
            bindSelected = ::bindSelected,
            shouldPreserveNativeOrder = { playbackPolicy.shouldPreserveNativeOrder },
            maxPageCount = pageCountLimit
        )

        private val multiCardRenderer = NotificationMediaMultiCardRenderer(
            layoutController = layoutController,
            templateController = viewController,
            nativeTopKey = ::nativeTopKey,
            onPlayerAttached = ::registerAdditionalPlayer,
            onPlayerDetached = ::unregisterPlayer,
            onPageSelected = ::onRendererPageSelected,
            onPageScrolled = ::onRendererPageScrolled,
            onGestureStarted = ::onRendererGestureStarted,
            onPageOrderChanged = ::onRendererPageOrderChanged,
            onCardMediaChanged = ::onAdditionalCardMediaChanged,
            shouldIgnoreScrollTouch = ::isAnySeekBarTouch
        )

        private val nativeGestureBlocker = NotificationMediaNativeGestureBlocker(
            // Both modes replace the native horizontal media-card gesture once
            // at least two MediaData entries exist. Multi-card mode delegates
            // the gesture to PageScrollView; single-card mode consumes it at
            // the player and binds the selected MediaData instead.
            isCarouselActive = {
                isSwitcherUsable() && selection.size >= 2
            },
            headerParent = { mediaHeader?.get()?.parent }
        )

        fun seedFromNativeSort() {
            val initialEntries = LinkedHashMap<String, Any>()
            val sort = sortUtilsRef.get()
            val sortList = NotificationMediaSingleCardSwitcherHooker.readField(
                sort,
                "mediaDataList"
            ) as? Iterable<*>
            sortList?.forEach { sortKey ->
                if (sortKey == null) return@forEach
                val key = accessor.sortKey(sortKey) ?: return@forEach
                val data = accessor.sortData(sortKey) ?: return@forEach
                initialEntries[key] = data
            }

            if (initialEntries.isEmpty()) {
                val sortMap = NotificationMediaSingleCardSwitcherHooker.readField(
                    sort,
                    "mediaDataToSortKey"
                ) as? Map<*, *>
                sortMap?.entries?.forEach { entry ->
                    val mapKey = entry.key
                    val sortKey = entry.value
                    val key = if (mapKey is String) {
                        mapKey
                    } else if (sortKey != null) {
                        accessor.sortKey(sortKey)
                    } else {
                        null
                    }
                    val data = if (sortKey != null) accessor.sortData(sortKey) else null
                    if (key != null && data != null) initialEntries[key] = data
                }
            }
            playbackPolicy.seed(initialEntries.map { it.key to it.value })
            selection.seed(initialEntries.map { it.key to it.value })
            updatePageIndicator()
        }

        fun onMediaDataLoaded(key: String, oldKey: String?, data: Any) {
            runOnMain {
                pendingMediaRefreshes.remove(key)
                playbackPolicy.onMediaDataLoaded(key, oldKey, data)
                selection.onMediaDataLoaded(key, oldKey, data)
                syncMultiCards(forceRebindKeys = setOf(key))
                updatePageIndicator()
            }
        }

        fun onMediaDataRemoved(key: String) {
            runOnMain {
                playbackPolicy.onMediaDataRemoved(key)
                selection.onMediaDataRemoved(key)
                syncMultiCards()
                updatePageIndicator()
            }
        }

        fun onNativeBind(data: Any?) {
            val synthetic = synchronized(bindLock) { bindingSelected }
            runOnMain {
                observeNativePlayback()
                playbackPolicy.onNativeBind(data, synthetic = synthetic)
                selection.onNativeBind(data, synthetic = synthetic)
                val key = data?.let(accessor::notificationKey)
                syncMultiCards(forceRebindKeys = key?.let(::setOf) ?: emptySet())
                updatePageIndicator()
            }
        }

        fun onFullAodStateChanged(active: Boolean) {
            runOnMain {
                multiCardRenderer.setFullAodState(
                    active = active,
                    keepExpanded = NotificationMediaCoverStyleHooker
                        .shouldKeepExpandedInFullAod()
                )
                if (fullAodActive == active) return@runOnMain
                fullAodActive = active
                // Full AOD is non-interactive for the carousel even when the
                // shared policy keeps custom-layout cards expanded. The
                // indicator is attached to the expanded header parent, so it
                // has no meaningful position while that presentation is active.
                updatePageIndicator(force = true)
            }
        }

        private fun onPlaybackPolicySettled() {
            if (!isSwitcherUsable()) return
            val data = playbackPolicy.preferredPlayingData() ?: nativeTopData() ?: return
            selection.onNativeBind(data)
            syncMultiCards()
            updatePageIndicator()
        }

        fun attach(holder: Any): View? {
            val currentPlayer = NotificationMediaSingleCardSwitcherHooker.readField(
                holder,
                "player"
            ) as? View ?: return null
            player = WeakReference(currentPlayer)
            playbackPolicy.initialize()
            // detach() clears the policy and selection snapshots. Re-seed from
            // MediaSortUtils on every native attach so a re-inflated header does
            // not resume with a partial order until the next MediaData callback.
            seedFromNativeSort()
            seekBar = visibleSeekBar(holder)?.let(::WeakReference)
            touchSlop = ViewConfiguration.get(currentPlayer.context).scaledTouchSlop
            resetTouch(currentPlayer)
            registerSeekBar(currentPlayer, holder)
            completeNativeAttach(currentPlayer, holder)
            return currentPlayer
        }

        private fun completeNativeAttach(currentPlayer: View, holder: Any) {
            if (switcherUnavailable) return
            if (!multiCardRenderer.attachOriginal(currentPlayer, holder)) {
                if (!nativeAttachRetryScheduled &&
                    nativeAttachRetryCount < MAX_NATIVE_ATTACH_RETRIES
                ) {
                    nativeAttachRetryCount++
                    nativeAttachRetryScheduled = true
                    currentPlayer.post {
                        nativeAttachRetryScheduled = false
                        if (player?.get() === currentPlayer) {
                            completeNativeAttach(currentPlayer, holder)
                        }
                    }
                } else if (!nativeAttachRetryScheduled) {
                    disableForFailure("原生媒体卡片未在重试窗口内挂载")
                }
                return
            }

            nativeAttachRetryCount = 0
            val originalParent = currentPlayer.parent as? android.view.ViewGroup
            originalParent?.let {
                mediaHeader = WeakReference(it)
                headerStates[it] = this
            }
            syncMultiCards()
            originalParent?.let(pageIndicator::attachTo)
            pageIndicatorNeedsSync = true
            updatePageIndicator()
        }

        fun disableForFailure(reason: String, error: Throwable? = null) {
            if (switcherUnavailable) return
            switcherUnavailable = true
            clearPlaybackObservers()
            resetTouch(player?.get())
            multiCardRenderer.detach()
            pageIndicator.detach()
            mediaHeader?.get()?.let(headerStates::remove)
            HookLogger.w(TAG, "媒体卡片切换功能已停用：$reason", error)
        }

        fun ensureTouchHook(currentPlayer: View) {
            ensureTouchHookForState(currentPlayer)
        }

        fun onDetached() {
            val currentPlayer = player?.get()
            clearPlaybackObservers()
            resetTouch(currentPlayer)
            multiCardRenderer.detach()
            pageIndicator.detach()
            mediaHeader?.get()?.let(headerStates::remove)
            mediaHeader = null
            nativeAttachRetryScheduled = false
            nativeAttachRetryCount = 0
            pageIndicatorOrderLockGeneration = null
            pageIndicatorNeedsSync = true
            lastIndicatorPageCount = -1
            lastIndicatorSelectedIndex = -1
            currentPlayer?.let(::unregisterPlayer)
            player = null
            seekBar = null
            switcherUnavailable = false
            runOnMain {
                playbackPolicy.onDetached()
                selection.onDetached()
            }
        }

        fun unregisterPlayer(currentPlayer: View) {
            playerStates.remove(currentPlayer)
            seekBars.remove(currentPlayer)
        }

        fun dispatchTouchEvent(
            view: View,
            event: MotionEvent,
            chain: Chain
        ): Any? {
            if (!isSwitcherUsable() ||
                selection.size < 2
            ) {
                nativeGestureBlocker.release(view)
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    resetTouch(view)
                }
                return chain.proceed()
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetTouch(view)
                    touchDownX = event.x
                    touchDownY = event.y
                    touchThreshold = calculateSwitchThreshold(view)
                    touchIgnored = isSeekBarTouch(view, event)
                    // A seek bar belongs to the card and must keep the
                    // HorizontalScrollView from stealing its drag. When the
                    // switcher owns at least two sessions, block the outer
                    // notification swipe helper from DOWN so it cannot win
                    // the same MOVE event before our selected-card path sees it.
                    nativeGestureBlocker.onDown(view, touchIgnored)
                    return chain.proceed()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (touchIgnored) return chain.proceed()
                    if (touchConsumed) return true

                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    if (!touchDirectionDecided) {
                        if (maxOf(absDx, absDy) <= touchSlop) {
                            return chain.proceed()
                        }
                        when {
                            absDx > absDy * DIRECTION_LOCK_RATIO -> {
                                touchDirectionDecided = true
                                touchHorizontal = true
                                nativeGestureBlocker.onHorizontal(view, touchIgnored)
                            }

                            absDy > absDx * DIRECTION_LOCK_RATIO -> {
                                touchDirectionDecided = true
                                touchHorizontal = false
                                nativeGestureBlocker.onVertical(view)
                                return chain.proceed()
                            }

                            else -> return chain.proceed()
                        }
                    }

                    if (!touchHorizontal || absDx < touchThreshold) {
                        return chain.proceed()
                    }

                    // Once the native multi-card container is active, the
                    // PageScrollView owns this gesture. Otherwise this
                    // player-level threshold selects the next MediaData in
                    // single-card mode.
                    if (multiCardRenderer.isActive) return chain.proceed()

                    val cancel = MotionEvent.obtain(event)
                    cancel.action = MotionEvent.ACTION_CANCEL
                    runCatching {
                        chain.proceed(arrayOf<Any?>(cancel))
                    }.onFailure { error ->
                        HookLogger.w(TAG, "取消原生媒体卡片触摸目标失败", error)
                    }
                    cancel.recycle()

                    touchConsumed = true
                    val visualStep = if (dx < 0f) 1 else -1
                    val step = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        -visualStep
                    } else {
                        visualStep
                    }
                    runOnMain {
                        selection.selectRelative(step)
                        syncMultiCards()
                        updatePageIndicator()
                    }
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (touchConsumed) {
                        resetTouch(view)
                        return true
                    }
                    // A HorizontalScrollView sends ACTION_CANCEL to the
                    // player when it takes over a horizontal gesture. It has
                    // already re-asserted the parent disallow flag before
                    // doing so; releasing it here would hand the remainder
                    // of the same swipe back to MiuiNotificationSwipeHelper.
                    val keepCarouselLock = event.actionMasked == MotionEvent.ACTION_CANCEL &&
                        touchHorizontal && multiCardRenderer.isActive
                    resetTouch(view, releaseParent = !keepCarouselLock)
                }
            }
            return chain.proceed()
        }

        private fun bindSelected(data: Any) {
            if (!isSwitcherUsable()) return
            // The renderer already owns the viewport when it is active. The
            // gesture-release path performs the one continuous snap; calling
            // showSelected here as well races that animation and can make the
            // page appear to flash into place.
            if (multiCardRenderer.isActive) return
            val target = viewControllerRef.get() ?: return
            synchronized(bindLock) {
                if (bindingSelected) return
                bindingSelected = true
            }
            try {
                bindMethod.invoke(target, data)
            } catch (error: Throwable) {
                HookLogger.e(TAG, "调用原生 bindMediaData 切换媒体失败", error)
                disableForFailure("单卡片视图绑定媒体失败", error)
            } finally {
                synchronized(bindLock) {
                    bindingSelected = false
                }
            }
        }

        private val pageIndicator = NotificationMediaPageIndicator()

        private fun onAdditionalCardMediaChanged(key: String) {
            scheduleMediaDataRefresh(key)
        }

        private fun onNativeMediaChanged() {
            val data = nativeTopData() ?: return
            val key = accessor.notificationKey(data) ?: return
            scheduleMediaDataRefresh(key)
        }

        private fun observeNativePlayback() {
            val controller = viewControllerRef.get()
            val mediaController = NotificationMediaSingleCardSwitcherHooker.readField(
                controller,
                "mediaController"
            ) as? MediaController
            nativePlaybackObserver.bind(mediaController)
        }

        /**
         * A transport action changes the MediaSession first and MediaData may
         * arrive a little later. Read the latest MediaSortKey data twice after
         * the callback so action closures, artwork and isPlaying are refreshed
         * together. The small bounded retry also covers players which publish
         * metadata and playback state in separate binder callbacks.
         */
        private fun scheduleMediaDataRefresh(key: String) {
            if (key.isEmpty()) return
            runOnMain {
                if (!isSwitcherUsable() || key in pendingMediaRefreshes) return@runOnMain
                pendingMediaRefreshes[key] = 0
                postNextMediaRefresh(key, 80L)
            }
        }

        private fun postNextMediaRefresh(key: String, delayMs: Long) {
            mainHandler.postDelayed({
                val attempt = pendingMediaRefreshes[key] ?: return@postDelayed
                refreshMediaDataFromNative(key)
                if (attempt < 2 && isSwitcherUsable()) {
                    pendingMediaRefreshes[key] = attempt + 1
                    postNextMediaRefresh(key, 180L)
                } else {
                    pendingMediaRefreshes.remove(key)
                }
            }, delayMs)
        }

        private fun refreshMediaDataFromNative(key: String) {
            val data = latestMediaDataForKey(key) ?: nativeDataForKey(key) ?: return
            playbackPolicy.onMediaDataLoaded(key, oldKey = null, data = data)
            selection.onMediaDataLoaded(key, oldKey = null, data = data)
            syncMultiCards(forceRebindKeys = setOf(key))
            updatePageIndicator()
        }

        private fun nativeDataForKey(key: String): Any? {
            val sort = sortUtilsRef.get() ?: return null
            val sortList = NotificationMediaSingleCardSwitcherHooker.readField(
                sort,
                "mediaDataList"
            ) as? Iterable<*>
            sortList?.firstNotNullOfOrNull { sortKey ->
                if (sortKey == null || accessor.sortKey(sortKey) != key) {
                    null
                } else {
                    accessor.sortData(sortKey)
                }
            }?.let { return it }

            val sortMap = NotificationMediaSingleCardSwitcherHooker.readField(
                sort,
                "mediaDataToSortKey"
            ) as? Map<*, *>
            val sortKey = sortMap?.get(key) ?: return null
            return accessor.sortData(sortKey)
        }

        /**
         * LegacyMediaDataManagerImpl updates mediaEntries before notifying its
         * listeners. MediaSortUtils can still expose the previous SortKey for
         * one turn, so playback/action refreshes must prefer this source.
         */
        private fun latestMediaDataForKey(key: String): Any? {
            val entries = NotificationMediaSingleCardSwitcherHooker.readField(
                mediaDataManager,
                "mediaEntries"
            ) as? Map<*, *> ?: return null
            return entries[key]
        }

        private fun clearPlaybackObservers() {
            nativePlaybackObserver.clear()
            pendingMediaRefreshes.clear()
        }

        fun onForegroundColorsApplied(controller: Any) {
            if (
                controller !== viewControllerRef.get() &&
                !multiCardRenderer.ownsController(controller)
            ) {
                return
            }
            runOnMain {
                if (isSwitcherUsable()) {
                    pageIndicator.updateTint(resolveIndicatorColor())
                }
            }
        }

        fun onNativeForegroundColorsUpdated() {
            runOnMain {
                if (isSwitcherUsable()) {
                    multiCardRenderer.refreshUiMode()
                }
            }
        }

        fun refreshForUiMode() {
            runOnMain {
                if (isSwitcherUsable()) {
                    multiCardRenderer.refreshUiMode()
                }
            }
        }

        fun extraCardControllers(): Set<Any> = multiCardRenderer.extraCardControllers()

        private fun resolveIndicatorColor(): Int? {
            if (multiCardRenderer.isActive) {
                multiCardRenderer.foregroundColor(pageSnapshot().selectedIndex)
                    ?.let { return it }
            }
            return viewControllerRef.get()?.let(NotificationMediaForegroundStyler::foregroundColor)
        }

        private fun syncMultiCards(forceRebindKeys: Set<String> = emptySet()) {
            if (!isSwitcherUsable()) return
            if (MediaCardRuntimeConfig.current.notification.cardSwitcherMode ==
                RootConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI
            ) {
                val snapshot = pageSnapshot()
                val entries = snapshot.entries
                val previousGeneration = multiCardRenderer.currentPageOrderGeneration
                when (
                    multiCardRenderer.sync(
                        entries = entries,
                        selectedIndex = snapshot.selectedIndex,
                        forceRebindKeys = forceRebindKeys
                    )
                ) {
                    NotificationMediaMultiCardSyncResult.NOT_READY -> {
                        // MediaData can be delivered before ViewController.attach.
                        // Keep the data snapshot and let completeNativeAttach()
                        // perform the first real renderer sync.
                        pageIndicatorNeedsSync = true
                    }

                    NotificationMediaMultiCardSyncResult.FAILED -> {
                        if (entries.size >= 2) {
                            disableForFailure("多卡片视图创建失败")
                        }
                    }

                    NotificationMediaMultiCardSyncResult.SUCCESS -> {
                        val currentGeneration = multiCardRenderer.currentPageOrderGeneration
                        if (currentGeneration != previousGeneration) {
                            // The renderer has synchronously corrected the card
                            // position. Ignore delayed scroll callbacks from the
                            // previous order until the next user gesture.
                            pageIndicatorOrderLockGeneration = currentGeneration
                            pageIndicator.forceUpdate(
                                pageCount = entries.size,
                                selectedIndex = snapshot.selectedIndex,
                                enabled = isPageIndicatorEnabled()
                            )
                            lastIndicatorPageCount = entries.size
                            lastIndicatorSelectedIndex = snapshot.selectedIndex
                            pageIndicatorNeedsSync = false
                        }
                    }
                }
            }
        }

        private fun isSwitcherUsable(): Boolean {
            return MediaCardRuntimeConfig.current.notification.cardSwitcherEnabled &&
                !switcherUnavailable
        }

        private fun isPageIndicatorEnabled(): Boolean {
            return isSwitcherUsable() && !fullAodActive
        }

        private fun onRendererPageSelected(key: String) {
            runOnMain {
                if (selection.size < 2) return@runOnMain
                selection.selectKey(key)
            }
        }

        private fun onRendererGestureStarted() {
            pageIndicatorOrderLockGeneration = null
        }

        private fun onRendererPageOrderChanged(generation: Int) {
            pageIndicatorOrderLockGeneration = generation
        }

        private fun onRendererPageScrolled(location: Float, generation: Int) {
            val update = {
                if (generation == multiCardRenderer.currentPageOrderGeneration &&
                    pageIndicatorOrderLockGeneration != generation
                ) {
                    pageIndicator.updateLocation(
                        pageCount = multiCardRenderer.pageCount,
                        location = location,
                        enabled = isPageIndicatorEnabled()
                    )
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) update() else mainHandler.post(update)
        }

        fun setHeaderTranslation(translation: Float) {
            multiCardRenderer.setHeaderTranslation(translation)
            pageIndicator.setTranslationX(translation)
        }

        fun headerTranslation(): Float? = multiCardRenderer.headerTranslation()

        private fun registerAdditionalPlayer(currentPlayer: View, holder: Any) {
            playerStates[currentPlayer] = this
            registerSeekBar(currentPlayer, holder)
            ensureTouchHookForState(currentPlayer)
        }

        private fun registerSeekBar(currentPlayer: View, holder: Any) {
            visibleSeekBar(holder)?.let { seekBars[currentPlayer] = it }
        }

        private fun visibleSeekBar(holder: Any): View? =
            MediaProgressStyleHooker.replacementSeekBar(holder)
                ?: (NotificationMediaSingleCardSwitcherHooker.readField(
                    holder,
                    "seekBar"
                ) as? View)

        private fun isAnySeekBarTouch(event: MotionEvent): Boolean {
            val players = synchronized(seekBars) { seekBars.keys.toList() }
            return players.any { isSeekBarTouch(it, event) }
        }

        private fun updatePageIndicator(force: Boolean = false) {
            val snapshot = pageSnapshot()
            val pageCount = snapshot.entries.size
            val selectedIndex = snapshot.selectedIndex
            pageIndicator.updateTint(resolveIndicatorColor())
            if (!force && !pageIndicatorNeedsSync &&
                pageCount == lastIndicatorPageCount &&
                selectedIndex == lastIndicatorSelectedIndex
            ) {
                return
            }
            pageIndicator.update(
                pageCount = pageCount,
                selectedIndex = selectedIndex,
                enabled = isPageIndicatorEnabled()
            )
            lastIndicatorPageCount = pageCount
            lastIndicatorSelectedIndex = selectedIndex
            pageIndicatorNeedsSync = false
        }

        private fun pageSnapshot(): NotificationMediaSelectionSnapshot {
            return selection.snapshot(pageCountLimit)
        }

        private fun nativeOrder(): List<String> {
            val sort = sortUtilsRef.get() ?: return emptyList()
            val sortList = NotificationMediaSingleCardSwitcherHooker.readField(
                sort,
                "mediaDataList"
            ) as? Iterable<*> ?: return emptyList()
            return sortList.mapNotNull { it?.let(accessor::sortKey) }.distinct()
        }

        private fun nativeTopKey(): String? {
            val data = NotificationMediaSingleCardSwitcherHooker.readField(
                layoutControllerRef.get(),
                "topMediaData"
            ) ?: return null
            return accessor.notificationKey(data)
        }

        private fun nativeTopData(): Any? {
            return NotificationMediaSingleCardSwitcherHooker.readField(
                layoutControllerRef.get(),
                "topMediaData"
            )
        }

        private fun isSeekBarTouch(currentPlayer: View, event: MotionEvent): Boolean {
            val view = seekBars[currentPlayer] ?: seekBar?.get() ?: return false
            if (!view.isShown || view.width <= 0 || view.height <= 0) return false
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val x = event.rawX
            val y = event.rawY
            return x >= location[0] && x < location[0] + view.width &&
                y >= location[1] && y < location[1] + view.height
        }

        private fun calculateSwitchThreshold(view: View): Float {
            val density = view.resources.displayMetrics.density
            return maxOf(
                touchSlop * 2f,
                MIN_SWITCH_DISTANCE_DP * density,
                view.width * SWITCH_DISTANCE_FRACTION
            )
        }

        private fun resetTouch(view: View? = null, releaseParent: Boolean = true) {
            if (view != null) nativeGestureBlocker.reset(view, releaseParent)
            touchDownX = 0f
            touchDownY = 0f
            touchThreshold = 0f
            touchIgnored = false
            touchDirectionDecided = false
            touchHorizontal = false
            touchConsumed = false
        }

        private fun runOnMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post(block)
            }
        }

        private fun ensureTouchHookForState(currentPlayer: View) {
            val module = NotificationMediaSingleCardSwitcherHooker.moduleForTouchHook
                ?: return
            NotificationMediaSingleCardSwitcherHooker.ensureTouchHook(module, currentPlayer)
        }
    }

    private class ReflectedMediaDataAccessor : NotificationMediaDataAccessor {
        private data class DataFields(
            val notificationKey: Field?,
            val token: Field?,
            val active: Field?,
            val isPlaying: Field?
        )

        private data class SortKeyFields(
            val key: Field?,
            val data: Field?
        )

        private val dataFields = Collections.synchronizedMap(
            WeakHashMap<Class<*>, DataFields>()
        )
        private val sortKeyFields = Collections.synchronizedMap(
            WeakHashMap<Class<*>, SortKeyFields>()
        )

        override fun notificationKey(data: Any): String? =
            fields(data).notificationKey?.get(data) as? String

        override fun sessionToken(data: Any): Any? =
            fields(data).token?.get(data)

        override fun isActive(data: Any): Boolean =
            (fields(data).active?.get(data) as? Boolean) ?: true

        override fun isPlaying(data: Any): Boolean? =
            fields(data).isPlaying?.get(data) as? Boolean

        override fun sortKey(sortKey: Any): String? =
            sortFields(sortKey).key?.get(sortKey) as? String

        override fun sortData(sortKey: Any): Any? =
            sortFields(sortKey).data?.get(sortKey)

        private fun fields(data: Any): DataFields {
            return dataFields.getOrPut(data.javaClass) {
                DataFields(
                    notificationKey = NotificationMediaSingleCardSwitcherHooker.findField(
                        data.javaClass,
                        "notificationKey"
                    ),
                    token = NotificationMediaSingleCardSwitcherHooker.findField(
                        data.javaClass,
                        "token"
                    ),
                    active = NotificationMediaSingleCardSwitcherHooker.findField(
                        data.javaClass,
                        "active"
                    ),
                    isPlaying = NotificationMediaSingleCardSwitcherHooker.findField(
                        data.javaClass,
                        "isPlaying"
                    )
                )
            }
        }

        private fun sortFields(sortKey: Any): SortKeyFields {
            return sortKeyFields.getOrPut(sortKey.javaClass) {
                SortKeyFields(
                    key = NotificationMediaSingleCardSwitcherHooker.findField(
                        sortKey.javaClass,
                        "key"
                    ),
                    data = NotificationMediaSingleCardSwitcherHooker.findField(
                        sortKey.javaClass,
                        "data"
                    )
                )
            }
        }
    }

    private var moduleForTouchHook: XposedModule? = null
}
