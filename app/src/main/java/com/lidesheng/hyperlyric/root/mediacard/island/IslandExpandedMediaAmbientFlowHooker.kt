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
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.island.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.IslandProbeUtils
import com.lidesheng.hyperlyric.root.mediacard.MediaArtworkSampler
import com.lidesheng.hyperlyric.root.mediacard.island.background.IslandExpandedBackgroundTarget
import com.lidesheng.hyperlyric.root.mediacard.island.background.IslandExpandedMediaBackgroundApi
import com.lidesheng.hyperlyric.root.mediacard.island.background.IslandExpandedMediaBackgroundController
import com.lidesheng.hyperlyric.root.mediacard.island.background.IslandExpandedMediaBackgroundHost
import com.lidesheng.hyperlyric.root.mediacard.notification.background.NotificationMediaColorConfig
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
    private const val FAKE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
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
    private val bindingBinder = ThreadLocal<Any?>()
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
        IslandExpandedMediaBackgroundController.initialize(xposedModule)
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
        IslandExpandedMediaBackgroundController.releaseAll()
        val binders = synchronized(activeBinders) { activeBinders.toList() }
        val cleanup = Runnable {
            binders.forEach { binder ->
                restoreCardTheme(binder)
                restoreMediaElements(binder)
            }
            val api = nativeApi
            if (api != null) {
                val trackedViews = synchronized(themeStates) { themeStates.keys.toList() }
                trackedViews.forEach { view -> restoreTrackedTheme(view, api) }
            }
            IslandExpandedMediaElementController.cleanup()
            activeBinders.clear()
            themeStates.clear()
            seekBarThemeStates.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run()
        else Handler(Looper.getMainLooper()).post(cleanup)
        binderStates.clear()
        colorExecutor.shutdown()
    }

    private enum class Action { ATTACH, BIND, DETACH, ALBUM, SEAMLESS }

    private class BinderHook(private val action: Action) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val binder = chain.thisObject ?: return chain.proceed()
            if (action == Action.DETACH) cleanupBinder(binder)
            val nestedInBind = action != Action.BIND && bindingBinder.get() === binder
            val previousBinding = if (action == Action.BIND) bindingBinder.get() else null
            if (action == Action.BIND) bindingBinder.set(binder)
            val result = try {
                chain.proceed()
            } finally {
                if (action == Action.BIND) {
                    if (previousBinding == null) bindingBinder.remove()
                    else bindingBinder.set(previousBinding)
                }
            }
            if (nestedInBind && (action == Action.ALBUM || action == Action.SEAMLESS)) {
                return result
            }
            runCatching {
                when (action) {
                    Action.ATTACH -> {
                        activeBinders.add(binder)
                        applyAppearance(binder, allowCoverColor = false)
                        applyMediaElements(binder)
                    }
                    Action.BIND -> {
                        activeBinders.add(binder)
                        applyAppearance(binder, allowCoverColor = true)
                        applyMediaElements(binder)
                    }
                    Action.ALBUM -> {
                        applyAppearance(binder, allowCoverColor = true)
                        applyMediaElements(binder)
                    }
                    Action.SEAMLESS -> applyMediaElements(binder)
                    Action.DETACH -> Unit
                }
                if (action != Action.DETACH) {
                    IslandAlbumCoverStyleHooker.onPlaybackStateChanged(
                        requireNotNull(nativeApi).isPlaying(binder)
                    )
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
            if ((IslandExpandedMediaBackgroundController.isActive() ||
                    currentMode() == RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED) &&
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
            if (IslandExpandedMediaBackgroundController.isActive()) {
                val api = nativeApi ?: return chain.proceed()
                return runCatching {
                    if (!IslandExpandedMediaBackgroundController.applyForeground(
                            binder,
                            holder,
                            api
                        )
                    ) {
                        IslandExpandedMediaBackgroundController.apply(binder, api)
                    }
                    null
                }.getOrElse { error ->
                    HookLogger.e(TAG, "Failed to preserve custom expanded foreground", error)
                    chain.proceed()
                }
            }
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
            if (seekBarThemeStates[seekBar]?.suppressHeadGlow == true) {
                api.setSeekBarHeadGlowAlpha(seekBar, 0f)
            }
            return result
        }
    }

    internal class BackgroundUpdateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.args.firstOrNull() as? View
            return if (
                view != null &&
                IslandExpandedMediaBackgroundController.shouldSkipNativeBackgroundUpdate(view)
            ) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    internal class ExpandedVisibilityHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val visibility = (chain.args.getOrNull(1) as? Number)?.toInt()
            val view = chain.thisObject as? View
            if (visibility == View.VISIBLE && view?.isShown == true) {
                IslandExpandedMediaBackgroundController.onExpandedViewShown(view)
            }
            return result
        }
    }

    internal class ClosingToExpandedHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (chain.args.getOrNull(1) == true) {
                (chain.thisObject as? ViewGroup)?.let(::restoreFakeTransitionTheme)
            }
            return result
        }
    }

    internal class MiniBarUpdateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val contentView = chain.thisObject as? View ?: return@runCatching
                applyContentViewTheme(contentView)
            }.onFailure { error ->
                HookLogger.e(TAG, "Failed to restore expanded media mini bar theme", error)
            }
            return result
        }
    }

    fun refreshCardTheme() {
        val refresh = Runnable {
            val snapshot = synchronized(activeBinders) { activeBinders.toList() }
            snapshot.forEach { binder ->
                runCatching { applyAppearance(binder, allowCoverColor = true) }
                    .onFailure { HookLogger.e(TAG, "Failed to refresh expanded media theme", it) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else Handler(Looper.getMainLooper()).post(refresh)
    }

    fun refreshBackgroundStyle() {
        val refresh = Runnable {
            val snapshot = synchronized(activeBinders) { activeBinders.toList() }
            snapshot.forEach { binder ->
                runCatching { applyAppearance(binder, allowCoverColor = true) }
                    .onFailure {
                        HookLogger.e(TAG, "Failed to refresh expanded media background", it)
                    }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else Handler(Looper.getMainLooper()).post(refresh)
    }

    fun applyFakeTransitionTheme(fakeContentView: ViewGroup) {
        applyCustomFakeTransitionTheme(fakeContentView)
    }

    fun restoreFakeTransitionTheme(fakeContentView: ViewGroup) {
        val dataOwner = fakeContentView.javaClass.getMethod("getRealView").invoke(fakeContentView)
        if (!IslandProbeUtils.isMediaIsland(IslandProbeUtils.getCurrentIslandData(dataOwner))) return
        val api = nativeApi ?: return
        val target = api.findContentBackgroundTarget(fakeContentView) ?: return
        IslandExpandedMediaBackgroundController.restoreFakeTransition(target, api)
    }

    private fun applyCustomFakeTransitionTheme(fakeContentView: ViewGroup) {
        val dataOwner = fakeContentView.javaClass.getMethod("getRealView").invoke(fakeContentView)
        if (!IslandProbeUtils.isMediaIsland(IslandProbeUtils.getCurrentIslandData(dataOwner))) return
        val api = nativeApi ?: return
        val target = api.findContentBackgroundTarget(fakeContentView) ?: return
        if (
            IslandExpandedMediaBackgroundController.applyFakeTransition(
                target,
                api.getMiniBar(target),
                api
            )
        ) {
            synchronized(activeBinders) { activeBinders.toList() }.forEach { binder ->
                IslandExpandedMediaBackgroundController.applyForeground(binder, api)
            }
        }
    }

    private fun applyContentViewTheme(contentView: View) {
        val isFakeView = contentView.javaClass.name == FAKE_CONTENT_VIEW_CLASS
        val dataOwner = if (isFakeView) {
            contentView.javaClass.methods.firstOrNull {
                it.name == "getRealView" && it.parameterTypes.isEmpty()
            }?.invoke(contentView)
        } else {
            contentView
        }
        if (!IslandProbeUtils.isMediaIsland(IslandProbeUtils.getCurrentIslandData(dataOwner))) return

        val api = nativeApi ?: return
        val target = api.findContentBackgroundTarget(contentView) ?: return
        if (IslandExpandedMediaBackgroundController.isActive()) {
            return
        }
        if (isFakeView) {
            IslandExpandedMediaBackgroundController.restoreFakeTransition(target, api)
        }
        if (!shouldUseLightTheme(contentView.context)) {
            restoreTrackedTheme(contentView, api)
            return
        }

        applyLightExpandedBackground(api, target)
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

    private fun applyAppearance(binder: Any, allowCoverColor: Boolean) {
        applyMode(binder, allowCoverColor)
        val api = nativeApi ?: return
        if (IslandExpandedMediaBackgroundController.isActive()) {
            if (allowCoverColor) IslandExpandedMediaBackgroundController.apply(binder, api)
        } else {
            IslandExpandedMediaBackgroundController.restore(binder)
            applyCardTheme(binder)
        }
    }

    private fun applyLightForeground(api: NativeApi, holder: Any, colors: CardColors) {
        val seekBar = api.getSeekBar(holder)
        val state = seekBarThemeStates.getOrPut(seekBar) {
            SeekBarThemeState(
                originalColorFilter = api.getSeekBarShaderColorFilter(seekBar),
                originalHeadGlowAlpha = api.getSeekBarHeadGlowAlpha(seekBar)
            )
        }
        state.suppressHeadGlow = true
        api.applyLightForeground(holder, colors)
    }

    private fun applyLightExpandedBackground(
        api: NativeApi,
        player: View
    ): Boolean {
        val target = api.findExpandedBackgroundTarget(player) ?: return false
        applyLightExpandedBackground(api, target)
        return true
    }

    private fun applyLightExpandedBackground(
        api: NativeApi,
        target: IslandExpandedBackgroundTarget
    ) {
        val state = themeStates.getOrPut(target.owner) {
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
    }

    private fun shouldUseLightTheme(binder: Any): Boolean {
        val api = nativeApi ?: return false
        return shouldUseLightTheme(api.getContext(binder))
    }

    private fun shouldUseLightTheme(context: Context): Boolean {
        return when (currentCardTheme()) {
            RootConstants.MEDIA_CARD_THEME_ALWAYS_LIGHT -> true
            RootConstants.MEDIA_CARD_THEME_ALWAYS_DARK -> false
            else -> !context.resources.configuration.isNightMode
        }
    }

    private fun restoreTrackedTheme(view: View, api: NativeApi) {
        themeStates.remove(view)?.let { state ->
            state.miniBar?.backgroundTintList = state.originalMiniBarTint
            api.restoreNativeExpandedBackground(state.target)
        }
    }

    private fun restoreCardTheme(binder: Any) {
        val api = nativeApi ?: return
        api.getHolders(binder).forEach { holder ->
            val player = api.getPlayer(holder)
            val seekBar = api.getSeekBar(holder)
            api.findExpandedBackgroundTarget(player)?.let { target ->
                restoreTrackedTheme(target.owner, api)
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

        if (IslandExpandedMediaBackgroundController.isActive()) {
            binderStates.remove(binder)?.request?.incrementAndGet()
            views.forEach { view -> hideAmbientFlow(view, api) }
            return
        }

        when (currentMode()) {
            RootConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED -> {
                binderStates.remove(binder)?.request?.incrementAndGet()
                views.forEach { view -> hideAmbientFlow(view, api) }
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
        val bitmap = MediaArtworkSampler.sample(drawable) ?: return
        state.colorToken = token
        state.palette = null
        val request = state.request.incrementAndGet()

        runCatching {
            colorExecutor.execute {
                if (binderStates[binder] !== state || state.request.get() != request) {
                    bitmap.recycle()
                    return@execute
                }
                val palette = runCatching { extractPalette(bitmap) }
                    .onFailure { HookLogger.e(TAG, "Failed to extract expanded media colors", it) }
                    .getOrNull()
                bitmap.recycle()
                primaryView.post {
                    val current = binderStates[binder]
                    if (current !== state || current.request.get() != request) return@post
                    if (palette == null) {
                        current.colorToken = null
                        return@post
                    }
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
        IslandExpandedMediaBackgroundController.restore(binder)
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

    private fun hideAmbientFlow(view: View, api: NativeApi) {
        val alreadyHidden = view.getTag(ORIGINAL_ALPHA_TAG_KEY) != null && view.alpha == 0f
        if (alreadyHidden) return
        if (view.getTag(ORIGINAL_ALPHA_TAG_KEY) == null) {
            view.setTag(ORIGINAL_ALPHA_TAG_KEY, view.alpha)
        }
        view.alpha = 0f
        api.pause(view)
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
        val target: IslandExpandedBackgroundTarget,
        val miniBar: View?,
        val originalMiniBarTint: ColorStateList?
    )

    private data class SeekBarThemeState(
        val originalColorFilter: ColorFilter?,
        val originalHeadGlowAlpha: Float,
        var suppressHeadGlow: Boolean = false
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
        private val mediaDataPackageNameField: Field,
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
        private val setGradientColorMethod: Method
    ) : IslandExpandedMediaBackgroundApi {
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

        override fun getContext(binder: Any): Context = contextField.get(binder) as Context

        override fun getPackageName(binder: Any): String? {
            val mediaData = mediaDataField.get(binder) ?: return null
            return mediaDataPackageNameField.get(mediaData) as? String
        }

        override fun getBackgroundHosts(binder: Any): List<IslandExpandedMediaBackgroundHost> {
            return getHolders(binder).mapNotNull { holder ->
                val target = findExpandedBackgroundTarget(getPlayer(holder)) ?: return@mapNotNull null
                IslandExpandedMediaBackgroundHost(target, holder, getMiniBar(target))
            }
        }

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

        override fun applyCustomForeground(
            holder: Any,
            colors: NotificationMediaColorConfig
        ) {
            val seekBar = getSeekBar(holder)
            val state = seekBarThemeStates.getOrPut(seekBar) {
                SeekBarThemeState(
                    originalColorFilter = getSeekBarShaderColorFilter(seekBar),
                    originalHeadGlowAlpha = getSeekBarHeadGlowAlpha(seekBar)
                )
            }
            state.suppressHeadGlow = false
            (titleTextField.get(holder) as TextView).setTextColor(colors.textPrimary)
            (artistTextField.get(holder) as TextView).setTextColor(colors.textSecondary)
            (elapsedTimeViewField.get(holder) as TextView).setTextColor(colors.textSecondary)
            (totalTimeViewField.get(holder) as TextView).setTextColor(colors.textSecondary)
            val tint = ColorStateList.valueOf(colors.textPrimary)
            (seamlessIconField.get(holder) as ImageView).imageTintList = tint
            @Suppress("UNCHECKED_CAST")
            (getActionListMethod.invoke(holder) as List<Any>).forEach { action ->
                (action as ImageView).apply {
                    imageTintBlendMode = BlendMode.SRC_IN
                    imageTintList = tint
                }
            }
            setSeekBarForegroundMethod.invoke(seekBar, colors.textPrimary)
            setSeekBarBackgroundMethod.invoke(
                seekBar,
                colors.textPrimary and 0x00ffffff or (0x33 shl 24)
            )
            setSeekBarShaderColorFilter(
                seekBar,
                BlendModeColorFilter(colors.textPrimary, BlendMode.SRC_IN)
            )
            setSeekBarHeadGlowAlpha(seekBar, state.originalHeadGlowAlpha)
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

        fun findExpandedBackgroundTarget(player: View): IslandExpandedBackgroundTarget? {
            var current: View? = player
            var expandedView: View? = null
            repeat(16) {
                current = current?.parent as? View ?: return null
                if (current.javaClass.name == EXPANDED_VIEW_CLASS) expandedView = current
                if (expandedView != null && current.javaClass.isOrExtends(BASE_CONTENT_VIEW_CLASS)) {
                    val owner = current ?: return null
                    val expanded = expandedView ?: return null
                    fun dimension(name: String): Int {
                        return (owner.javaClass.methods.single {
                            it.name == name && it.parameterTypes.isEmpty()
                        }.invoke(owner) as Number).toInt()
                    }
                    return IslandExpandedBackgroundTarget(
                        owner = owner,
                        expandedView = expanded,
                        viewportWidth = dimension("getExpandedViewWidth"),
                        viewportHeight = dimension("getExpandedViewHeight")
                    )
                }
            }
            return null
        }

        fun findContentBackgroundTarget(contentView: View): IslandExpandedBackgroundTarget? {
            val isFakeView = contentView.javaClass.name == FAKE_CONTENT_VIEW_CLASS
            val getterName = if (isFakeView) "getFakeExpandedView" else "getExpandedView"
            val expandedView = contentView.javaClass.methods.firstOrNull {
                it.name == getterName && it.parameterTypes.isEmpty()
            }?.invoke(contentView) as? View ?: return null
            if (isFakeView) {
                val realView = contentView.javaClass.methods.single {
                    it.name == "getRealView" && it.parameterTypes.isEmpty()
                }.invoke(contentView) as View
                fun realDimension(name: String): Int {
                    return (realView.javaClass.methods.single {
                        it.name == name && it.parameterTypes.isEmpty()
                    }.invoke(realView) as Number).toInt()
                }
                val left = realDimension("getExpandedViewMarginHorizontal")
                val top = realDimension("getIslandViewMarginTop")
                val width = realDimension("getExpandedViewWidth")
                val height = realDimension("getExpandedViewHeight")
                val fakeContainer = contentView.javaClass.methods.single {
                    it.name == "getFakeContainer" && it.parameterTypes.isEmpty()
                }.invoke(contentView) as View
                return IslandExpandedBackgroundTarget(
                    owner = contentView,
                    expandedView = expandedView,
                    customBackgroundView = expandedView,
                    extensionBackgroundView = contentView,
                    viewportWidth = width,
                    viewportHeight = height,
                    transitionContentBounds = Rect(left, top, left + width, top + height),
                    transitionOccludingViews = listOf(fakeContainer),
                    nativeBackgroundViews = listOf(expandedView)
                )
            }
            return IslandExpandedBackgroundTarget(contentView, expandedView)
        }

        fun getMiniBar(target: IslandExpandedBackgroundTarget): View? {
            return expandedBackgroundMethods(target).getMiniBar.invoke(target.owner) as? View
        }

        fun applyLiveUpdateBackground(
            target: IslandExpandedBackgroundTarget,
            lightContext: Context
        ) {
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

        fun restoreNativeExpandedBackground(target: IslandExpandedBackgroundTarget) {
            expandedBackgroundMethods(target).updateBackgroundBg.invoke(
                target.owner,
                target.expandedView,
                false
            )
        }

        override fun prepareCustomBackground(target: IslandExpandedBackgroundTarget) {
            val methods = expandedBackgroundMethods(target)
            target.nativeBackgroundViews.forEach { view ->
                methods.setMiViewBlurMode.invoke(null, view, 0)
                methods.clearMiBackgroundBlendColor.invoke(null, view)
                if (view !== target.customBackgroundView) view.background = null
            }
        }

        override fun restoreNativeBackground(target: IslandExpandedBackgroundTarget) {
            target.nativeBackgroundViews.forEach { view ->
                expandedBackgroundMethods(target).updateBackgroundBg.invoke(
                    target.owner,
                    view,
                    false
                )
            }
        }

        private fun expandedBackgroundMethods(
            target: IslandExpandedBackgroundTarget
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

        override fun getArtwork(binder: Any): Drawable? = artworkField.get(binder) as? Drawable

        fun pause(view: View) {
            pauseMethod.invoke(view)
        }

        fun setGradientColor(view: View, mainColor: Int, colors: IntArray) {
            setGradientColorMethod.invoke(view, mainColor, colors)
        }

        companion object {
            fun create(classLoader: ClassLoader): NativeApi {
                val binderClass = classLoader.loadClass(BINDER_CLASS)
                val holderClass = classLoader.loadClass(
                    "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder"
                )
                val musicBgViewClass = classLoader.loadClass(MUSIC_BG_VIEW_CLASS)
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
                    mediaDataPackageNameField = mediaDataClass.getDeclaredField("packageName").apply {
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
