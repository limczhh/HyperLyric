package com.lidesheng.hyperlyric.root.mediacard.island

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object IslandExpandedMediaAmbientFlowHooker {
    private const val TAG = "IslandExpandedMediaFlow"
    private const val BINDER_CLASS =
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewBinderImpl"
    private const val MUSIC_BG_VIEW_CLASS = "com.mi.widget.view.MusicBgView"
    private const val SEEK_BAR_HEAD_ALPHA_LISTENER_CLASS =
        "miuix.miuixbasewidget.widget.HyperProgressSeekBar\$1"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val EXPANDED_VIEW_CLASS =
        "miui.systemui.dynamicisland.view.DynamicIslandExpandedView"
    private const val MI_BLUR_COMPAT_CLASS = "miui.systemui.util.MiBlurCompat"
    private const val ORIGINAL_ALPHA_TAG_KEY = 0x7e48594c

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val binderStates = Collections.synchronizedMap(WeakHashMap<Any, BinderState>())
    private val activeBinders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val themeStates = Collections.synchronizedMap(WeakHashMap<View, ViewThemeState>())
    private val seekBarThemeStates = Collections.synchronizedMap(
        WeakHashMap<View, SeekBarThemeState>()
    )
    private val restoringNativeForeground = ThreadLocal<Boolean>()
    private val colorExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HyperLyric-IslandMediaColor").apply { isDaemon = true }
    }

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var nativeApi: NativeApi? = null

    private val prefs: SharedPreferences?
        get() = (module as? HookEntry)?.prefs

    fun initialize(xposedModule: XposedModule) {
        module = xposedModule
    }

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        initialize(xposedModule)
        if (!hookedClassLoaders.add(classLoader)) return

        val api = resolveApi(classLoader) ?: run {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Native expanded media flow API is unavailable; hook skipped")
            return
        }

        val installedHandles = mutableListOf<HookHandle>()
        api.hookMethods.forEach { method ->
            runCatching {
                xposedModule.deoptimize(method)
                val hooker = hookerFor(method)
                    ?: error("No hooker for ${method.declaringClass.name}.${method.name}")
                installedHandles += xposedModule.hook(method).intercept(hooker)
            }.onFailure { error ->
                HookLogger.e(TAG, "Failed to hook ${method.declaringClass.simpleName}.${method.name}", error)
            }
        }

        if (installedHandles.size != api.hookMethods.size) {
            installedHandles.forEach(HookHandle::unhook)
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Expanded media flow hook was not installed completely; all handles removed")
        } else {
            HookLogger.i(TAG, "Expanded media flow hook initialized: methods=${installedHandles.size}")
        }
    }

    fun isTargetMethod(method: Method): Boolean {
        return when (method.declaringClass.name) {
            BINDER_CLASS -> when (method.name) {
                "attach" -> method.parameterCount == 2
                "bindMediaData" -> method.parameterCount == 1
                "detach" -> method.parameterCount == 0
                "setAlbumImage" -> method.parameterCount == 1
                "setSeamless" -> method.parameterCount == 2
                "updateForegroundColors" -> method.parameterCount == 1
                else -> false
            }

            MUSIC_BG_VIEW_CLASS ->
                (method.name == "start" || method.name == "resume") &&
                    method.parameterCount == 0

            SEEK_BAR_HEAD_ALPHA_LISTENER_CLASS ->
                method.name == "onUpdate" && method.parameterCount == 2

            else -> false
        }
    }

    fun hookerFor(executable: Executable): Hooker? {
        val method = executable as? Method ?: return null
        if (!isTargetMethod(method)) return null
        resolveApi(method.declaringClass.classLoader) ?: return null
        return when (method.declaringClass.name) {
            BINDER_CLASS -> when (method.name) {
                "attach" -> BinderHook(Action.ATTACH)
                "bindMediaData" -> BinderHook(Action.BIND)
                "detach" -> BinderHook(Action.DETACH)
                "setAlbumImage" -> BinderHook(Action.ALBUM)
                "setSeamless" -> BinderHook(Action.SEAMLESS)
                "updateForegroundColors" -> ForegroundColorsHook()
                else -> null
            }

            MUSIC_BG_VIEW_CLASS -> PlaybackStartHook()
            SEEK_BAR_HEAD_ALPHA_LISTENER_CLASS -> HeadGlowUpdateHook()
            else -> null
        }
    }

    fun releaseAll() {
        val binders = synchronized(activeBinders) { activeBinders.toList() }
        val cleanup = Runnable {
            binders.forEach { binder ->
                restoreCardTheme(binder)
                restoreMediaElements(binder)
            }
            IslandExpandedMediaElementController.cleanup()
            activeBinders.clear()
            themeStates.clear()
            seekBarThemeStates.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run()
        else Handler(Looper.getMainLooper()).post(cleanup)
        binderStates.clear()
        colorExecutor.shutdownNow()
    }

    private enum class Action { ATTACH, BIND, DETACH, ALBUM, SEAMLESS }

    private class BinderHook(private val action: Action) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val binder = chain.thisObject ?: return chain.proceed()
            if (action == Action.DETACH) cleanupBinder(binder)
            val result = chain.proceed()
            runCatching {
                when (action) {
                    Action.ATTACH -> {
                        activeBinders.add(binder)
                        applyMode(binder, allowCoverColor = false)
                        applyCardTheme(binder)
                        applyMediaElements(binder)
                    }
                    Action.BIND -> {
                        activeBinders.add(binder)
                        applyMode(binder, allowCoverColor = true)
                        applyCardTheme(binder)
                        applyMediaElements(binder)
                    }
                    Action.ALBUM,
                    Action.SEAMLESS -> applyMediaElements(binder)
                    Action.DETACH -> Unit
                }
            }.onFailure { error ->
                HookLogger.e(TAG, "Failed to apply expanded media flow mode", error)
            }
            return result
        }
    }

    private class PlaybackStartHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.thisObject as? View ?: return chain.proceed()
            if (currentMode() == RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED &&
                isExpandedIslandView(view)
            ) {
                return null
            }
            return chain.proceed()
        }
    }

    private class ForegroundColorsHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (restoringNativeForeground.get() == true) return chain.proceed()
            val binder = chain.thisObject ?: return chain.proceed()
            val holder = chain.args.firstOrNull() ?: return chain.proceed()
            if (!shouldUseLightTheme(binder)) return chain.proceed()

            val api = nativeApi ?: return chain.proceed()
            return try {
                val lightContext = api.getContext(binder)
                    .withNightMode(Configuration.UI_MODE_NIGHT_NO)
                applyLightForeground(api, holder, CardColors.from(lightContext))
                null
            } catch (error: Throwable) {
                HookLogger.e(TAG, "Failed to apply native light foreground", error)
                chain.proceed()
            }
        }
    }

    private class HeadGlowUpdateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val api = nativeApi ?: return result
            val listener = chain.thisObject ?: return result
            val seekBar = api.getHeadAlphaListenerSeekBar(listener)
            if (seekBarThemeStates.containsKey(seekBar)) {
                api.setSeekBarHeadGlowAlpha(seekBar, 0f)
            }
            return result
        }
    }

    fun refreshCardTheme() {
        val refresh = Runnable {
            val snapshot = synchronized(activeBinders) { activeBinders.toList() }
            snapshot.forEach { binder ->
                runCatching { applyCardTheme(binder) }
                    .onFailure { HookLogger.e(TAG, "Failed to refresh expanded media theme", it) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else Handler(Looper.getMainLooper()).post(refresh)
    }

    fun refreshMediaElements() {
        val refresh = Runnable {
            val snapshot = synchronized(activeBinders) { activeBinders.toList() }
            snapshot.forEach { binder ->
                runCatching { applyMediaElements(binder) }
                    .onFailure { HookLogger.e(TAG, "Failed to refresh expanded media elements", it) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else Handler(Looper.getMainLooper()).post(refresh)
    }

    private fun applyCardTheme(binder: Any) {
        val api = nativeApi ?: return
        if (!shouldUseLightTheme(binder)) {
            restoreCardTheme(binder)
            return
        }

        val lightContext = api.getContext(binder).withNightMode(Configuration.UI_MODE_NIGHT_NO)
        val colors = CardColors.from(lightContext)
        api.getHolders(binder).forEach { holder ->
            val player = api.getPlayer(holder)
            if (!applyLightExpandedBackground(api, player)) {
                player.post {
                    if (activeBinders.contains(binder) && shouldUseLightTheme(binder)) {
                        runCatching {
                            applyLightExpandedBackground(api, player)
                        }.onFailure {
                            HookLogger.e(TAG, "Failed to apply deferred live update background", it)
                        }
                    }
                }
            }
            applyLightForeground(api, holder, colors)
        }
    }

    private fun applyLightForeground(api: NativeApi, holder: Any, colors: CardColors) {
        val seekBar = api.getSeekBar(holder)
        seekBarThemeStates.getOrPut(seekBar) {
            SeekBarThemeState(
                originalColorFilter = api.getSeekBarShaderColorFilter(seekBar),
                originalHeadGlowAlpha = api.getSeekBarHeadGlowAlpha(seekBar)
            )
        }
        api.applyLightForeground(holder, colors)
    }

    private fun applyLightExpandedBackground(
        api: NativeApi,
        player: View
    ): Boolean {
        val target = api.findExpandedBackgroundTarget(player) ?: return false
        val state = themeStates.getOrPut(player) {
            val miniBar = api.getMiniBar(target)
            ViewThemeState(
                target = target,
                miniBar = miniBar,
                originalMiniBarTint = miniBar?.backgroundTintList
            )
        }
        val lightContext = target.expandedView.context.withNightMode(Configuration.UI_MODE_NIGHT_NO)
        api.applyLiveUpdateBackground(target, lightContext)
        state.miniBar?.backgroundTintList = ColorStateList.valueOf(
            Color.argb(0x99, 0, 0, 0)
        )
        return true
    }

    private fun shouldUseLightTheme(binder: Any): Boolean {
        val api = nativeApi ?: return false
        return when (currentCardTheme()) {
            RootConstants.MEDIA_CARD_THEME_ALWAYS_LIGHT -> true
            RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK -> false
            else -> !api.getContext(binder).resources.configuration.isNightMode
        }
    }

    private fun restoreCardTheme(binder: Any) {
        val api = nativeApi ?: return
        api.getHolders(binder).forEach { holder ->
            val player = api.getPlayer(holder)
            val seekBar = api.getSeekBar(holder)
            themeStates.remove(player)?.let { state ->
                state.miniBar?.backgroundTintList = state.originalMiniBarTint
                api.restoreNativeExpandedBackground(state.target)
            }
            seekBarThemeStates.remove(seekBar)?.let { state ->
                api.setSeekBarShaderColorFilter(seekBar, state.originalColorFilter)
                api.setSeekBarHeadGlowAlpha(seekBar, state.originalHeadGlowAlpha)
            }
            restoringNativeForeground.set(true)
            try {
                api.applyNativeForeground(binder, holder)
            } finally {
                restoringNativeForeground.remove()
            }
        }
    }

    private fun applyMediaElements(binder: Any) {
        val api = nativeApi ?: return
        val coverStyle = currentCoverStyle()
        val hideCoverSource = hideCoverSource()
        val hideDeviceSwitch = hideDeviceSwitch()
        val playbackActive = api.isPlaying(binder)
        api.getHolders(binder).forEach { holder ->
            IslandExpandedMediaElementController.apply(
                elements = api.getMediaElements(holder),
                coverStyle = coverStyle,
                hideCoverSource = hideCoverSource,
                hideDeviceSwitch = hideDeviceSwitch,
                playbackActive = playbackActive
            )
        }
    }

    private fun restoreMediaElements(binder: Any) {
        val api = nativeApi ?: return
        api.getHolders(binder).forEach { holder ->
            IslandExpandedMediaElementController.restore(api.getMediaElements(holder))
        }
    }

    private fun applyMode(binder: Any, allowCoverColor: Boolean) {
        val api = nativeApi ?: return
        val views = api.getMusicBgViews(binder)
        if (views.isEmpty()) return

        when (currentMode()) {
            RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED -> {
                binderStates.remove(binder)?.request?.incrementAndGet()
                views.forEach { view ->
                    if (view.getTag(ORIGINAL_ALPHA_TAG_KEY) == null) {
                        view.setTag(ORIGINAL_ALPHA_TAG_KEY, view.alpha)
                    }
                    view.alpha = 0f
                    api.pause(view)
                }
            }

            RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR -> {
                views.forEach(::restoreViewAlpha)
                if (allowCoverColor) scheduleCoverColors(binder, views.first(), api)
            }

            else -> {
                binderStates.remove(binder)?.request?.incrementAndGet()
                views.forEach(::restoreViewAlpha)
            }
        }
    }

    private fun scheduleCoverColors(binder: Any, primaryView: View, api: NativeApi) {
        val drawable = api.getArtwork(binder) ?: return
        val token = "${System.identityHashCode(drawable)}:${drawable.constantState?.hashCode() ?: 0}"
        val state = binderStates.getOrPut(binder) { BinderState() }
        if (state.colorToken == token) {
            state.palette?.let { palette ->
                api.setGradientColor(primaryView, palette.mainColor, palette.colors)
            }
            return
        }
        state.colorToken = token
        state.palette = null
        val request = state.request.incrementAndGet()

        val source = api.drawableToBitmap(drawable) ?: return
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, false)
        runCatching {
            colorExecutor.execute {
                val palette = runCatching { extractPalette(bitmap) }
                    .onFailure { HookLogger.e(TAG, "Failed to extract expanded media colors", it) }
                    .getOrNull()
                bitmap.recycle()
                palette ?: return@execute
                primaryView.post {
                    val current = binderStates[binder]
                    if (current !== state || current.request.get() != request) return@post
                    if (currentMode() !=
                        RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
                    ) return@post
                    val currentPrimary = api.getMusicBgViews(binder).firstOrNull() ?: return@post
                    current.palette = palette
                    api.setGradientColor(currentPrimary, palette.mainColor, palette.colors)
                }
            }
        }.onFailure { error ->
            bitmap.recycle()
            HookLogger.e(TAG, "Failed to schedule expanded media color extraction", error)
        }
    }

    private fun extractPalette(bitmap: Bitmap): MediaPalette? {
        val extracted = ColorExtractor.extractThemePalette(bitmap, 3).rawColors
        val mainColor = extracted.firstOrNull() ?: return null
        return MediaPalette(
            mainColor = mainColor,
            colors = IntArray(3) { index -> extracted.getOrElse(index) { mainColor } }
        )
    }

    private fun cleanupBinder(binder: Any) {
        activeBinders.remove(binder)
        restoreCardTheme(binder)
        restoreMediaElements(binder)
        binderStates.remove(binder)?.request?.incrementAndGet()
        nativeApi?.getMusicBgViews(binder)?.forEach(::restoreViewAlpha)
    }

    private fun restoreViewAlpha(view: View) {
        val original = view.getTag(ORIGINAL_ALPHA_TAG_KEY) as? Float ?: return
        view.setTag(ORIGINAL_ALPHA_TAG_KEY, null)
        if (view.alpha == 0f) view.alpha = original
    }

    private fun isExpandedIslandView(view: View): Boolean {
        var current: View? = view
        repeat(8) {
            val parent = current?.parent ?: return false
            if (parent.javaClass.name.contains(".notification.mediaisland.")) return true
            current = parent as? View ?: return false
        }
        return false
    }

    private fun currentMode(): Int {
        return prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE,
            RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE
        )?.coerceIn(
            RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT,
            RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE
    }

    private fun currentCardTheme(): Int {
        return prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
            RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME
        )?.coerceIn(
            RootConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
            RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME
    }

    private fun currentCoverStyle(): Int {
        return prefs?.getInt(
            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE
        )?.coerceIn(
            RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT,
            RootConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE
    }

    private fun hideCoverSource(): Boolean {
        return prefs?.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
            RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE
    }

    private fun hideDeviceSwitch(): Boolean {
        return prefs?.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
            RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH
        ) ?: RootConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH
    }

    private val Configuration.isNightMode: Boolean
        get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun Context.withNightMode(nightMode: Int): Context {
        val configuration = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        return createConfigurationContext(configuration)
    }

    private fun resolveApi(classLoader: ClassLoader?): NativeApi? {
        nativeApi?.let { return it }
        classLoader ?: return null
        return runCatching { NativeApi.create(classLoader) }
            .onSuccess { nativeApi = it }
            .onFailure { HookLogger.w(TAG, "Native expanded media flow API unavailable: ${it.message}") }
            .getOrNull()
    }

    private data class BinderState(
        var colorToken: String? = null,
        var palette: MediaPalette? = null,
        val request: AtomicInteger = AtomicInteger()
    )

    private data class MediaPalette(
        val mainColor: Int,
        val colors: IntArray
    )

    private data class ViewThemeState(
        val target: ExpandedBackgroundTarget,
        val miniBar: View?,
        val originalMiniBarTint: ColorStateList?
    )

    private data class SeekBarThemeState(
        val originalColorFilter: ColorFilter?,
        val originalHeadGlowAlpha: Float
    )

    private data class ExpandedBackgroundTarget(
        val owner: Any,
        val expandedView: View
    )

    private data class CardColors(
        val primaryText: Int,
        val secondaryText: Int,
        val durationText: Int,
        val action: Int,
        val seekBarForeground: Int,
        val seekBarBackground: Int
    ) {
        companion object {
            fun from(context: Context): CardColors {
                fun color(name: String): Int {
                    val id = context.resources.getIdentifier(name, "color", context.packageName)
                    require(id != 0) { "Missing color resource: $name" }
                    return context.getColor(id)
                }
                return CardColors(
                    primaryText = color("media_primary_text"),
                    secondaryText = color("media_secondary_text"),
                    durationText = color("media_duration_time_font_color"),
                    action = color("notification_media_action_button_light_color"),
                    seekBarForeground = android.graphics.Color.BLACK,
                    seekBarBackground = color("media_seekbar_background_color")
                )
            }
        }
    }

    private class NativeApi private constructor(
        val hookMethods: List<Method>,
        private val holderField: Field,
        private val dummyHolderField: Field,
        private val artworkField: Field,
        private val mediaBgViewField: Field,
        private val playerField: Field,
        private val contextField: Field,
        private val titleTextField: Field,
        private val artistTextField: Field,
        private val elapsedTimeViewField: Field,
        private val totalTimeViewField: Field,
        private val seamlessIconField: Field,
        private val albumViewField: Field,
        private val albumImageField: Field,
        private val appIconField: Field,
        private val seamlessField: Field,
        private val mediaDataField: Field,
        private val mediaDataIsPlayingField: Field,
        private val seekBarField: Field,
        private val seekBarPaintField: Field,
        private val seekBarRuntimeShaderField: Field,
        private val seekBarHeadGlowAlphaField: Field,
        private val headAlphaListenerSeekBarField: Field,
        private val getActionListMethod: Method,
        private val updateForegroundColorsMethod: Method,
        private val setSeekBarForegroundMethod: Method,
        private val setSeekBarBackgroundMethod: Method,
        private val pauseMethod: Method,
        private val setGradientColorMethod: Method,
        private val drawableToBitmapMethod: Method
    ) {
        private val expandedBackgroundMethods = Collections.synchronizedMap(
            WeakHashMap<ClassLoader, ExpandedBackgroundMethods>()
        )

        fun getHolders(binder: Any): List<Any> {
            return listOfNotNull(holderField.get(binder), dummyHolderField.get(binder)).distinct()
        }

        fun getMusicBgViews(binder: Any): List<View> {
            return getHolders(binder).mapNotNull { holder -> getMusicBgView(holder) }
                .distinct()
        }

        fun getMusicBgView(holder: Any): View = mediaBgViewField.get(holder) as View

        fun getPlayer(holder: Any): View = playerField.get(holder) as View

        fun getMediaElements(holder: Any): IslandExpandedMediaElements {
            val player = getPlayer(holder)
            @Suppress("UNCHECKED_CAST")
            val actions = getActionListMethod.invoke(holder) as List<View>
            val actionsId = player.resources.getIdentifier(
                "actions",
                "id",
                player.context.packageName
            )
            require(actionsId != 0) { "Missing SystemUI id resource: actions" }
            return IslandExpandedMediaElements(
                albumView = albumViewField.get(holder) as View,
                albumImage = albumImageField.get(holder) as ImageView,
                coverSource = appIconField.get(holder) as ImageView,
                deviceSwitch = seamlessField.get(holder) as View,
                title = titleTextField.get(holder) as View,
                artist = artistTextField.get(holder) as View,
                actionsAnchor = requireNotNull(player.findViewById(actionsId)),
                firstAction = actions.first(),
                player = player
            )
        }

        fun isPlaying(binder: Any): Boolean {
            val mediaData = mediaDataField.get(binder) ?: return false
            return mediaDataIsPlayingField.get(mediaData) == true
        }

        fun getSeekBar(holder: Any): View = seekBarField.get(holder) as View

        fun getHeadAlphaListenerSeekBar(listener: Any): View {
            return headAlphaListenerSeekBarField.get(listener) as View
        }

        fun getContext(binder: Any): Context = contextField.get(binder) as Context

        fun applyNativeForeground(binder: Any, holder: Any) {
            updateForegroundColorsMethod.invoke(binder, holder)
        }

        fun applyLightForeground(holder: Any, colors: CardColors) {
            (titleTextField.get(holder) as TextView).setTextColor(colors.primaryText)
            (artistTextField.get(holder) as TextView).setTextColor(colors.secondaryText)
            (elapsedTimeViewField.get(holder) as TextView).setTextColor(colors.durationText)
            (totalTimeViewField.get(holder) as TextView).setTextColor(colors.durationText)
            val tint = ColorStateList.valueOf(colors.action)
            (seamlessIconField.get(holder) as ImageView).imageTintList =
                ColorStateList.valueOf(colors.seekBarForeground)
            @Suppress("UNCHECKED_CAST")
            (getActionListMethod.invoke(holder) as List<Any>).forEach { action ->
                (action as ImageView).apply {
                    imageTintBlendMode = BlendMode.SRC_IN
                    imageTintList = tint
                }
            }
            val seekBar = getSeekBar(holder)
            setSeekBarForegroundMethod.invoke(seekBar, colors.seekBarForeground)
            setSeekBarBackgroundMethod.invoke(seekBar, colors.seekBarBackground)
            setSeekBarShaderColorFilter(
                seekBar,
                BlendModeColorFilter(colors.seekBarForeground, BlendMode.SRC_IN)
            )
            setSeekBarHeadGlowAlpha(seekBar, 0f)
        }

        fun getSeekBarShaderColorFilter(seekBar: View): ColorFilter? {
            return (seekBarPaintField.get(seekBar) as Paint).colorFilter
        }

        fun setSeekBarShaderColorFilter(seekBar: View, colorFilter: ColorFilter?) {
            (seekBarPaintField.get(seekBar) as Paint).colorFilter = colorFilter
            seekBar.invalidate()
        }

        fun getSeekBarHeadGlowAlpha(seekBar: View): Float {
            return seekBarHeadGlowAlphaField.getFloat(seekBar)
        }

        fun setSeekBarHeadGlowAlpha(seekBar: View, alpha: Float) {
            seekBarHeadGlowAlphaField.setFloat(seekBar, alpha)
            (seekBarRuntimeShaderField.get(seekBar) as? RuntimeShader)?.setFloatUniform(
                "uHeadGlowAlpha",
                alpha
            )
            seekBar.invalidate()
        }

        fun findExpandedBackgroundTarget(player: View): ExpandedBackgroundTarget? {
            var current: View? = player
            var expandedView: View? = null
            repeat(16) {
                current = current?.parent as? View ?: return null
                if (current.javaClass.name == EXPANDED_VIEW_CLASS) expandedView = current
                if (expandedView != null && current.javaClass.isOrExtends(BASE_CONTENT_VIEW_CLASS)) {
                    return ExpandedBackgroundTarget(current, expandedView)
                }
            }
            return null
        }

        fun getMiniBar(target: ExpandedBackgroundTarget): View? {
            return expandedBackgroundMethods(target).getMiniBar.invoke(target.owner) as? View
        }

        fun applyLiveUpdateBackground(target: ExpandedBackgroundTarget, lightContext: Context) {
            val view = target.expandedView
            val methods = expandedBackgroundMethods(target)
            val blurOpened = methods.getBackgroundBlurOpened.invoke(null, view.context) as Boolean
            if (!blurOpened || view.parent == null) {
                methods.setMiViewBlurMode.invoke(null, view, 0)
                methods.clearMiBackgroundBlendColor.invoke(null, view)
                view.background = requireNotNull(
                    lightContext.getDrawable(methods.liveUpdateBackgroundDrawableId)
                )
                return
            }

            val blendColors = intArrayOf(
                lightContext.getColor(methods.liveUpdateBlendColor1Id),
                lightContext.resources.getInteger(methods.blurModeLinearLightId),
                lightContext.getColor(methods.liveUpdateBlendColor2Id),
                lightContext.resources.getInteger(methods.blurModeLabId),
                lightContext.getColor(methods.liveUpdateBlendColor3Id),
                lightContext.resources.getInteger(methods.blurModePureId)
            )
            methods.setMiViewBlurMode.invoke(null, view, 1)
            methods.clearMiBackgroundBlendColor.invoke(null, view)
            methods.setMiBackgroundBlendColors.invoke(null, view, blendColors, 0.0f, 2, null)
            view.background = null
        }

        fun restoreNativeExpandedBackground(target: ExpandedBackgroundTarget) {
            expandedBackgroundMethods(target).updateBackgroundBg.invoke(
                target.owner,
                target.expandedView,
                false
            )
        }

        private fun expandedBackgroundMethods(
            target: ExpandedBackgroundTarget
        ): ExpandedBackgroundMethods {
            val ownerClass = target.owner.javaClass
            val classLoader = requireNotNull(ownerClass.classLoader) {
                "Expanded island view has no ClassLoader"
            }
            return synchronized(expandedBackgroundMethods) {
                expandedBackgroundMethods.getOrPut(classLoader) {
                    ExpandedBackgroundMethods.create(ownerClass, classLoader)
                }
            }
        }

        private fun Class<*>.isOrExtends(className: String): Boolean {
            var current: Class<*>? = this
            while (current != null) {
                if (current.name == className) return true
                current = current.superclass
            }
            return false
        }

        fun getArtwork(binder: Any): Drawable? = artworkField.get(binder) as? Drawable

        fun pause(view: View) {
            pauseMethod.invoke(view)
        }

        fun setGradientColor(view: View, mainColor: Int, colors: IntArray) {
            setGradientColorMethod.invoke(view, mainColor, colors)
        }

        fun drawableToBitmap(drawable: Drawable): Bitmap? {
            return drawableToBitmapMethod.invoke(null, drawable) as? Bitmap
        }

        companion object {
            fun create(classLoader: ClassLoader): NativeApi {
                val binderClass = classLoader.loadClass(BINDER_CLASS)
                val holderClass = classLoader.loadClass(
                    "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder"
                )
                val musicBgViewClass = classLoader.loadClass(MUSIC_BG_VIEW_CLASS)
                val drawableUtilsClass = classLoader.loadClass("com.miui.utils.DrawableUtils")
                val mediaDataClass = classLoader.loadClass(
                    "com.android.systemui.media.controls.shared.model.MediaData"
                )

                val attach = binderClass.declaredMethods.single {
                    it.name == "attach" && it.parameterCount == 2
                }.apply { isAccessible = true }
                val bind = binderClass.declaredMethods.single {
                    it.name == "bindMediaData" && it.parameterCount == 1
                }.apply { isAccessible = true }
                val detach = binderClass.declaredMethods.single {
                    it.name == "detach" && it.parameterCount == 0
                }.apply { isAccessible = true }
                val setAlbumImage = binderClass.declaredMethods.single {
                    it.name == "setAlbumImage" && it.parameterCount == 1
                }.apply { isAccessible = true }
                val setSeamless = binderClass.declaredMethods.single {
                    it.name == "setSeamless" && it.parameterCount == 2
                }.apply { isAccessible = true }
                val start = musicBgViewClass.getDeclaredMethod("start").apply {
                    isAccessible = true
                }
                val resume = musicBgViewClass.getDeclaredMethod("resume").apply {
                    isAccessible = true
                }
                val seekBarClass = holderClass.getDeclaredField("seekBar").type
                val headAlphaListenerClass = classLoader.loadClass(
                    SEEK_BAR_HEAD_ALPHA_LISTENER_CLASS
                )
                val headAlphaUpdate = headAlphaListenerClass.declaredMethods.single {
                    it.name == "onUpdate" && it.parameterCount == 2
                }.apply { isAccessible = true }

                return NativeApi(
                    hookMethods = listOf(
                        attach,
                        bind,
                        detach,
                        setAlbumImage,
                        setSeamless,
                        binderClass.declaredMethods.single {
                            it.name == "updateForegroundColors" && it.parameterCount == 1
                        }.apply { isAccessible = true },
                        start,
                        resume,
                        headAlphaUpdate
                    ),
                    holderField = binderClass.getDeclaredField("holder").apply {
                        isAccessible = true
                    },
                    dummyHolderField = binderClass.getDeclaredField("dummyHolder").apply {
                        isAccessible = true
                    },
                    artworkField = binderClass.getDeclaredField("artWorkDrawable").apply {
                        isAccessible = true
                    },
                    mediaBgViewField = holderClass.getDeclaredField("mediaBgView").apply {
                        isAccessible = true
                    },
                    playerField = holderClass.getDeclaredField("player").apply {
                        isAccessible = true
                    },
                    contextField = binderClass.getDeclaredField("context").apply {
                        isAccessible = true
                    },
                    titleTextField = holderClass.getDeclaredField("titleText").apply {
                        isAccessible = true
                    },
                    artistTextField = holderClass.getDeclaredField("artistText").apply {
                        isAccessible = true
                    },
                    elapsedTimeViewField = holderClass.getDeclaredField("elapsedTimeView").apply {
                        isAccessible = true
                    },
                    totalTimeViewField = holderClass.getDeclaredField("totalTimeView").apply {
                        isAccessible = true
                    },
                    seamlessIconField = holderClass.getDeclaredField("seamlessIcon").apply {
                        isAccessible = true
                    },
                    albumViewField = holderClass.getDeclaredField("albumView").apply {
                        isAccessible = true
                    },
                    albumImageField = holderClass.getDeclaredField("albumImageView").apply {
                        isAccessible = true
                    },
                    appIconField = holderClass.getDeclaredField("appIcon").apply {
                        isAccessible = true
                    },
                    seamlessField = holderClass.getDeclaredField("seamless").apply {
                        isAccessible = true
                    },
                    mediaDataField = binderClass.getDeclaredField("mediaData").apply {
                        isAccessible = true
                    },
                    mediaDataIsPlayingField = mediaDataClass.getDeclaredField("isPlaying").apply {
                        isAccessible = true
                    },
                    seekBarField = holderClass.getDeclaredField("seekBar").apply {
                        isAccessible = true
                    },
                    seekBarPaintField = seekBarClass.getDeclaredField("mPaint").apply {
                        isAccessible = true
                    },
                    seekBarRuntimeShaderField = seekBarClass.getDeclaredField("runtimeShader").apply {
                        isAccessible = true
                    },
                    seekBarHeadGlowAlphaField = seekBarClass.getDeclaredField("uHeadGlowAlpha").apply {
                        isAccessible = true
                    },
                    headAlphaListenerSeekBarField = headAlphaListenerClass.getDeclaredField(
                        "this\$0"
                    ).apply { isAccessible = true },
                    getActionListMethod = holderClass.getDeclaredMethod("getActionList").apply {
                        isAccessible = true
                    },
                    updateForegroundColorsMethod = binderClass.declaredMethods.single {
                        it.name == "updateForegroundColors" && it.parameterCount == 1
                    }.apply { isAccessible = true },
                    setSeekBarForegroundMethod = seekBarClass.getDeclaredMethod(
                        "setForegroundPrimaryColor",
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true },
                    setSeekBarBackgroundMethod = seekBarClass.getDeclaredMethod(
                        "setBackgroundPrimaryColor",
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true },
                    pauseMethod = musicBgViewClass.getDeclaredMethod("pause").apply {
                        isAccessible = true
                    },
                    setGradientColorMethod = musicBgViewClass.getDeclaredMethod(
                        "setGradientColor",
                        Int::class.javaPrimitiveType,
                        IntArray::class.java
                    ).apply { isAccessible = true },
                    drawableToBitmapMethod = drawableUtilsClass.getDeclaredMethod(
                        "drawable2Bitmap",
                        Drawable::class.java
                    ).apply { isAccessible = true }
                )
            }
        }
    }

    private data class ExpandedBackgroundMethods(
        val updateBackgroundBg: Method,
        val getMiniBar: Method,
        val getBackgroundBlurOpened: Method,
        val setMiViewBlurMode: Method,
        val clearMiBackgroundBlendColor: Method,
        val setMiBackgroundBlendColors: Method,
        val liveUpdateBlendColor1Id: Int,
        val liveUpdateBlendColor2Id: Int,
        val liveUpdateBlendColor3Id: Int,
        val blurModeLinearLightId: Int,
        val blurModeLabId: Int,
        val blurModePureId: Int,
        val liveUpdateBackgroundDrawableId: Int
    ) {
        companion object {
            fun create(ownerClass: Class<*>, classLoader: ClassLoader): ExpandedBackgroundMethods {
                val baseContentViewClass = generateSequence(ownerClass as Class<*>?) {
                    it.superclass
                }.firstOrNull { it.name == BASE_CONTENT_VIEW_CLASS }
                    ?: error("Missing superclass: $BASE_CONTENT_VIEW_CLASS")
                val miBlurCompatClass = classLoader.loadClass(MI_BLUR_COMPAT_CLASS)
                val colorClass = classLoader.loadClass("miui.systemui.dynamicisland.R\$color")
                val integerClass = classLoader.loadClass("miui.systemui.dynamicisland.R\$integer")
                val drawableClass = classLoader.loadClass("miui.systemui.dynamicisland.R\$drawable")
                return ExpandedBackgroundMethods(
                    updateBackgroundBg = baseContentViewClass.getDeclaredMethod(
                        "updateBackgroundBg",
                        View::class.java,
                        Boolean::class.javaPrimitiveType
                    ).apply { isAccessible = true },
                    getMiniBar = baseContentViewClass.getDeclaredMethod("getMiniBar").apply {
                        isAccessible = true
                    },
                    getBackgroundBlurOpened = miBlurCompatClass.getDeclaredMethod(
                        "getBackgroundBlurOpened",
                        Context::class.java
                    ).apply { isAccessible = true },
                    setMiViewBlurMode = miBlurCompatClass.getDeclaredMethod(
                        "setMiViewBlurModeCompat",
                        View::class.java,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true },
                    clearMiBackgroundBlendColor = miBlurCompatClass.getDeclaredMethod(
                        "clearMiBackgroundBlendColorCompat",
                        View::class.java
                    ).apply { isAccessible = true },
                    setMiBackgroundBlendColors = miBlurCompatClass.getDeclaredMethod(
                        "setMiBackgroundBlendColors\$default",
                        View::class.java,
                        IntArray::class.java,
                        Float::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Any::class.java
                    ).apply { isAccessible = true },
                    liveUpdateBlendColor1Id = colorClass.resourceId(
                        "liveupdate_island_element_blend_shade_color_1"
                    ),
                    liveUpdateBlendColor2Id = colorClass.resourceId(
                        "liveupdate_island_element_blend_shade_color_2"
                    ),
                    liveUpdateBlendColor3Id = colorClass.resourceId(
                        "liveupdate_island_element_blend_shade_color_3"
                    ),
                    blurModeLinearLightId = integerClass.resourceId("blur_mode_linear_light"),
                    blurModeLabId = integerClass.resourceId("blur_mode_lab"),
                    blurModePureId = integerClass.resourceId("blur_mode_pure"),
                    liveUpdateBackgroundDrawableId = drawableClass.resourceId(
                        "dynamic_island_liveupdate_background"
                    )
                )
            }

            private fun Class<*>.resourceId(name: String): Int {
                return getDeclaredField(name).apply { isAccessible = true }.getInt(null)
            }
        }
    }
}
