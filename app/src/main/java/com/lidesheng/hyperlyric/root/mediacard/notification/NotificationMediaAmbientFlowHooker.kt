package com.lidesheng.hyperlyric.root.mediacard.notification

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.mediacard.MediaArtworkSampler
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaBackgroundController
import com.lidesheng.hyperlyric.root.utils.HookLogger
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
    private const val TAG = "NotificationMediaAmbientFlow"
    private const val VIEW_TAG = "hyperlyric.notification_media_ambient_flow"
    private val controllerClassNames = listOf(
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl",
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewController"
    )

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val states = Collections.synchronizedMap(WeakHashMap<Any, ControllerState>())
    private val activeControllers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val themeStates = Collections.synchronizedMap(WeakHashMap<Any, ControllerThemeState>())
    private val nativeApis = Collections.synchronizedMap(WeakHashMap<ClassLoader, NativeMusicBgApi>())
    private val themeApis = Collections.synchronizedMap(WeakHashMap<ClassLoader, CardThemeApi>())
    private val nativeUnavailableClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    @Volatile
    private var colorExecutor = newColorExecutor()

    @Volatile
    private var module: XposedModule? = null

    private val prefs
        get() = (module as? HookEntry)?.prefs

    fun initialize(xposedModule: XposedModule) {
        module = xposedModule
        NotificationMediaBackgroundController.initialize(xposedModule)
        if (colorExecutor.isShutdown) colorExecutor = newColorExecutor()
    }

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        initialize(xposedModule)
        if (!hookedClassLoaders.add(classLoader)) return

        val controllerClass = controllerClassNames.firstNotNullOfOrNull { className ->
            runCatching { classLoader.loadClass(className) }.getOrNull()
        }
        if (controllerClass == null) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Notification center media controller is unavailable")
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
                    xposedModule.deoptimize(method)
                    xposedModule.hook(method)
                        .intercept(hookerFor(method) ?: return@runCatching)
                    installed++
                    if (method.name in NATIVE_BACKGROUND_UPDATE_METHODS) {
                        installedNativeUpdates.add(method.name)
                    }
                }.onFailure { error ->
                    HookLogger.e(TAG, "Failed to hook ${method.name}", error)
                }
            }
        NotificationMediaBackgroundController.setNativeHooksAvailable(
            classLoader,
            installedNativeUpdates.containsAll(NATIVE_BACKGROUND_UPDATE_METHODS)
        )
        if (!installedNativeUpdates.containsAll(NATIVE_BACKGROUND_UPDATE_METHODS)) {
            HookLogger.w(TAG, "Native media background methods are incomplete; custom background hook skipped")
        }

        runCatching {
            val seekBarClass = classLoader.loadClass(HYPER_PROGRESS_SEEK_BAR_CLASS)
            findNearestMethods(seekBarClass, "onDraw").forEach { method ->
                method.isAccessible = true
                xposedModule.deoptimize(method)
                xposedModule.hook(method).intercept(ProgressDrawHook())
                installed++
            }
        }.onFailure { error ->
            HookLogger.w(TAG, "Native HyperProgressSeekBar API unavailable: ${error.message}")
        }

        if (installed == 0) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "No compatible media controller methods were found")
        } else {
            HookLogger.i(TAG, "Notification media ambient flow hook initialized: methods=$installed")
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
            "updateForegroundColors", "updateMediaBackground" -> NativeBackgroundUpdateHook()
            "onDraw" -> ProgressDrawHook()
            else -> null
        }
    }

    fun releaseAll() {
        val snapshot = synchronized(states) { states.toMap() }
        val controllers = synchronized(activeControllers) { activeControllers.toList() }
        states.clear()
        activeControllers.clear()
        colorExecutor.shutdownNow()
        NotificationMediaBackgroundController.releaseAll()
        val cleanup = Runnable {
            controllers.forEach(::restoreCardTheme)
            snapshot.values.forEach(::disposeState)
            themeStates.clear()
            nativeApis.clear()
            themeApis.clear()
            nativeUnavailableClassLoaders.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup.run()
        } else {
            Handler(Looper.getMainLooper()).post(cleanup)
        }
    }

    class ControllerHook(private val action: Action) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject ?: return chain.proceed()
            if (action == Action.DETACH) {
                activeControllers.remove(controller)
                removeView(controller)
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
                    HookLogger.e(TAG, "Failed to prepare native media card theme", it)
                }
            }
            val result = chain.proceed()
            runCatching {
                when (action) {
                    Action.ATTACH -> {
                        activeControllers.add(controller)
                        syncView(controller)
                    }
                    Action.BIND -> {
                        activeControllers.add(controller)
                        NotificationMediaBackgroundController.onBind(
                            controller,
                            chain.args.firstOrNull()
                        )
                        bind(controller, chain.args.firstOrNull())
                    }
                    Action.DETACH -> Unit
                }
            }.onFailure { error ->
                HookLogger.e(TAG, "Media ambient flow ${action.name.lowercase()} failed", error)
            }
            return result
        }
    }

    enum class Action { ATTACH, DETACH, BIND }

    class NativeBackgroundUpdateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject ?: return chain.proceed()
            return if (NotificationMediaBackgroundController.isActive(controller)) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    class ProgressDrawHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            chain.thisObject?.let(NotificationMediaBackgroundController::applySeekBarColor)
            return chain.proceed()
        }
    }

    fun refreshCardTheme() {
        val refresh = Runnable {
            val snapshot = synchronized(activeControllers) { activeControllers.toList() }
            snapshot.forEach { controller ->
                runCatching { refreshCardTheme(controller) }
                    .onFailure { HookLogger.e(TAG, "Failed to refresh media card theme", it) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else Handler(Looper.getMainLooper()).post(refresh)
    }

    fun refreshBackgroundStyle() {
        val controllers = synchronized(activeControllers) { activeControllers.toList() }
        controllers.forEach { controller ->
            if (NotificationMediaBackgroundController.isActive(controller)) {
                restoreCardTheme(controller)
            }
        }
        NotificationMediaBackgroundController.refresh(controllers) { controller ->
            runCatching { refreshCardTheme(controller) }
                .onFailure { HookLogger.e(TAG, "Failed to restore native media background", it) }
        }
        controllers.forEach(::syncView)
    }

    private fun prepareCardTheme(controller: Any) {
        val api = resolveThemeApi(controller.javaClass.classLoader) ?: return
        api.apply(controller, currentCardTheme(), refreshViews = false)
    }

    private fun refreshCardTheme(controller: Any) {
        if (NotificationMediaBackgroundController.isActive(controller)) return
        val api = resolveThemeApi(controller.javaClass.classLoader) ?: return
        api.apply(controller, currentCardTheme(), refreshViews = true)
    }

    private fun restoreCardTheme(controller: Any) {
        val api = resolveThemeApi(controller.javaClass.classLoader) ?: return
        api.restore(controller)
    }

    private fun resolveThemeApi(classLoader: ClassLoader?): CardThemeApi? {
        classLoader ?: return null
        themeApis[classLoader]?.let { return it }
        return runCatching { CardThemeApi.create(classLoader) }
            .onSuccess { themeApis[classLoader] = it }
            .onFailure { HookLogger.w(TAG, "Native media card theme API unavailable: ${it.message}") }
            .getOrNull()
    }

    private fun syncView(controller: Any) {
        if (NotificationMediaBackgroundController.isActive(controller)) {
            removeView(controller)
            return
        }
        if (currentMode() != RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED) {
            ensureView(controller)
        } else {
            removeView(controller)
        }
    }

    private fun bind(controller: Any, mediaData: Any?) {
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
        val state = states.getOrPut(controller) { ControllerState() }
        val isPlaying = readField(mediaData, "isPlaying") == true
        state.isPlaying = isPlaying
        syncPlayback(state)

        val packageName = readField(mediaData, "packageName") as? String ?: return
        val artwork = readField(mediaData, "artwork") as? Icon
        val song = readField(mediaData, "song")?.toString().orEmpty()
        val artist = readField(mediaData, "artist")?.toString().orEmpty()
        val artworkUpdated = readField(controller, "isArtWorkUpdate") == true
        val colorToken = "$mode:$packageName:$song:$artist"
        if (state.pendingColorToken == colorToken) return
        if (!artworkUpdated && state.colorToken == colorToken) return
        state.pendingColorToken = colorToken
        val request = state.colorRequest.incrementAndGet()
        val context = view.context

        colorExecutor.execute {
            val current = states[controller]
            if (current !== state || current.colorRequest.get() != request) return@execute
            val palette = runCatching {
                val bitmap = loadArtwork(context, artwork, packageName)
                    ?: return@runCatching null
                try {
                    extractPalette(mode, bitmap, state.nativeApi)
                } finally {
                    bitmap.recycle()
                }
            }.getOrElse { error ->
                HookLogger.e(TAG, "Failed to extract media artwork colors", error)
                null
            }
            view.post {
                val latest = states[controller]
                if (latest === state && latest.colorRequest.get() == request) {
                    latest.pendingColorToken = null
                    if (palette != null) {
                        applyPalette(latest, palette)
                        latest.colorToken = colorToken
                    }
                }
            }
        }
    }

    private fun ensureView(controller: Any): View? {
        val state = states.getOrPut(controller) { ControllerState() }
        state.view?.takeIf { it.parent != null }?.let { return it }

        val holder = readField(controller, "holder") ?: return null
        val mediaBg = readField(holder, "mediaBg") as? View ?: return null
        val parent = mediaBg.parent as? ViewGroup ?: return null
        val nativeApi = resolveNativeApi(controller.javaClass.classLoader) ?: return null

        for (index in 0 until parent.childCount) {
            val existing = parent.getChildAt(index)
            if (existing.tag == VIEW_TAG) {
                stopView(existing, nativeApi)
                parent.removeView(existing)
                break
            }
        }

        val view = nativeApi.createView(mediaBg.context)
        view.apply {
            tag = VIEW_TAG
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            outlineProvider = mediaBg.outlineProvider
            clipToOutline = true
        }
        val index = (parent.indexOfChild(mediaBg) + 1).coerceAtMost(parent.childCount)
        parent.addView(view, index, mediaBg.layoutParams)
        state.colorRequest.incrementAndGet()
        state.view = view
        state.nativeApi = nativeApi
        state.colorToken = null
        state.pendingColorToken = null
        state.hasColors = false
        return view
    }

    private fun removeView(controller: Any) {
        val state = states.remove(controller) ?: return
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
    ): Bitmap? {
        val drawable = runCatching {
            artwork?.loadDrawable(context)
                ?: context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        return MediaArtworkSampler.sample(drawable)
    }

    private fun extractPalette(
        mode: Int,
        bitmap: Bitmap,
        nativeApi: NativeMusicBgApi?
    ): MediaPalette? {
        if (
            mode == RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC &&
            nativeApi != null
        ) {
            return nativeApi.extractSystemPalette(bitmap)
        }

        val maxColors = if (
            mode == RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
        ) 3 else 1
        val extracted = ColorExtractor.extractThemePalette(bitmap, maxColors).rawColors
        val mainColor = extracted.firstOrNull() ?: return null
        val colors = IntArray(3) { index -> extracted.getOrElse(index) { mainColor } }
        return MediaPalette(mainColor, colors)
    }

    private fun applyPalette(state: ControllerState, palette: MediaPalette) {
        val view = state.view ?: return
        val nativeApi = state.nativeApi ?: return
        if (!nativeApi.accepts(view)) return
        nativeApi.setGradientColor(view, palette.mainColor, palette.colors)
        state.hasColors = true
        syncPlayback(state)
    }

    private fun syncPlayback(state: ControllerState) {
        val view = state.view ?: return
        val nativeApi = state.nativeApi ?: return
        if (!nativeApi.accepts(view)) return
        if (state.isPlaying && state.hasColors) {
            nativeApi.start(view)
            nativeApi.resume(view)
        } else {
            nativeApi.pause(view)
        }
    }

    private fun resolveNativeApi(classLoader: ClassLoader?): NativeMusicBgApi? {
        classLoader ?: return null
        nativeApis[classLoader]?.let { return it }
        if (nativeUnavailableClassLoaders.contains(classLoader)) return null

        return runCatching { NativeMusicBgApi.create(classLoader) }
            .onSuccess { api ->
                nativeApis[classLoader] = api
                HookLogger.i(TAG, "Using native MusicBgView renderer")
            }
            .onFailure { error ->
                nativeUnavailableClassLoaders.add(classLoader)
                HookLogger.w(TAG, "Native MusicBgView is unavailable: ${error.message}")
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
        return prefs?.getInt(
            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE,
            RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE
        )?.coerceIn(
            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED,
            RootConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
        ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE
    }

    private fun currentCardTheme(): Int {
        return prefs?.getInt(
            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
            RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME
        )?.coerceIn(
            RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
            RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK
        ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME
    }

    private data class ControllerState(
        var view: View? = null,
        var nativeApi: NativeMusicBgApi? = null,
        var colorToken: String? = null,
        var pendingColorToken: String? = null,
        var isPlaying: Boolean = false,
        var hasColors: Boolean = false,
        val colorRequest: AtomicInteger = AtomicInteger()
    )

    private data class ControllerThemeState(val originalContext: Context)

    private data class MediaPalette(
        val mainColor: Int,
        val colors: IntArray
    )

    private fun newColorExecutor() = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HyperLyric-MediaColor").apply { isDaemon = true }
    }

    private class CardThemeApi private constructor(
        private val contextField: Field,
        private val updateForegroundColorsMethod: Method,
        private val updateMediaBackgroundMethod: Method
    ) {
        fun apply(controller: Any, theme: Int, refreshViews: Boolean) {
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

            if (refreshViews) {
                updateForegroundColorsMethod.invoke(controller)
                updateMediaBackgroundMethod.invoke(controller)
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
                    contextField = findRequiredField(controllerClass, "context"),
                    updateForegroundColorsMethod = controllerClass.declaredMethods.single {
                        it.name == "updateForegroundColors" && it.parameterCount == 0
                    }.apply { isAccessible = true },
                    updateMediaBackgroundMethod = controllerClass.declaredMethods.single {
                        it.name == "updateMediaBackground" && it.parameterCount == 0
                    }.apply { isAccessible = true }
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
        private val getPaletteColorMethod: Method
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

        fun extractSystemPalette(bitmap: Bitmap): MediaPalette {
            val mainColor = getMainColorMethod.invoke(null, bitmap) as Int
            val colors = intArrayOf(
                getPaletteColor(mainColor, "primary", 12),
                getPaletteColor(mainColor, "primary", 10),
                getPaletteColor(mainColor, "tertiary", 12)
            )
            return MediaPalette(mainColor, colors)
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
                    getPaletteColorMethod = getPaletteColor
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
