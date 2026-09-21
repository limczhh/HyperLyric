package com.lidesheng.hyperlyric.root.mediacard.notification

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.MediaAmbientFlowPalette
import com.lidesheng.hyperlyric.root.mediacard.MediaAmbientFlowPaletteExtractor
import com.lidesheng.hyperlyric.root.mediacard.MediaArtworkSampler
import com.lidesheng.hyperlyric.root.mediacard.MediaCardRuntimeConfig
import com.lidesheng.hyperlyric.root.mediacard.background.MediaFlowArtwork
import com.lidesheng.hyperlyric.root.mediacard.background.MediaFlowBackgroundView
import com.lidesheng.hyperlyric.root.mediacard.background.MediaFlowOverlayLayout
import com.lidesheng.hyperlyric.root.mediacard.background.MediaFlowTone
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaBackgroundController
import com.lidesheng.hyperlyric.root.mediacard.notification.style.NotificationMediaForegroundStyler
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object NotificationMediaAmbientFlowHooker {
    private const val TAG = "NotificationMediaAmbientFlowHooker"
    private const val VIEW_TAG = "hyperlyric.notification_media_ambient_flow"
    private val controllerClassNames = listOf(
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewController"
    )

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val states = Collections.synchronizedMap(WeakHashMap<Any, ControllerState>())
    private val themeStates = Collections.synchronizedMap(WeakHashMap<Any, ControllerThemeState>())
    private val nativeApis =
        Collections.synchronizedMap(WeakHashMap<ClassLoader, NativeMusicBgApi>())
    private val themeApis = Collections.synchronizedMap(WeakHashMap<ClassLoader, CardThemeApi>())
    private val themeUnavailableClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val nativeUnavailableClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val colorExecutor = newColorExecutor()

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

        val controllerClass = controllerClassNames.firstNotNullOfOrNull { className ->
            runCatching { classLoader.loadClass(className) }.getOrNull()
        }
        if (controllerClass == null) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "通知中心媒体控制器不可用")
            return
        }
        var installed = 0
        val installedNativeUpdates = mutableSetOf<String>()
        TARGET_METHOD_NAMES
            .flatMap { methodName -> findNearestMethods(controllerClass, methodName) }
            .filter(::isTargetMethod)
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    xposedModule.managedHook(
                        executable = method,
                        capability = "media.notification.ambient.${method.name}",
                        hooker = hookerFor(method) ?: return@runCatching,
                    )
                    installed++
                    if (method.name in NATIVE_BACKGROUND_UPDATE_METHODS) {
                        installedNativeUpdates.add(method.name)
                    }
                }.onFailure { error ->
                    HookLogger.e(TAG, "安装通知中心媒体 Hook 失败: method=${method.name}", error)
                }
            }
        NotificationMediaBackgroundController.setNativeHooksAvailable(
            classLoader,
            installedNativeUpdates.containsAll(NATIVE_BACKGROUND_UPDATE_METHODS)
        )
        if (!installedNativeUpdates.containsAll(NATIVE_BACKGROUND_UPDATE_METHODS)) {
            HookLogger.w(TAG, "通知中心原生背景接口不完整，跳过自定义背景 Hook")
        }

        runCatching {
            val seekBarClass = classLoader.loadClass(HYPER_PROGRESS_SEEK_BAR_CLASS)
            findNearestMethods(seekBarClass, "onDraw").forEach { method ->
                method.isAccessible = true
                xposedModule.managedHook(
                    executable = method,
                    capability = "media.notification.progress_draw",
                    hooker = ProgressDrawHook(),
                )
                installed++
            }
        }.onFailure { error ->
            HookLogger.w(TAG, "通知中心进度条接口不可用: reason=${error.message}")
        }

        if (installed == 0) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "未找到兼容的通知中心媒体控制方法")
        } else {
            HookLogger.d(TAG, "通知中心媒体流光 Hook 已初始化: methods=$installed")
        }
    }

    fun isTargetMethod(method: Method): Boolean {
        if (method.declaringClass.name == HYPER_PROGRESS_SEEK_BAR_CLASS) {
            return method.name == "onDraw" && method.parameterCount == 1
        }
        if (!method.declaringClass.name.startsWith(CONTROLLER_PACKAGE)) return false
        return when (method.name) {
            "attach", "detach" -> true
            "bindMediaData" -> method.parameterTypes.isNotEmpty()
            "updateForegroundColors", "updateMediaBackground" -> method.parameterCount == 0
            else -> false
        }
    }

    fun hookerFor(executable: Executable): Hooker? {
        val method = executable as? Method ?: return null
        if (!isTargetMethod(method)) return null
        return when (method.name) {
            "attach" -> ControllerHook(Action.ATTACH)
            "detach" -> ControllerHook(Action.DETACH)
            "bindMediaData" -> ControllerHook(Action.BIND)
            "updateForegroundColors", "updateMediaBackground" ->
                NativeBackgroundUpdateHook(method.name)
            "onDraw" -> ProgressDrawHook()
            else -> null
        }
    }

    class ControllerHook(private val action: Action) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject ?: return chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) {
                if (action == Action.DETACH) {
                    removeView(controller, forgetState = true)
                    NotificationMediaBackgroundController.onDetach(controller)
                    restoreCardTheme(controller)
                }
                val result = chain.proceed()
                return result
            }
            if (action == Action.DETACH) {
                removeView(controller, forgetState = true)
                NotificationMediaBackgroundController.onDetach(controller)
                restoreCardTheme(controller)
            } else {
                runCatching {
                    if (NotificationMediaBackgroundController.isActive(controller)) {
                        restoreCardTheme(controller)
                    } else {
                        prepareCardTheme(controller)
                    }
                }.onFailure {
                    HookLogger.e(TAG, "准备通知中心媒体卡片主题失败", it)
                }
            }
            if (action != Action.DETACH &&
                !NotificationMediaBackgroundController.isActive(controller) &&
                !hasVisibleCustomFlow(controller)
            ) {
                // Let the native bind/attach path establish the current
                // SystemUI colors before a custom foreground is applied again.
                NotificationMediaForegroundStyler.clear(controller)
            }
            val result = chain.proceed()
            runCatching {
                when (action) {
                    Action.ATTACH -> {
                        syncView(controller)
                        applyUnifiedForeground(controller)
                    }

                    Action.BIND -> {
                        NotificationMediaBackgroundController.onBind(
                            controller,
                            chain.args.firstOrNull()
                        )
                        bind(controller, chain.args.firstOrNull())
                        applyUnifiedForeground(controller)
                    }

                    Action.DETACH -> Unit
                }
            }.onFailure { error ->
                HookLogger.e(
                    TAG,
                    "处理通知中心媒体流光失败: action=${action.name.lowercase()}",
                    error
                )
            }
            return result
        }
    }

    enum class Action { ATTACH, DETACH, BIND }

    class NativeBackgroundUpdateHook(
        private val methodName: String
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!MediaCardRuntimeConfig.current.enabled) return chain.proceed()
            val controller = chain.thisObject ?: return chain.proceed()
            val result = if (NotificationMediaBackgroundController.shouldSuppressNativeBackground(
                    controller
                )
            ) {
                null
            } else {
                chain.proceed()
            }
            if (methodName == "updateForegroundColors") {
                NotificationMediaBackgroundController.onUiModeChanged(controller)
                refreshCustomFlowTone(controller)
            }
            applyUnifiedForeground(controller)
            return result
        }
    }

    /** Replays native color callbacks for live controllers after a UI-mode change. */
    fun refreshForUiMode(nativeControllersToSkip: Set<Any> = emptySet()) {
        if (!MediaCardRuntimeConfig.current.enabled) return

        val controllers = synchronized(states) { states.keys.toList() }
        controllers.forEach { controller ->
            runCatching {
                if (controller !in nativeControllersToSkip) {
                    // NativeBackgroundUpdateHook owns the custom background/tone refresh.
                    invokeZeroArgMethods(controller, "updateMediaBackground")
                    invokeZeroArgMethods(controller, "updateForegroundColors")
                }
            }.onFailure { error ->
                HookLogger.w(TAG, "刷新通知中心媒体卡片主题失败", error)
            }
        }
    }

    class ProgressDrawHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (MediaCardRuntimeConfig.current.enabled) {
                chain.thisObject?.let(NotificationMediaForegroundStyler::applySeekBarColor)
            }
            return chain.proceed()
        }
    }

    private fun prepareCardTheme(controller: Any) {
        val api = resolveThemeApi(controller.javaClass.classLoader) ?: return
        api.apply(controller, currentCardTheme())
    }

    private fun restoreCardTheme(controller: Any) {
        val api = resolveThemeApi(controller.javaClass.classLoader) ?: return
        api.restore(controller)
    }

    private fun resolveThemeApi(classLoader: ClassLoader?): CardThemeApi? {
        classLoader ?: return null
        themeApis[classLoader]?.let { return it }
        return runCatching { CardThemeApi.create(classLoader) }
            .onSuccess {
                themeApis[classLoader] = it
                themeUnavailableClassLoaders.remove(classLoader)
            }
            .onFailure {
                if (themeUnavailableClassLoaders.add(classLoader)) {
                    HookLogger.w(TAG, "通知中心媒体主题接口不可用: reason=${it.message}")
                }
            }
            .getOrNull()
    }

    private fun syncView(controller: Any) {
        if (NotificationMediaBackgroundController.isActive(controller)) {
            removeView(controller)
            return
        }
        if (currentMode() != RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED) {
            ensureView(controller)
            states[controller]?.lastMediaData?.let { bind(controller, it) }
        } else {
            removeView(controller)
        }
    }

    private fun bind(controller: Any, mediaData: Any?) {
        val state = states.getOrPut(controller) { ControllerState() }
        if (mediaData != null) state.lastMediaData = mediaData
        if (NotificationMediaBackgroundController.isActive(controller)) {
            removeView(controller)
            return
        }
        val mode = currentMode()
        if (mode == RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED) {
            removeView(controller)
            return
        }
        mediaData ?: return
        val view = ensureView(controller) ?: return
        val isPlaying = readField(mediaData, "isPlaying") == true
        state.isPlaying = isPlaying
        configureCustomView(state)
        syncPlayback(state)

        val packageName = readField(mediaData, "packageName") as? String ?: return
        val artwork = readField(mediaData, "artwork") as? Icon
        val song = readField(mediaData, "song")?.toString().orEmpty()
        val artist = readField(mediaData, "artist")?.toString().orEmpty()
        val artworkUpdated = readField(controller, "isArtWorkUpdate") == true
        val paletteMode = if (isCustomMode(mode)) "custom" else mode.toString()
        val mediaIdentity = NotificationMediaDataIdentity.of(mediaData)
        val colorToken = "$paletteMode:$packageName:$mediaIdentity:$song:$artist"
        if (state.pendingColorToken == colorToken) return
        if (!artworkUpdated && state.colorToken == colorToken) return
        state.pendingColorToken = colorToken
        val request = state.colorRequest.incrementAndGet()
        val context = view.context

        colorExecutor.execute {
            val current = states[controller]
            if (current !== state || current.colorRequest.get() != request) return@execute
            val palette = runCatching {
                val drawable = loadArtwork(context, artwork, packageName)
                    ?: return@runCatching null
                if (isCustomMode(mode)) {
                    val bitmap = MediaArtworkSampler.sample(drawable) ?: return@runCatching null
                    try {
                        MediaFlowColorPayload.Custom(
                            MediaFlowArtwork.prepare(bitmap) ?: return@runCatching null
                        )
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    extractPalette(mode, drawable, state.nativeApi)?.let {
                        MediaFlowColorPayload.Native(it)
                    }
                }
            }.getOrElse { error ->
                HookLogger.e(TAG, "提取通知中心媒体封面颜色失败", error)
                null
            }
            view.post {
                val latest = states[controller]
                if (latest === state && latest.colorRequest.get() == request) {
                    latest.pendingColorToken = null
                    if (palette != null) {
                        applyPalette(latest, palette)
                        latest.colorToken = colorToken
                        applyUnifiedForeground(controller)
                    }
                }
            }
        }
    }

    private fun ensureView(controller: Any): View? {
        val state = states.getOrPut(controller) { ControllerState() }
        val customMode = isCustomMode(currentMode())
        state.view?.takeIf { it.parent != null && state.customView == customMode }?.let {
            return it
        }
        disposeState(state)

        val holder = readField(controller, "holder") ?: return null
        val mediaBg = readField(holder, "mediaBg") as? View ?: return null
        val parent = mediaBg.parent as? ViewGroup ?: return null
        val nativeApi = if (customMode) null else {
            resolveNativeApi(controller.javaClass.classLoader) ?: return null
        }

        for (index in 0 until parent.childCount) {
            val existing = parent.getChildAt(index)
            if (existing.tag == VIEW_TAG) {
                stopView(existing, state.nativeApi ?: nativeApi)
                parent.removeView(existing)
                break
            }
        }

        val view = if (customMode) {
            MediaFlowBackgroundView(mediaBg.context)
        } else {
            requireNotNull(nativeApi).createView(mediaBg.context)
        }
        view.apply {
            tag = VIEW_TAG
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = mediaBg.outlineProvider
            clipToOutline = true
        }
        val layoutParams =
            MediaFlowOverlayLayout.createConstraintFill(mediaBg.layoutParams) ?: run {
                HookLogger.w(
                    TAG,
                    "无法创建独立媒体背景约束，跳过流光视图"
                )
                stopView(view, nativeApi)
                return null
            }
        val index = (parent.indexOfChild(mediaBg) + 1).coerceAtMost(parent.childCount)
        parent.addView(view, index, layoutParams)
        state.colorRequest.incrementAndGet()
        state.view = view
        state.nativeApi = nativeApi
        state.customView = customMode
        state.colorToken = null
        state.pendingColorToken = null
        state.hasColors = false
        return view
    }

    private fun removeView(controller: Any, forgetState: Boolean = false) {
        val state = (if (forgetState) states.remove(controller) else states[controller]) ?: return
        state.colorRequest.incrementAndGet()
        state.pendingColorToken = null
        disposeState(state)
    }

    private fun disposeState(state: ControllerState) {
        val view = state.view ?: return
        stopView(view, state.nativeApi)
        (view.parent as? ViewGroup)?.removeView(view)
        state.view = null
        state.nativeApi = null
        state.customView = false
    }

    private fun stopView(view: View, nativeApi: NativeMusicBgApi?) {
        if (nativeApi != null && nativeApi.accepts(view)) {
            nativeApi.pause(view)
        }
    }

    private fun loadArtwork(
        context: Context,
        artwork: Icon?,
        packageName: String
    ): Drawable? {
        return runCatching {
            artwork?.loadDrawable(context)
                ?: context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }

    private fun extractPalette(
        mode: Int,
        drawable: Drawable,
        nativeApi: NativeMusicBgApi?
    ): MediaAmbientFlowPalette? {
        if (
            mode == RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC &&
            nativeApi != null
        ) {
            return nativeApi.extractSystemPalette(drawable)
        }

        val bitmap = MediaArtworkSampler.sample(drawable) ?: return null
        return try {
            val mainColor = MediaAmbientFlowPaletteExtractor.extractCoverMainColor(bitmap)
                ?: return null
            nativeApi?.createPalette(mainColor)
        } finally {
            bitmap.recycle()
        }
    }

    private fun applyPalette(state: ControllerState, palette: MediaAmbientFlowPalette) {
        val view = state.view ?: return
        val nativeApi = state.nativeApi ?: return
        if (!nativeApi.accepts(view)) return
        nativeApi.setGradientColor(view, palette.mainColor, palette.colors)
        state.hasColors = true
        syncPlayback(state)
    }

    private fun applyPalette(state: ControllerState, payload: MediaFlowColorPayload) {
        when (payload) {
            is MediaFlowColorPayload.Native -> applyPalette(state, payload.palette)
            is MediaFlowColorPayload.Custom -> {
                val view = state.view as? MediaFlowBackgroundView ?: return
                state.hasColors = true
                view.visibility = View.VISIBLE
                view.update(
                    artwork = payload.artwork,
                    tone = currentFlowTone(view.context),
                    playing = state.isPlaying
                )
            }
        }
    }

    private fun syncPlayback(state: ControllerState) {
        val view = state.view ?: return
        if (view is MediaFlowBackgroundView) {
            configureCustomView(state)
            return
        }
        val nativeApi = state.nativeApi ?: return
        if (!nativeApi.accepts(view)) return
        if (state.isPlaying && state.hasColors) {
            nativeApi.start(view)
            nativeApi.resume(view)
        } else {
            nativeApi.pause(view)
        }
    }

    private fun configureCustomView(state: ControllerState) {
        val view = state.view as? MediaFlowBackgroundView ?: return
        view.visibility = if (state.hasColors) View.VISIBLE else View.INVISIBLE
        view.update(
            tone = currentFlowTone(view.context),
            playing = state.isPlaying && state.hasColors
        )
    }

    private fun refreshCustomFlowTone(controller: Any) {
        states[controller]?.let(::configureCustomView)
    }

    private fun hasVisibleCustomFlow(controller: Any): Boolean {
        if (currentMode() != RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL) {
            return false
        }
        val state = states[controller] ?: return false
        return state.customView && state.hasColors && state.view?.visibility == View.VISIBLE
    }

    /**
     * The native controller remains the source of truth when its background is
     * native. Every custom notification background, however, must use the
     * shared foreground palette for all holder elements.
     */
    internal fun applyUnifiedForeground(controller: Any) {
        if (NotificationMediaBackgroundController.isActive(controller)) {
            val backgroundIsDark =
                NotificationMediaBackgroundController.currentBackgroundIsDark(controller)
            if (backgroundIsDark == null) {
                NotificationMediaForegroundStyler.clear(controller)
            } else {
                NotificationMediaForegroundStyler.apply(
                    controller = controller,
                    backgroundIsDark = backgroundIsDark,
                    foregroundSource =
                        NotificationMediaBackgroundController.currentForegroundSource(controller)
                )
            }
            return
        }
        val mode = currentMode()
        if (mode != RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL) {
            NotificationMediaForegroundStyler.clear(controller)
            return
        }
        val state = states[controller]
        if (state?.customView != true || !state.hasColors || state.view?.visibility != View.VISIBLE) {
            NotificationMediaForegroundStyler.clear(controller)
            return
        }
        val context = readField(controller, "context") as? Context ?: run {
            NotificationMediaForegroundStyler.clear(controller)
            return
        }
        NotificationMediaForegroundStyler.apply(
            controller = controller,
            backgroundIsDark = currentFlowTone(context) == MediaFlowTone.DARK
        )
    }

    private fun invokeZeroArgMethods(receiver: Any, name: String) {
        findNearestMethods(receiver.javaClass, name)
            .filter { method -> method.parameterCount == 0 }
            .forEach { method ->
                method.isAccessible = true
                method.invoke(receiver)
            }
    }

    private fun resolveNativeApi(classLoader: ClassLoader?): NativeMusicBgApi? {
        classLoader ?: return null
        nativeApis[classLoader]?.let { return it }
        if (nativeUnavailableClassLoaders.contains(classLoader)) return null

        return runCatching { NativeMusicBgApi.create(classLoader) }
            .onSuccess { api ->
                nativeApis[classLoader] = api
            }
            .onFailure { error ->
                nativeUnavailableClassLoaders.add(classLoader)
                HookLogger.w(TAG, "原生 MusicBgView 不可用: reason=${error.message}")
            }
            .getOrNull()
    }

    private fun readField(receiver: Any, name: String): Any? {
        return findField(receiver.javaClass, name)?.let { field ->
            runCatching { field.get(receiver) }.getOrNull()
        }
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        return null
    }

    private fun findNearestMethods(type: Class<*>, name: String): List<Method> {
        var current: Class<*>? = type
        while (current != null) {
            val methods = current.declaredMethods.filter { method ->
                method.name == name &&
                        !method.isBridge &&
                        !method.isSynthetic &&
                        !Modifier.isAbstract(method.modifiers)
            }
            if (methods.isNotEmpty()) return methods
            current = current.superclass
        }
        return emptyList()
    }

    private fun currentMode(): Int {
        if (!MediaCardRuntimeConfig.current.enabled) {
            return RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
        }
        return MediaCardRuntimeConfig.current.notification.ambientFlowMode
    }

    private fun isCustomMode(mode: Int): Boolean =
        mode == RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL

    private fun currentFlowTone(context: Context): MediaFlowTone {
        val light = when (currentCardTheme()) {
            RootConstants.MEDIA_CARD_THEME_ALWAYS_LIGHT -> true
            RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK -> false
            else -> context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        }
        return if (light) MediaFlowTone.LIGHT else MediaFlowTone.DARK
    }

    private fun currentCardTheme(): Int {
        if (!MediaCardRuntimeConfig.current.enabled) {
            return RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME
        }
        return MediaCardRuntimeConfig.current.notification.cardTheme
    }

    private data class ControllerState(
        var view: View? = null,
        var nativeApi: NativeMusicBgApi? = null,
        var customView: Boolean = false,
        var lastMediaData: Any? = null,
        var colorToken: String? = null,
        var pendingColorToken: String? = null,
        var isPlaying: Boolean = false,
        var hasColors: Boolean = false,
        val colorRequest: AtomicInteger = AtomicInteger()
    )

    private sealed interface MediaFlowColorPayload {
        data class Native(val palette: MediaAmbientFlowPalette) : MediaFlowColorPayload
        data class Custom(val artwork: MediaFlowArtwork) : MediaFlowColorPayload
    }

    private data class ControllerThemeState(val originalContext: Context)

    private fun newColorExecutor() = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HyperLyric-MediaColor").apply { isDaemon = true }
    }

    private class CardThemeApi private constructor(
        private val contextField: Field
    ) {
        fun apply(controller: Any, theme: Int) {
            val existingState = themeStates[controller]
            val originalContext = existingState?.originalContext
                ?: contextField.get(controller) as Context
            val themedContext = when (theme) {
                RootConstants.MEDIA_CARD_THEME_ALWAYS_LIGHT ->
                    originalContext.withNightMode(Configuration.UI_MODE_NIGHT_NO)

                RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK ->
                    originalContext.withNightMode(Configuration.UI_MODE_NIGHT_YES)

                else -> originalContext
            }

            if (theme == RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM) {
                if (existingState != null) {
                    contextField.set(controller, originalContext)
                    themeStates.remove(controller)
                }
            } else {
                themeStates[controller] = ControllerThemeState(originalContext)
                contextField.set(controller, themedContext)
            }

        }

        fun restore(controller: Any) {
            val state = themeStates.remove(controller) ?: return
            contextField.set(controller, state.originalContext)
        }

        private fun Context.withNightMode(nightMode: Int): Context {
            val configuration = Configuration(resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            return createConfigurationContext(configuration)
        }

        companion object {
            fun create(classLoader: ClassLoader): CardThemeApi {
                val controllerClass = controllerClassNames.firstNotNullOfOrNull { className ->
                    runCatching { classLoader.loadClass(className) }.getOrNull()
                } ?: error("Media controller class is unavailable")
                return CardThemeApi(
                    contextField = findRequiredField(controllerClass, "context")
                )
            }

            private fun findRequiredField(type: Class<*>, name: String): Field {
                var current: Class<*>? = type
                while (current != null) {
                    runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                        field.isAccessible = true
                        return field
                    }
                    current = current.superclass
                }
                error("No field $name in ${type.name}")
            }
        }
    }

    private class NativeMusicBgApi private constructor(
        private val viewClass: Class<*>,
        private val constructor: java.lang.reflect.Constructor<*>,
        private val setGradientColorMethod: Method,
        private val startMethod: Method,
        private val resumeMethod: Method,
        private val pauseMethod: Method,
        private val getMainColorMethod: Method,
        private val getPaletteColorMethod: Method,
        private val drawableToBitmapMethod: Method
    ) {
        fun createView(context: Context): View = constructor.newInstance(context) as View

        fun accepts(view: View): Boolean = viewClass.isInstance(view)

        fun setGradientColor(view: View, mainColor: Int, colors: IntArray) {
            setGradientColorMethod.invoke(view, mainColor, colors)
        }

        fun start(view: View) {
            startMethod.invoke(view)
        }

        fun resume(view: View) {
            resumeMethod.invoke(view)
        }

        fun pause(view: View) {
            pauseMethod.invoke(view)
        }

        fun extractSystemPalette(drawable: Drawable): MediaAmbientFlowPalette {
            val bitmap = drawableToBitmapMethod.invoke(null, drawable) as Bitmap
            val mainColor = getMainColorMethod.invoke(null, bitmap) as Int
            return createPalette(mainColor)
        }

        fun createPalette(mainColor: Int): MediaAmbientFlowPalette {
            val colors = intArrayOf(
                getPaletteColor(mainColor, "primary", 12),
                getPaletteColor(mainColor, "primary", 10),
                getPaletteColor(mainColor, "tertiary", 12)
            )
            return MediaAmbientFlowPalette(mainColor, colors)
        }

        private fun getPaletteColor(mainColor: Int, role: String, tone: Int): Int {
            return getPaletteColorMethod.invoke(null, mainColor, role, tone) as Int
        }

        companion object {
            fun create(classLoader: ClassLoader): NativeMusicBgApi {
                val viewClass = classLoader.loadClass("com.mi.widget.view.MusicBgView")
                val constructor = viewClass.getDeclaredConstructor(Context::class.java).apply {
                    isAccessible = true
                }
                val setGradientColor = viewClass.getDeclaredMethod(
                    "setGradientColor",
                    Int::class.javaPrimitiveType,
                    IntArray::class.java
                ).apply { isAccessible = true }
                val start = viewClass.getDeclaredMethod("start").apply { isAccessible = true }
                val resume = viewClass.getDeclaredMethod("resume").apply { isAccessible = true }
                val pause = viewClass.getDeclaredMethod("pause").apply { isAccessible = true }

                val drawableUtils = classLoader.loadClass("com.miui.utils.DrawableUtils")
                val drawableToBitmap = drawableUtils.declaredMethods.single { method ->
                    method.name == "drawable2Bitmap" &&
                            method.parameterTypes.contentEquals(arrayOf(Drawable::class.java)) &&
                            method.returnType == Bitmap::class.java
                }.apply { isAccessible = true }

                val miPalette = classLoader.loadClass("miuix.mipalette.MiPalette")
                miPalette.declaredMethods.firstOrNull { method ->
                    method.name == "init" && method.parameterCount == 0
                }?.apply { isAccessible = true }?.invoke(null)
                val getMainColor = miPalette.getDeclaredMethod(
                    "getMainColorHCT",
                    Bitmap::class.java
                ).apply { isAccessible = true }
                val getPaletteColor = miPalette.getDeclaredMethod(
                    "getPaletteColor",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType
                ).apply { isAccessible = true }

                return NativeMusicBgApi(
                    viewClass = viewClass,
                    constructor = constructor,
                    setGradientColorMethod = setGradientColor,
                    startMethod = start,
                    resumeMethod = resume,
                    pauseMethod = pause,
                    getMainColorMethod = getMainColor,
                    getPaletteColorMethod = getPaletteColor,
                    drawableToBitmapMethod = drawableToBitmap
                )
            }
        }
    }

    private const val CONTROLLER_PACKAGE =
        "com.android.systemui.statusbar.notification.mediacontrol."
    private val TARGET_METHOD_NAMES = listOf(
        "attach",
        "detach",
        "bindMediaData",
        "updateForegroundColors",
        "updateMediaBackground"
    )
    private val NATIVE_BACKGROUND_UPDATE_METHODS = setOf(
        "updateForegroundColors",
        "updateMediaBackground"
    )
    private const val HYPER_PROGRESS_SEEK_BAR_CLASS =
        "miuix.miuixbasewidget.widget.HyperProgressSeekBar"
}
