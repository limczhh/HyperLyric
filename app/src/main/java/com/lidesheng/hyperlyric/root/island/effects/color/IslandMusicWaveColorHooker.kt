package com.lidesheng.hyperlyric.root.island.effects.color

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.SuperIslandContentStylePolicy
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.SystemUiEnhancementGate
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.island.effects.spectrum.IslandMusicWaveSpectrumHooker
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.policy.IslandModificationTargetPolicy
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal object IslandMusicWaveColorHooker {
    private const val TAG = "IslandMusicWaveColorHooker"
    private val DEFAULT_NATIVE_COLORS = arrayOf(-1, -1)
    private const val ICON_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    const val NATIVE_GRADIENT_CAPABILITY = "island.music_wave.native_gradient"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val trackedHolders = WeakHashMap<Any, TrackedHolder>()
    private val registeringHolder = ThreadLocal<Any>()
    private val callbackHolders = WeakHashMap<Any, WeakReference<Any>>()
    private val hookedGradientMethods = mutableSetOf<Method>()

    @Volatile
    private var generation = 0

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var overrideApplied = false

    private val prefs: SharedPreferences?
        get() = (module as? HookEntry)?.prefs

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        module = xposedModule
        if (!hookedClassLoaders.add(classLoader)) return

        try {
            val holderClass = classLoader.loadClass(ICON_HOLDER_CLASS)
            val api = IslandMusicWaveNativeApi.create(holderClass)
            // Keep Lottie's frame caller interpreted so an inlined callback cannot bypass the Hook.
            api.gradientCallers.forEach(xposedModule::deoptimize)
            val dataField = holderClass.declaredFields.firstOrNull {
                it.name == "data"
            }?.apply {
                isAccessible = true
            }
            val lottieViewField = holderClass.getDeclaredField("lottieView").apply {
                isAccessible = true
            }

            api.callbackRegistration?.let { method ->
                method.isAccessible = true
                xposedModule.managedHook(
                    executable = method,
                    capability = "island.music_wave.capture_callback",
                    hooker = CaptureGradientCallbackHook(api, dataField, lottieViewField),
                )
            }

            val paletteMethod = api.paletteMethod
            if (paletteMethod != null) {
                paletteMethod.isAccessible = true
                xposedModule.managedHook(
                    executable = paletteMethod,
                    capability = if (paletteMethod.name == "setLottieColor")
                        "island.music_wave.set_lottie_color"
                    else "island.music_wave.get_lottie_color",
                    hooker = ArtworkPaletteHook(
                        api, dataField, lottieViewField,
                        holderClass.declaredFields.firstOrNull {
                            paletteMethod.name == "getLottieColor" && it.name == "cachedBitmap"
                        }?.apply { isAccessible = true }
                    ),
                )
            } else {
                HookLogger.w(TAG, "音频律动原生取色接口不可用: target=setLottieColor/getLottieColor")
            }

            val picInfoField = holderClass.getDeclaredField("picInfo").apply {
                isAccessible = true
            }
            val registerCallbackMethod = holderClass.declaredMethods.firstOrNull {
                it.name == "registerLottieCallback" && it.parameterTypes.isEmpty()
            }
            if (registerCallbackMethod != null) {
                registerCallbackMethod.isAccessible = true
                xposedModule.managedHook(
                    executable = registerCallbackMethod,
                    capability = "island.music_wave.register_callback",
                    hooker = RegisterLottieCallbackHook(dataField, lottieViewField, picInfoField),
                )
            } else {
                HookLogger.w(TAG, "音频律动刷新接口不可用: target=registerLottieCallback")
            }

            HookLogger.d(TAG, "音频律动封面色 Hook 已初始化")
        } catch (e: ClassNotFoundException) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "当前插件不支持音频律动封面色: reason=${e.message}")
        } catch (e: NoSuchFieldException) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "音频律动颜色字段不可用: reason=${e.message}")
        } catch (e: Throwable) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.e(TAG, "初始化音频律动封面色 Hook 失败", e)
        }
    }

    fun refresh() {
        runOnMain {
            val sharedPrefs = prefs
            val disabledReason = when {
                sharedPrefs == null -> "no_remote_preferences"
                !SystemUiEnhancementGate.isEnabled() -> "system_ui_enhancement_disabled"
                SuperIslandContentStylePolicy.readMusicWaveMode(sharedPrefs) ==
                        RootConstants.ISLAND_MUSIC_WAVE_MODE_HIDDEN ->
                    "music_wave_hidden"
                !SuperIslandContentStylePolicy.usesMusicWaveCoverColor(
                    SuperIslandContentStylePolicy.readMusicWaveColorStyle(sharedPrefs)
                ) -> "music_wave_cover_color_disabled"

                else -> null
            }
            if (disabledReason != null) {
                HookLogger.dState(
                    stateId = "IslandMusicWaveColorHooker.refresh",
                    tag = TAG,
                    state = "disabled|$disabledReason"
                ) {
                    "音频律动封面色未生效: reason=$disabledReason"
                }
                restoreNativeColors()
            } else {
                val effectivePrefs = sharedPrefs ?: return@runOnMain
                val scope = IslandModificationTargetPolicy.currentScope(effectivePrefs)
                val synced = when (scope) {
                    IslandModificationTargetPolicy.Scope.ALL_MEDIA ->
                        applyMediaColors(effectivePrefs)

                    IslandModificationTargetPolicy.Scope.INJECTED_LYRIC -> {
                        val colorSession = CoverColorHelper.currentSession()
                        val artworkRequest = CoverColorHelper.currentArtworkRequest()
                        when {
                            artworkRequest != null ->
                                applyCachedColors(effectivePrefs, artworkRequest)

                            colorSession != null -> applyCachedColors(effectivePrefs, colorSession)
                            else -> false
                        }
                    }
                }
                if (!synced) {
                    HookLogger.dState(
                        stateId = "IslandMusicWaveColorHooker.refresh",
                        tag = TAG,
                        state = "not_synced|$scope"
                    ) {
                        "音频律动封面色未同步: scope=$scope"
                    }
                    restoreNativeColors()
                } else {
                    HookLogger.dState(
                        stateId = "IslandMusicWaveColorHooker.refresh",
                        tag = TAG,
                        state = "synced|$scope"
                    ) {
                        "音频律动封面色已同步: scope=$scope"
                    }
                }
            }
        }
    }

    /** Only framework containers, native objects and primitive arrays cross module generations. */
    fun prepareForHotReload(): ArrayList<Any?> {
        check(Looper.myLooper() == Looper.getMainLooper())
        val callbacks = ArrayList<Any?>()
        synchronized(callbackHolders) {
            callbackHolders.forEach { (callback, holderRef) ->
                val holder = holderRef.get() ?: return@forEach
                val tracked = synchronized(trackedHolders) { trackedHolders[holder] }
                    ?: return@forEach
                val data = tracked.dataRef.get() ?: return@forEach
                callbacks.add(arrayListOf(callback, holder, data, tracked.bitmapRef?.get(),
                    tracked.palette?.copyOf(), tracked.nativeColors?.copyOf()))
            }
        }
        cleanup()
        return callbacks
    }

    fun restoreDynamicHooks(
        xposedModule: XposedModule,
        methods: List<Method>,
        classLoaders: Iterable<ClassLoader>,
    ) {
        if (methods.isEmpty()) return
        classLoaders.forEach { loader ->
            val holderClass = try {
                loader.loadClass(ICON_HOLDER_CLASS)
            } catch (_: ClassNotFoundException) {
                return@forEach
            }
            val api = IslandMusicWaveNativeApi.create(holderClass)
            val dataField = holderClass.getDeclaredField("data").apply { isAccessible = true }
            methods.filter { it in api.callbackMethods(it.declaringClass) }.forEach { method ->
                installNativeGradientHook(xposedModule, method, dataField)
            }
        }
    }

    fun adoptHotReloadCallbacks(callbacks: List<*>) {
        check(Looper.myLooper() == Looper.getMainLooper())
        callbacks.forEach { raw ->
            val entry = raw as? ArrayList<*> ?: return@forEach
            val callback = entry.getOrNull(0) ?: return@forEach
            val holder = entry.getOrNull(1) ?: return@forEach
            val data = entry.getOrNull(2) ?: return@forEach
            runCatching {
                val holderClass = holder.javaClass
                val api = IslandMusicWaveNativeApi.create(holderClass)
                val dataField = holderClass.getDeclaredField("data").apply { isAccessible = true }
                if (dataField.get(holder) !== data) return@runCatching
                val picInfo = holderClass.getDeclaredField("picInfo").apply { isAccessible = true }.get(holder)
                if (!isMusicWave(picInfo)) return@runCatching
                val methods = api.callbackMethods(callback.javaClass)
                check(methods.isNotEmpty()) { "Transferred music-wave callback is incompatible" }
                methods.forEach { installNativeGradientHook(requireNotNull(module), it, dataField) }
                val view = holderClass.getDeclaredField("lottieView").apply { isAccessible = true }
                    .get(holder) as? View ?: return@runCatching
                val target = IslandModificationTargetPolicy.resolve(
                    data, IslandProbeUtils.getHolderRootView(holder)
                )
                if (!target.isMediaTarget) {
                    return@runCatching
                }
                trackHolder(holder, data, view)
                synchronized(callbackHolders) { callbackHolders[callback] = WeakReference(holder) }
                val bitmap = runCatching {
                    holderClass.getDeclaredField("cachedBitmap").apply { isAccessible = true }.get(holder)
                }.getOrNull() as? Bitmap
                val palette = entry.getOrNull(4) as? IntArray
                if (palette != null && (bitmap == null || bitmap === entry.getOrNull(3))) {
                    rememberArtworkPalette(holder, palette, bitmap, null)
                }
                @Suppress("UNCHECKED_CAST")
                val nativeColors = entry.getOrNull(5) as? Array<Int>
                synchronized(trackedHolders) { trackedHolders[holder]?.nativeColors = nativeColors }
            }.onFailure { HookLogger.e(TAG, "接管音频律动颜色回调失败", it) }
        }
        refresh()
        IslandMusicWaveSpectrumHooker.refresh()
    }

    private fun installNativeGradientHook(xposedModule: XposedModule, method: Method, dataField: Field?) {
        synchronized(hookedGradientMethods) {
            if (method in hookedGradientMethods) return
            method.isAccessible = true
            xposedModule.managedHook(
                executable = method,
                capability = NATIVE_GRADIENT_CAPABILITY,
                hooker = NativeGradientValueHook(dataField),
            )
            hookedGradientMethods.add(method)
        }
    }

    fun cleanup() {
        runOnMain {
            generation++
            restoreNativeColors()
            synchronized(callbackHolders) { callbackHolders.clear() }
            synchronized(hookedGradientMethods) { hookedGradientMethods.clear() }
            registeringHolder.remove()
            synchronized(trackedHolders) {
                trackedHolders.clear()
            }
            hookedClassLoaders.clear()
            module = null
        }
    }

    private fun applyOptimizedColors(
        colors: WaveColors
    ) {
        if (!hasAllowedTrackedHolder()) {
            if (overrideApplied) restoreNativeColors()
            return
        }
        val appliedTargetCount = allowedTrackedHolderCount()
        if (appliedTargetCount == 0) {
            if (overrideApplied) restoreNativeColors()
            return
        }
        synchronized(trackedHolders) {
            trackedHolders.forEach { (holder, tracked) ->
                tracked.overrideColors = if (isAllowedTarget(holder, tracked))
                    arrayOf(colors.top, colors.bottom) else null
            }
        }
        overrideApplied = true
        HookLogger.dState(
            stateId = "IslandMusicWaveColorHooker.apply",
            tag = TAG,
            state = "${colors.top}|${colors.bottom}|$appliedTargetCount"
        ) {
            "音频律动封面色已注入: top=#${Integer.toHexString(colors.top)}, " +
                    "bottom=#${Integer.toHexString(colors.bottom)}, " +
                    "appliedTargets=$appliedTargetCount, trackedHolders=${trackedHolderCount()}"
        }
        invalidateTrackedLottieViews()
    }

    private fun restoreNativeColors() {
        synchronized(trackedHolders) { trackedHolders.values.forEach { it.overrideColors = null } }
        if (overrideApplied) {
            overrideApplied = false
            HookLogger.dState(
                stateId = "IslandMusicWaveColorHooker.apply",
                tag = TAG,
                state = "restored"
            ) {
                "音频律动封面色已恢复原生颜色"
            }
        }
        invalidateTrackedLottieViews()
    }

    private fun colorsFromPalette(colors: List<Int>, useGradient: Boolean): WaveColors? {
        val primary = colors.firstOrNull() ?: return null
        val secondary = if (useGradient) colors.getOrNull(1) ?: primary else primary
        return WaveColors(
            top = primary,
            bottom = secondary
        )
    }

    private fun applyCachedColors(
        sharedPrefs: SharedPreferences,
        colorSession: CoverColorHelper.ColorSession
    ): Boolean {
        if (!CoverColorHelper.isCurrentSession(colorSession)) return false
        val useGradient = SuperIslandContentStylePolicy.usesMusicWaveGradient(
            SuperIslandContentStylePolicy.readMusicWaveColorStyle(sharedPrefs)
        )
        val colors = CoverColorHelper
            .getCachedColors(useGradient = true, session = colorSession)
            ?.second
            ?.toList()
            ?.let { colorsFromPalette(it, useGradient) }
            ?: return false
        applyOptimizedColors(colors)
        return true
    }

    private fun applyCachedColors(
        sharedPrefs: SharedPreferences,
        artworkRequest: CoverColorHelper.ArtworkRequest
    ): Boolean {
        if (!CoverColorHelper.isCurrentArtwork(artworkRequest)) return false
        val useGradient = SuperIslandContentStylePolicy.usesMusicWaveGradient(
            SuperIslandContentStylePolicy.readMusicWaveColorStyle(sharedPrefs)
        )
        val colors = CoverColorHelper
            .getCachedColors(useGradient = true, request = artworkRequest)
            ?.second
            ?.toList()
            ?.let { colorsFromPalette(it, useGradient) }
            ?: return false
        applyOptimizedColors(colors)
        return true
    }

    private fun applyMediaColors(sharedPrefs: SharedPreferences): Boolean {
        val useGradient = SuperIslandContentStylePolicy.usesMusicWaveGradient(
            SuperIslandContentStylePolicy.readMusicWaveColorStyle(sharedPrefs)
        )
        var applied = 0
        synchronized(trackedHolders) {
            trackedHolders.forEach { (holder, tracked) ->
                val colors = tracked.palette?.toList()?.let { colorsFromPalette(it, useGradient) }
                tracked.overrideColors = if (colors != null && isAllowedTarget(holder, tracked)) {
                    applied++
                    arrayOf(colors.top, colors.bottom)
                } else null
            }
        }
        overrideApplied = applied > 0
        invalidateTrackedLottieViews()
        return applied > 0
    }

    /** Shared by the original Lottie and the spectrum renderer; always scoped to one holder. */
    fun colorsForHolder(holder: Any): Array<Int>? = synchronized(trackedHolders) {
        val tracked = trackedHolders[holder] ?: return@synchronized null
        tracked.overrideColors?.takeIf { isAllowedTarget(holder, tracked) } ?: tracked.nativeColors
    }

    private fun trackHolder(holder: Any, data: Any, lottieView: View?) {
        val holderRoot = IslandProbeUtils.getHolderRootView(holder)
        synchronized(trackedHolders) {
            val previous = trackedHolders[holder]
            if (previous?.dataRef?.get() === data) {
                trackedHolders[holder] = previous.copy(
                    lottieViewRef = lottieView?.let(::WeakReference),
                    holderRootRef = holderRoot?.let(::WeakReference),
                )
                return
            }
            val previousData = previous?.dataRef?.get()
            val sameIsland = previousData != null && previousData.javaClass.getMethod("getKey").invoke(previousData) ==
                data.javaClass.getMethod("getKey").invoke(data)
            trackedHolders[holder] = TrackedHolder(
                dataRef = WeakReference(data),
                lottieViewRef = lottieView?.let(::WeakReference),
                holderRootRef = holderRoot?.let(::WeakReference),
                bitmapRef = previous?.bitmapRef?.takeIf { sameIsland },
                palette = previous?.palette?.takeIf { sameIsland },
                nativeColors = previous?.nativeColors?.takeIf { sameIsland },
                overrideColors = previous?.overrideColors?.takeIf { sameIsland },
            )
        }
    }

    private fun rememberArtworkPalette(
        holder: Any, palette: IntArray?, bitmap: Bitmap?, nativeColors: Array<Int>?,
    ) {
        synchronized(trackedHolders) {
            trackedHolders[holder]?.let { tracked ->
                tracked.palette = palette?.copyOf()
                tracked.bitmapRef = bitmap?.let(::WeakReference)
                tracked.nativeColors = nativeColors?.copyOf() ?: tracked.nativeColors
            }
        }
    }

    private fun untrackHolder(holder: Any) {
        synchronized(trackedHolders) {
            trackedHolders.remove(holder)
        }
    }

    private fun hasAllowedTrackedHolder(): Boolean {
        val holders = synchronized(trackedHolders) {
            trackedHolders.entries.toList()
        }
        return holders.any { (holder, tracked) -> isAllowedTarget(holder, tracked) }
    }

    private fun isAllowedTarget(holder: Any, tracked: TrackedHolder): Boolean {
        val data = tracked.dataRef.get() ?: return false
        return IslandModificationTargetPolicy.allowsCurrentScope(
            data = data,
            hostRoot = tracked.holderRootRef?.get()
                ?: IslandProbeUtils.getHolderRootView(holder),
            prefs = prefs
        )
    }

    private fun allowedTrackedHolderCount(): Int = synchronized(trackedHolders) {
        trackedHolders.entries.count { (holder, tracked) -> isAllowedTarget(holder, tracked) }
    }

    private fun trackedHolderCount(): Int = synchronized(trackedHolders) {
        trackedHolders.size
    }

    private fun invalidateTrackedLottieViews() {
        val views = synchronized(trackedHolders) {
            trackedHolders.values.mapNotNull { it.lottieViewRef?.get() }
        }
        views.forEach(View::invalidate)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private class ArtworkPaletteHook(
        private val api: IslandMusicWaveNativeApi,
        private val dataField: Field?,
        private val lottieViewField: Field,
        private val cachedBitmapField: Field?
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val requestGeneration = generation
            val holder = chain.thisObject
            val data = runCatching { holder?.let { dataField?.get(it) } }.getOrNull()
            var nativeColors: Array<Int>? = null
            fun proceedAndCapture(): Any? {
                val result = chain.proceed()
                if (holder != null) nativeColors = runCatching { api.nativeColors(holder, result) }.getOrNull()
                return result
            }
            // The legacy setter writes two static fields. Read them under the same lock as
            // the native call so another holder cannot overwrite them between capture steps.
            val result = if (api.paletteMethod?.name == "setLottieColor") {
                synchronized(requireNotNull(api.paletteMethod).declaringClass) { proceedAndCapture() }
            } else proceedAndCapture()
            if (holder == null || data == null) return result
            runCatching {
                // New getLottieColor() runs on Dispatchers.IO. Extract there, then touch Views on main.
                val bitmap = chain.args.getOrNull(0) as? Bitmap
                val artworkPalette = runCatching {
                    if (IslandProbeUtils.extractMediaIslandInfo(data) != null) {
                        bitmap?.let(CoverColorHelper::extractPalette)
                    } else null
                }.onFailure { error ->
                    HookLogger.w(TAG, "当前媒体封面取色失败，将恢复原生颜色: ${error.message}")
                }.getOrNull()
                runOnMain {
                    runCatching {
                        if (generation != requestGeneration || dataField?.get(holder) !== data ||
                            (cachedBitmapField != null && cachedBitmapField.get(holder) !== bitmap)
                        ) {
                            return@runCatching
                        }
                        val target = IslandModificationTargetPolicy.resolve(
                            data = data,
                            hostRoot = IslandProbeUtils.getHolderRootView(holder)
                        )
                        if (!target.isMediaTarget) {
                            untrackHolder(holder)
                            refresh()
                            IslandMusicWaveSpectrumHooker.refresh()
                            return@runCatching
                        }
                        val lottieView = lottieViewField.get(holder) as? View
                        trackHolder(holder, data, lottieView)
                        rememberArtworkPalette(holder, artworkPalette?.second, bitmap, nativeColors)
                        refresh()
                        IslandMusicWaveSpectrumHooker.refresh()
                    }.onFailure { HookLogger.e(TAG, "应用音频律动封面色失败", it) }
                }
            }.onFailure { HookLogger.e(TAG, "读取原生音频律动颜色失败", it) }
            return result
        }
    }

    private class CaptureGradientCallbackHook(
        private val api: IslandMusicWaveNativeApi,
        private val dataField: Field?,
        private val lottieViewField: Field
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val args = chain.args.toTypedArray()
            runCatching {
                val holder = registeringHolder.get() ?: return@runCatching
                val view = lottieViewField.get(holder) ?: return@runCatching
                if (api.drawableField?.get(view) !== chain.thisObject) return@runCatching
                var callback = args.getOrNull(2) ?: return@runCatching
                if (api.gradientMethods.isNotEmpty()) {
                    // Legacy SystemUI registers a singleton callback. Give each holder its own
                    // instance before Lottie retains it, so its native palette can stay independent.
                    callback = callback.javaClass.getDeclaredConstructor()
                        .apply { isAccessible = true }.newInstance()
                    args[2] = callback
                }
                val methods = api.callbackMethods(callback.javaClass)
                check(methods.isNotEmpty()) { "Native music-wave value callback unavailable" }
                methods.forEach { installNativeGradientHook(requireNotNull(module), it, dataField) }
                synchronized(callbackHolders) { callbackHolders[callback] = WeakReference(holder) }
            }.onFailure { HookLogger.e(TAG, "跟踪原生音频律动颜色回调失败", it) }
            return chain.proceed(args)
        }
    }

    private class NativeGradientValueHook(private val dataField: Field?) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val holder = synchronized(callbackHolders) {
                callbackHolders[chain.thisObject]?.get()
            } ?: return chain.proceed()
            val tracked = synchronized(trackedHolders) { trackedHolders[holder] }
                ?: return chain.proceed()
            val current = runCatching { tracked.dataRef.get() === dataField?.get(holder) }.getOrDefault(false)
            if (!current) return chain.proceed()
            return colorsForHolder(holder) ?: DEFAULT_NATIVE_COLORS
        }
    }

    private class RegisterLottieCallbackHook(
        private val dataField: Field?,
        private val lottieViewField: Field,
        private val picInfoField: Field
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val previous = registeringHolder.get()
            chain.thisObject?.let(registeringHolder::set)
            val result = try {
                chain.proceed()
            } finally {
                if (previous == null) registeringHolder.remove() else registeringHolder.set(previous)
            }
            runCatching {
                val holder = chain.thisObject ?: return@runCatching
                if (!isMusicWave(picInfoField.get(holder))) {
                    untrackHolder(holder)
                    refresh()
                    IslandMusicWaveSpectrumHooker.refresh()
                    return@runCatching
                }

                val lottieView = lottieViewField.get(holder) as? View ?: return@runCatching
                val data = dataField?.get(holder)
                if (data == null) {
                    untrackHolder(holder)
                    refresh()
                    IslandMusicWaveSpectrumHooker.refresh()
                    return@runCatching
                }
                val target = IslandModificationTargetPolicy.resolve(
                    data = data,
                    hostRoot = IslandProbeUtils.getHolderRootView(holder)
                )
                if (!target.isMediaTarget) {
                    untrackHolder(holder)
                    refresh()
                    IslandMusicWaveSpectrumHooker.refresh()
                    return@runCatching
                }
                trackHolder(holder, data, lottieView)
                refresh()
                IslandMusicWaveSpectrumHooker.refresh()
                lottieView.invalidate()
            }.onFailure { e ->
                HookLogger.e(TAG, "刷新音频律动动画失败", e)
            }
            return result
        }
    }

    private fun isMusicWave(picInfo: Any?): Boolean {
        val pic = picInfo?.javaClass?.methods
            ?.firstOrNull { it.name == "getPic" && it.parameterTypes.isEmpty() }
            ?.invoke(picInfo) as? String
        return pic == "musicWave" || pic == "musicPause"
    }

    private data class TrackedHolder(
        val dataRef: WeakReference<Any>,
        val lottieViewRef: WeakReference<View>?,
        val holderRootRef: WeakReference<android.view.ViewGroup>?,
        var bitmapRef: WeakReference<Bitmap>? = null,
        var palette: IntArray? = null,
        var nativeColors: Array<Int>? = null,
        var overrideColors: Array<Int>? = null,
    )

    private data class WaveColors(
        val top: Int,
        val bottom: Int
    )

}
