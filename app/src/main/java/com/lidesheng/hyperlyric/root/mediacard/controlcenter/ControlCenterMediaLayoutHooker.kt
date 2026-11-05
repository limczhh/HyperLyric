package com.lidesheng.hyperlyric.root.mediacard.controlcenter

import android.content.SharedPreferences
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal object ControlCenterMediaLayoutHooker {
    private const val TAG = "ControlCenterMediaLayout"
    private const val PANEL_PACKAGE = "miui.systemui.controlcenter.panel.secondary.media."
    private var artworkDrawHook: HookHandle? = null
    private var landscapeLayoutHook: HookHandle? = null
    private val installed = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val detailAdapterInstalled = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val detailsInstalled = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val nativeApis = Collections.synchronizedMap(WeakHashMap<ClassLoader, NativeApi>())
    private val pendingDetails = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    fun hook(module: XposedModule, loader: ClassLoader, prefs: SharedPreferences) {
        val options = ControlCenterMediaLayoutOptions(
            immersiveCoverLayout = prefs.getBoolean(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_SQUARE_COVER,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_SQUARE_COVER
            ),
            progressHeadGlow = prefs.getBoolean(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_PROGRESS_HEAD_GLOW,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_PROGRESS_HEAD_GLOW
            ),
            customActions = prefs.getBoolean(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_CUSTOM_ACTIONS,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_CUSTOM_ACTIONS
            ),
            coverFlip = prefs.getInt(
                RootConstants.KEY_HOOK_CONTROL_CENTER_MEDIA_COVER_FLIP,
                RootConstants.DEFAULT_HOOK_CONTROL_CENTER_MEDIA_COVER_FLIP
            ).coerceIn(0, 2)
        )
        if (!options.immersiveCoverLayout && !options.progressHeadGlow &&
            options.coverFlip == RootConstants.CONTROL_CENTER_MEDIA_COVER_FLIP_DEFAULT) return
        if (options.immersiveCoverLayout && options.customActions) {
            ControlCenterMediaActionSource.install(module, loader)
        }
        hookMiPlayDetailAdapter(module, loader)
        val api = hookDetails(module, loader, options) ?: return
        // A detail host need not load the Control Center delegate or animator.
        // Install their transitions separately, after the common detail hooks.
        hookPanel(module, loader, api)
    }

    private fun hookDetails(
        module: XposedModule,
        loader: ClassLoader,
        options: ControlCenterMediaLayoutOptions,
    ): NativeApi? {
        val content = runCatching {
            loader.loadClass("com.android.systemui.QSControlMiPlayDetailContent")
        }.getOrNull() ?: return null
        val owner = content.classLoader ?: return null
        nativeApis[owner]?.let { return it }
        if (!detailsInstalled.add(owner)) return null
        val handles = mutableListOf<HookHandle>()
        return runCatching {
            val header = owner.loadClass("com.android.systemui.QSControlMiPlayDetailHeader")
            val frame = runCatching {
                owner.loadClass("miui.systemui.controlcenter.widget.MaxHeightFrameLayout")
            }.getOrNull()
            val api = NativeApi(content, frame, options)
            handles += ControlCenterMediaCoverFlipHooker.install(module, owner, header, options)
            if (options.progressHeadGlow) {
                handles += ControlCenterMediaProgressGlow.installOverflowDrawing(module, owner)
            }
            fun install(method: Method, name: String, hooker: Hooker) {
                method.isAccessible = true
                handles += module.managedHook(method, "media.controlcenter.layout.$name", hooker)
            }
            listOfNotNull(frame, content).forEach { type ->
                install(type.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType), "measure.${type.simpleName}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val view = chain.thisObject as? View ?: return chain.proceed()
                        val state = view.layoutState() ?: return chain.proceed()
                        // A stale snapshot may have survived native reparenting.
                        // Repair it before either our sizing or native measurement,
                        // including while the native transition owns the bounds.
                        protect { MediaLayoutParams.validateTree(state.header) }
                        // Native animation exclusively controls all intermediate bounds.
                        if (state.isTransitioning) return chain.proceed()
                        val sizing = state.panelSizing ?: return chain.proceed()
                        protect {
                            sizing.prepareForMeasure(view, chain.args[0] as Int)
                            if (!sizing.forcing && sizing.active) {
                                state.contentView?.let(api::refreshDeviceHeight)
                            }
                        }
                        val height = sizing.heightFor(view) ?: return chain.proceed()
                        return chain.proceed(arrayOf(chain.args[0],
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)))
                    }
                })
            }
            header.declaredMethods.filter { it.name == "setCover" }.forEach { method ->
                install(method, "header.cover_content", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        protect { (chain.thisObject as? View)?.layoutState()?.onArtworkChanged() }
                        return result
                    }
                })
            }
            header.declaredMethods.filter {
                it.name in setOf("updateSize", "onOrientationChanged", "prepareShowPanel", "onMaterialChanged")
            }.forEach { method ->
                install(method, "header.${method.name}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val state = (chain.thisObject as? View)?.layoutState()
                        if (state?.isTransitioning == true) return chain.proceed()
                        return nativeLayoutUpdate(chain, state, api)
                    }
                })
            }
            val contentClasses = listOf(content) + listOf(
                "com.android.systemui.QSControlMiPlayDetailContentSupportLand",
                "com.android.systemui.QSControlMiPlayDetailContentFlip",
            ).mapNotNull { runCatching { owner.loadClass(it) }.getOrNull() }
            contentClasses.forEach { type ->
                type.declaredMethods.filter {
                    it.name == "onConfigurationChanged" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == Configuration::class.java
                }.forEach { method ->
                    install(method, "orientation.${type.simpleName}", object : Hooker {
                        override fun intercept(chain: Chain): Any? {
                            val state = (chain.thisObject as? View)?.layoutState()
                            return nativeLayoutUpdate(chain, state, api)
                        }
                    })
                }
                type.declaredMethods.filter { it.name == "getRVMaxHeight" && it.parameterCount == 0 }.forEach { method ->
                    install(method, "device_space", object : Hooker {
                        override fun intercept(chain: Chain): Any? {
                            val native = chain.proceed()
                            val state = (chain.thisObject as? View)?.layoutState()
                                ?.takeIf { it.isApplied } ?: return native
                            return state.landscapeDeviceHeight
                                ?: state.panelSizing?.takeIf { it.active }?.availableDeviceHeight() ?: native
                        }
                    })
                }
                type.declaredMethods.filter { it.name == "resetUILayout" }.forEach { method ->
                    install(method, "reset_layout", object : Hooker {
                        override fun intercept(chain: Chain): Any? {
                            val state = (chain.thisObject as? View)?.layoutState()
                            return nativeLayoutUpdate(chain, state, api)
                        }
                    })
                }
            }
            install(content.getDeclaredMethod("updateHeight", ArrayList::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType), "list_height", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val result = chain.proceed()
                    protect {
                        val view = chain.thisObject as? View ?: return@protect
                        view.layoutState()?.panelSizing?.updateDeviceHeight(api.targetDeviceHeight(view))
                    }
                    return result
                }
            })
            ensureArtworkDrawHook(module)
            ensureLandscapeLayoutHook(module)
            nativeApis[owner] = api
            api
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            detailsInstalled.remove(owner)
            HookLogger.w(TAG, "安装MiPlay详情Hook失败: loader=$owner", error)
        }.getOrNull()
    }

    private fun hookPanel(module: XposedModule, loader: ClassLoader, api: NativeApi) {
        val delegate = runCatching { loader.loadClass(PANEL_PACKAGE + "MediaPanelDelegate") }.getOrNull() ?: return
        val owner = delegate.classLoader ?: return
        if (!installed.add(owner)) return
        val handles = mutableListOf<HookHandle>()
        runCatching {
            val animator = owner.loadClass(PANEL_PACKAGE + "MediaPanelAnimator")
            val panelController = owner.loadClass(PANEL_PACKAGE + "MediaPanelController")
            val controllerBase = owner.loadClass("miui.systemui.controlcenter.panel.secondary.SecondaryPanelControllerBase")
            val delegateBase = controllerBase.getDeclaredField("delegateBase").apply { isAccessible = true }
            val getBlurBg = controllerBase.getDeclaredMethod("getBlurBg").apply { isAccessible = true }
            fun install(method: Method, name: String, hooker: Hooker) {
                method.isAccessible = true
                handles += module.managedHook(method, "media.controlcenter.layout.$name", hooker)
            }
            install(delegate.getDeclaredMethod("initView"), "create", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val result = chain.proceed()
                    protect { api.attach(chain.thisObject ?: return@protect) }
                    return result
                }
            })
            delegate.declaredMethods.filter { it.name == "prepareShow" && !it.isBridge }.forEach { method ->
                install(method, "prepare", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        protect { api.resetIfUnlocked(chain.thisObject ?: return@protect) }
                        val result = chain.proceed()
                        protect { api.attach(chain.thisObject ?: return@protect) }
                        return result
                    }
                })
            }
            // startShow/startHide suppress the media frame's layout before invoking the
            // animator. Resolve native parent constraints before that lock, not from
            // calculateViewValues, where ViewGroup.layout can no longer lay out children.
            controllerBase.declaredMethods.filter {
                it.name in setOf("startShow", "startHide") && !it.isBridge
            }.forEach { method ->
                install(method, "prepare_transition.${method.name}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        protect {
                            val instance = chain.thisObject ?: return@protect
                            if (panelController.isInstance(instance)) {
                                delegateBase.get(instance)?.let { delegate ->
                                    api.setCustomActionsVisible(delegate, method.name == "startShow")
                                    api.setArtworkBackground(delegate, getBlurBg.invoke(instance) as View)
                                    api.prepareTransition(delegate)
                                }
                            }
                        }
                        return chain.proceed()
                    }
                })
            }
            controllerBase.declaredMethods.filter {
                it.name in setOf("cancelPrepare", "forceToHide") && !it.isBridge
            }.forEach { method ->
                install(method, "cancel_transition.${method.name}", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        protect {
                            val instance = chain.thisObject ?: return@protect
                            if (panelController.isInstance(instance)) {
                                delegateBase.get(instance)?.let(api::finishTransition)
                            }
                        }
                        return result
                    }
                })
            }
            install(controllerBase.getDeclaredMethod("onShown"), "expanded_handoff", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val result = chain.proceed()
                    protect {
                        val instance = chain.thisObject ?: return@protect
                        if (panelController.isInstance(instance)) {
                            delegateBase.get(instance)?.let(api::completeExpandedLayout)
                        }
                    }
                    return result
                }
            })
            val controller = animator.getDeclaredField("contentController").apply { isAccessible = true }
            val animationSize = animator.getDeclaredField("size").apply { isAccessible = true }
            val isExpanded = runCatching { animator.getMethod("isExpanded") }.getOrNull()
            val lastAnimValue = animator.getDeclaredField("lastAnimValue").apply { isAccessible = true }
            val landscapeAccess = LandscapeMediaPanelTransition.Access()
            val landscapeTransitions = WeakHashMap<Any, LandscapeMediaPanelTransition>()
            val baseAnimator = owner.loadClass("miui.systemui.controlcenter.panel.secondary.SecondaryPanelAnimatorBase")
            val baseFrame: (Any) -> Unit = if (module.getApiVersion() >= 102) {
                val invoke = module.getInvoker(baseAnimator.getDeclaredMethod("frameCallback"))
                val callback: (Any) -> Unit = { instance -> invoke.invokeSpecial(instance); Unit }
                callback
            } else {
                // API 101 lacks the nonvirtual invoker. In the inspected native
                // class the base callback only releases updateScheduled.
                val scheduled = baseAnimator.getDeclaredField("updateScheduled").apply { isAccessible = true }
                val callback: (Any) -> Unit = { instance -> scheduled.setBoolean(instance, false) }
                callback
            }
            fun captureLandscape(instance: Any, delegate: Any): LandscapeMediaPanelTransition? {
                landscapeTransitions.remove(instance)
                if (!api.isLandscapeApplied(delegate)) return null
                return LandscapeMediaPanelTransition.capture(instance, landscapeAccess,
                    api.transitionGeneration(delegate), baseFrame)?.also {
                    landscapeTransitions[instance] = it
                }
            }
            // Native cache reuse only compares the two vertical window positions. Our
            // expanded artwork and centered labels require fresh widths and X positions.
            // Only invalidate when native actually calculates; interrupted transitions
            // that intentionally retain animValue must keep that live endpoint.
            install(animator.getDeclaredMethod("calculateViewValues"), "animation_target", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val instance = chain.thisObject
                    val delegate = instance?.let(controller::get)
                    val previous = landscapeTransitions[instance]
                    // A reversal must retain the expanded snapshot, even if this
                    // ROM recalculates while the source card is not suppressed.
                    if (instance != null && delegate != null && api.isLandscapeApplied(delegate) &&
                        previous != null && previous.generation == api.transitionGeneration(delegate) &&
                        previous.matches(instance)) return null
                    protect {
                        controller.get(chain.thisObject)?.let { delegate ->
                            if (api.isApplied(delegate)) lastAnimValue.set(chain.thisObject, null)
                        }
                    }
                    val result = chain.proceed()
                    protect {
                        val instance = chain.thisObject ?: return@protect
                        val delegate = controller.get(instance) ?: return@protect
                        captureLandscape(instance, delegate)
                    }
                    return result
                }
            })
            install(animator.getDeclaredMethod("frameCallback"), "animation_frame", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val instance = chain.thisObject
                    val delegate = instance?.let(controller::get)
                    var transition = landscapeTransitions[instance]
                    // Native collapse can deliberately retain animValue when the
                    // compact source is suppressed. Its expanded layout was still
                    // resolved before startHide; capture it before ANY geometry is
                    // written, rather than falling back to the old column formula.
                    if (instance != null && delegate != null && api.isLandscapeApplied(delegate) &&
                        transition?.generation != api.transitionGeneration(delegate)) {
                        protect {
                            transition = captureLandscape(instance, delegate)
                        }
                    }
                    val current = transition
                    if (current != null && delegate != null && api.isLandscapeApplied(delegate) &&
                        current.generation == api.transitionGeneration(delegate) && current.matches(instance)) {
                        current.render(instance)
                        api.clipTransition(delegate)
                        return null
                    }
                    val result = chain.proceed()
                    protect { controller.get(chain.thisObject)?.let {
                        api.updateArtworkBlend(it, animationSize.getFloat(chain.thisObject))
                        api.clipTransition(it)
                    } }
                    return result
                }
            })
            install(animator.getDeclaredMethod("onAnimComplete"), "animation_complete", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val result = chain.proceed()
                    protect { controller.get(chain.thisObject)?.let {
                        api.finishTransition(it)
                        if (isExpanded?.invoke(chain.thisObject) == true) {
                            api.completeExpandedLayout(it)
                        }
                    } }
                    return result
                }
            })
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            installed.remove(owner)
            HookLogger.w(TAG, "跳过不支持的控制中心媒体布局: ${error.message}", error)
        }
    }

    /**
     * The notification modal uses MiPlayDetailAdapter to inflate a detached
     * detail view. Its host assigns the root LayoutParams after this returns.
     */
    private fun hookMiPlayDetailAdapter(module: XposedModule, loader: ClassLoader) {
        val adapter = runCatching {
            loader.loadClass("com.android.systemui.qs.miplay.MiPlayDetailAdapter")
        }.getOrNull() ?: return
        if (!detailAdapterInstalled.add(adapter.classLoader ?: loader)) return
        runCatching {
            val method = adapter.getDeclaredMethod("createDetailView", Context::class.java,
                View::class.java, ViewGroup::class.java).apply { isAccessible = true }
            module.managedHook(method, "media.controlcenter.layout.detail_adapter", object : Hooker {
                override fun intercept(chain: Chain): Any? {
                    val result = chain.proceed()
                    protect {
                        val view = result as? View ?: return@protect
                        if (view.parent != null && view.layoutParams != null) {
                            nativeApis[view.javaClass.classLoader]?.attachModal(view)
                        } else if (pendingDetails.add(view)) {
                            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(view: View) {
                                    view.removeOnAttachStateChangeListener(this)
                                    pendingDetails.remove(view)
                                    protect {
                                        nativeApis[view.javaClass.classLoader]?.attachModal(view)
                                    }
                                }
                                override fun onViewDetachedFromWindow(view: View) = Unit
                            })
                        }
                    }
                    return result
                }
            })
            // updateContainerHeight runs outside the plugin after its orientation
            // callback and can otherwise put WRAP_CONTENT back on our modal host.
            val modal = adapter.classLoader!!.loadClass("com.android.systemui.statusbar.notification.modal.ModalQSControlDetail")
            val detailContent = modal.getDeclaredField("mDetailContent").apply { isAccessible = true }
            module.managedHook(modal.getDeclaredMethod("updateContainerHeight").apply { isAccessible = true },
                "media.controlcenter.layout.modal_size", object : Hooker {
                    override fun intercept(chain: Chain): Any? {
                        val result = chain.proceed()
                        protect {
                            val host = detailContent.get(chain.thisObject) as? ViewGroup ?: return@protect
                            for (index in 0 until host.childCount) {
                                host.getChildAt(index).layoutState()?.prepareModalLandscape()
                            }
                        }
                        return result
                    }
                })
        }.onFailure { error ->
            detailAdapterInstalled.remove(adapter.classLoader ?: loader)
            HookLogger.w(TAG, "跳过 MiPlay QS 详情入口 Hook: ${error.message}")
        }
    }

    private fun nativeLayoutUpdate(chain: Chain, state: ControlCenterMediaLayout?, api: NativeApi): Any? {
        protect { state?.beginNativeUpdate() }
        return try {
            chain.proceed()
        } finally {
            protect {
                if (state?.endNativeUpdate() == true) {
                    state.contentView?.let { api.invalidateDeviceBudget(it); api.prepareContent(it) }
                    state.apply()
                }
            }
        }
    }

    private fun View.layoutState() = getTag(R.id.control_center_media_layout_state) as? ControlCenterMediaLayout

    private fun ensureLandscapeLayoutHook(module: XposedModule) = synchronized(this) {
        if (landscapeLayoutHook != null) return@synchronized
        val method = LinearLayout::class.java.getDeclaredMethod("onLayout", Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType).apply { isAccessible = true }
        landscapeLayoutHook = module.managedHook(method, "media.controlcenter.landscape_layout", object : Hooker {
            override fun intercept(chain: Chain): Any? {
                val view = chain.thisObject as? View ?: return chain.proceed()
                val state = view.layoutState() ?: return chain.proceed()
                if (view !== state.contentView || !state.isLandscapeApplied) return chain.proceed()
                var handled = false
                protect { handled = state.layoutLandscapeBounds() }
                return if (handled) null else chain.proceed()
            }
        })
    }

    private fun ensureArtworkDrawHook(module: XposedModule) = synchronized(this) {
        if (artworkDrawHook != null) return@synchronized
        val method = ViewGroup::class.java.getDeclaredMethod("drawChild", Canvas::class.java,
            View::class.java, Long::class.javaPrimitiveType).apply { isAccessible = true }
        artworkDrawHook = module.managedHook(method, "media.controlcenter.artwork_blend", object : Hooker {
            override fun intercept(chain: Chain): Any? {
                val child = chain.args[1] as? View ?: return chain.proceed()
                if (child !is ImageView && child !is ViewGroup) return chain.proceed()
                val state = child.layoutState() ?: return chain.proceed()
                if (chain.thisObject !== child.parent) return chain.proceed()
                val canvas = chain.args[0] as? Canvas ?: return chain.proceed()
                return state.drawArtwork(child, canvas) { chain.proceed() }
            }
        })
    }
    private inline fun protect(block: () -> Unit) {
        runCatching(block).onFailure { HookLogger.w(TAG, "应用控制中心媒体布局失败", it) }
    }

    private class NativeApi(
        content: Class<*>,
        private val frameClass: Class<*>?,
        private val options: ControlCenterMediaLayoutOptions,
    ) {
        private val pluginLoader = content.classLoader
        private val delegate by lazy { requireNotNull(pluginLoader).loadClass(PANEL_PACKAGE + "MediaPanelDelegate") }
        private val detailClass = content
        private val getDetailHeader = content.getDeclaredMethod("getMHeader")
        private fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
        private val headerField by lazy { delegate.field("headerLayout") }
        private val headerPaddingTop by lazy { delegate.field("headerPaddingTop") }
        private val headerPaddingStartEnd by lazy { delegate.field("headerPaddingStartEnd") }
        private val contentField by lazy { delegate.field("contentView") }
        private val titleView by lazy { delegate.getDeclaredMethod("getTitleView") }
        private val titleTypeface by lazy { delegate.getDeclaredMethod("getTitleOriginalTypeface") }
        private val maxHeight = content.field("maxHeight")
        private val resetMaxHeight = content.getDeclaredMethod("resetMaxHeight")
        private val listItems = content.field("mListItems")
        private val targetHeight = content.field("mTargetHeight")
        private val recycler = content.getDeclaredMethod("getMRecycler")
        private val updateHeight = content.getDeclaredMethod("updateHeight", ArrayList::class.java,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        private val deviceHeightBudgets = WeakHashMap<View, Int>()
        private val getFrameLimit = frameClass?.getDeclaredMethod("getMaxHeight")
        private val setFrameLimit = frameClass?.getDeclaredMethod("setMaxHeight", Int::class.javaPrimitiveType)

        fun attach(delegate: Any) {
            val header = headerField.get(delegate) as? LinearLayout ?: return
            val content = contentField.get(delegate) as? View ?: return
            attachContent(header, content, panelHost = true)
        }

        fun attachModal(content: View) {
            if (!detailClass.isInstance(content) || content.parent == null || content.layoutParams == null) return
            if (content.layoutState()?.let { it.panelSizing != null && it.isApplied } == true) return
            val header = getDetailHeader.invoke(content) as? LinearLayout ?: return
            val state = attachContent(header, content)
            state.setArtworkBackground((content.parent as? View) ?: content)
            state.apply()
            content.requestLayout()
        }

        fun attachContent(header: LinearLayout, host: View, panelHost: Boolean = false): ControlCenterMediaLayout {
            val content = (sequenceOf(host) + generateSequence(header.parent as? View) { it.parent as? View })
                .firstOrNull { detailClass.isInstance(it) }
                ?: error("Missing QSControlMiPlayDetailContent for ${header.javaClass.name}")
            check(content.parent != null && content.layoutParams != null) {
                "MiPlay detail must be added to its native host before layout initialization"
            }
            val previous = header.layoutState()
            if (previous?.isTransitioning == true) return previous
            val state = previous ?: ControlCenterMediaLayout.create(
                header,
                content as? ViewGroup,
                options
            )
            if (panelHost) state.isPanelHost = true
            if (options.immersiveCoverLayout && state.panelSizing == null) {
                val frame = generateSequence(content.parent as? View) { it.parent as? View }
                    .firstOrNull { frameClass?.isInstance(it) == true }
                fun dimension(name: String): Int {
                    val id = content.resources.getIdentifier(name, "dimen", "miui.systemui.plugin")
                    return if (id != 0) content.resources.getDimensionPixelSize(id) else 0
                }
                state.panelSizing = ControlCenterMediaPanelSizing(state, content,
                    maxHeight.getInt(content), frame?.let { getFrameLimit?.invoke(it) as? Int } ?: maxHeight.getInt(content),
                    { maxHeight.setInt(content, it) }, { height -> frame?.let { setFrameLimit?.invoke(it, height) }; Unit },
                    dimension("miplay_detail_item_height") + dimension("miplay_detail_recycler_padding_vertical"),
                    { ((recycler.invoke(content) as View).layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0 },
                    { (resetMaxHeight.invoke(content) as Int).let { it to it } },
                    frame as? ViewGroup, recycler.invoke(content) as View)
                frame?.setTag(R.id.control_center_media_layout_state, state)
            }
            header.setTag(R.id.control_center_media_layout_state, state)
            content.setTag(R.id.control_center_media_layout_state, state)
            if (artworkDrawHook != null) pluginLoader?.let(state::enableArtworkBlend)
            prepareContent(content)
            state.refreshMediaFeatures()
            if (previous == null) {
                header.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                    if (r - l != or - ol || b - t != ob - ot)
                        content.post { protect { refreshDeviceHeight(content) } }
                }
            }
            return state
        }

        private fun state(delegate: Any) = (headerField.get(delegate) as? View)?.layoutState()
        fun isApplied(delegate: Any) = state(delegate)?.isApplied == true
        fun isLandscapeApplied(delegate: Any) = state(delegate)?.isLandscapeApplied == true
        fun transitionGeneration(delegate: Any) = state(delegate)?.transitionGeneration ?: -1
        fun setCustomActionsVisible(delegate: Any, visible: Boolean) {
            state(delegate)?.setCustomActionsVisible(visible)
        }
        fun setArtworkBackground(delegate: Any, background: View) { state(delegate)?.setArtworkBackground(background) }
        fun updateArtworkBlend(delegate: Any, progress: Float) { state(delegate)?.syncArtworkBlend(progress) }
        fun resetIfUnlocked(delegate: Any) {
            state(delegate)?.let { state ->
                if (state.panelSizing?.frameView?.isLayoutSuppressed != true) state.finishTransition()
            }
        }
        fun clipTransition(delegate: Any) { state(delegate)?.clipTransition() }
        fun finishTransition(delegate: Any) {
            state(delegate)?.let { it.finishTransition(); it.endPanelAnimation() }
        }

        fun prepareTransition(delegate: Any) {
            val previous = state(delegate)
            val currentFrame = previous?.panelSizing?.frameView
            if (currentFrame?.isLayoutSuppressed == true) {
                // Reversing an in-flight animation: do not unlock or remeasure it.
                previous.beginTransition(currentFrame)
                return
            }
            previous?.finishTransition()
            // expand() performs this native typeface restoration after startShow locks
            // layout. Do the identical restoration before measuring centered WRAP_CONTENT
            // labels, so their animated endpoint uses the settled text metrics as well.
            (titleView.invoke(delegate) as? TextView)?.typeface = titleTypeface.invoke(delegate) as? Typeface
            attach(delegate)
            val state = state(delegate) ?: return
            resolveExpandedLayout(delegate, state)
            state.preparePanelTransition()
            if (state.isLandscapeApplied) {
                state.layoutLandscapeBounds()
                (contentField.get(delegate) as? View)?.let(::refreshDeviceHeight)
            }
            if (!state.isApplied) return
            // Native device-icon coordinates subtract the delegate's cached paddings.
            headerPaddingTop.setInt(delegate, state.header.paddingTop)
            headerPaddingStartEnd.setInt(delegate, state.header.paddingStart)
            state.panelSizing?.frameView?.let(state::beginTransition)
        }

        fun completeExpandedLayout(delegate: Any) {
            val current = state(delegate) ?: return
            if (current.header.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                current.finishTransition()
                current.markPanelShown()
                return
            }
            current.markPanelShown()
            val state = current.takeIf { it.isApplied && it.isTransitioning } ?: return
            // Native onShown has released suppressLayout and run its detail-showing
            // callbacks. Resolve their requested layout synchronously in that same
            // handoff, before drawing, instead of displaying an old animation frame
            // and waiting for the next traversal to recompute the static layout.
            state.finishTransition()
            resolveExpandedLayout(delegate, state)
            state.syncArtworkBlend(1f)
        }

        private fun resolveExpandedLayout(delegate: Any, state: ControlCenterMediaLayout) {
            val sizing = state.panelSizing ?: return
            val content = contentField.get(delegate) as? View ?: return
            sizing.layoutFromParent()
            // A child's resolved width can be smaller than either a resource width
            // or its parent's AT_MOST spec. Remeasure height and parent constraints
            // against that actual width before native captures the target bounds.
            val actualWidth = state.header.measuredWidth
            if (actualWidth > 0) sizing.prepare(actualWidth)
            refreshDeviceHeight(content)
            sizing.layoutFromParent()
        }

        fun prepareContent(content: View) {
            if (!detailClass.isInstance(content)) return
            val state = content.layoutState() ?: return
            if (state.isTransitioning) return
            if (!options.immersiveCoverLayout) {
                state.refreshMediaFeatures()
                return
            }
            val dimensionName = if (content.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                "miplay_detail_content_land_width" else "miplay_detail_content_width"
            val id = content.resources.getIdentifier(dimensionName, "dimen", "miui.systemui.plugin")
            val width = if (id != 0) content.resources.getDimensionPixelSize(id) else content.measuredWidth
            state.panelSizing?.prepare(width)
            if (content.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) state.apply()
            if (state.panelSizing?.active == true || state.isLandscapeApplied) refreshDeviceHeight(content)
        }

        fun targetDeviceHeight(content: View): Int =
            if (detailClass.isInstance(content)) targetHeight.getInt(content) else 0
        fun invalidateDeviceBudget(content: View) { deviceHeightBudgets.remove(content) }
        fun refreshDeviceHeight(content: View) {
            if (!detailClass.isInstance(content)) return
            val state = content.layoutState() ?: return
            if (state.isTransitioning) return
            val budget = state.landscapeDeviceHeight
                ?: state.panelSizing?.takeIf { it.active }?.availableDeviceHeight() ?: return
            // The native data observer already updates device changes. We only
            // need an extra refresh when our header changes the available space.
            if (deviceHeightBudgets[content] == budget) return
            val items = listItems.get(content) ?: return
            deviceHeightBudgets[content] = budget
            try {
                updateHeight.invoke(content, items, false, false)
            } catch (error: Throwable) {
                deviceHeightBudgets.remove(content)
                throw error
            }
        }
    }
}
