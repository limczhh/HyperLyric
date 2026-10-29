/*
 * The holder-binding behavior is adapted from HyperIsland's MusicIslandSpectrumHook:
 * https://github.com/1812z/HyperIsland
 * Copyright (c) 2026 1812z
 * Licensed under the MIT License. The full notice is in assets/licenses/HyperIsland-MIT.txt.
 */
package com.lidesheng.hyperlyric.root.island.effects.spectrum

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.SuperIslandContentStylePolicy
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.SystemUiEnhancementGate
import com.lidesheng.hyperlyric.root.island.host.IslandProbeUtils
import com.lidesheng.hyperlyric.root.island.policy.IslandModificationTargetPolicy
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.CoverColorHelper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.min

/** Replaces Xiaomi's decorative music-wave Lottie with a shared, audio-driven spectrum view. */
internal object IslandMusicWaveSpectrumHooker {
    private const val TAG = "IslandMusicWaveSpectrumHooker"
    private const val HOLDER_CLASS = "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val PIC_MUSIC_WAVE = "musicWave"
    private const val PIC_MUSIC_PAUSE = "musicPause"
    private const val CAPABILITY = "island.music_wave.spectrum_bind"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val holderStates = WeakHashMap<Any, HolderState>()
    private val warningStates = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var module: XposedModule? = null

    private val prefs: SharedPreferences?
        get() = (module as? HookEntry)?.prefs

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        module = xposedModule
        if (!hookedClassLoaders.add(classLoader)) return

        try {
            val holderClass = classLoader.loadClass(HOLDER_CLASS)
            val dataField = holderClass.getDeclaredField("data").apply { isAccessible = true }
            val picInfoField = holderClass.getDeclaredField("picInfo").apply { isAccessible = true }
            val containerField = holderClass.getDeclaredField("iconContainer").apply {
                isAccessible = true
            }
            val lottieField = holderClass.getDeclaredField("lottieView").apply {
                isAccessible = true
            }
            val bindMethods = holderClass.declaredMethods.filter {
                it.name == "bind" && it.parameterCount == 2
            }
            if (bindMethods.isEmpty()) {
                hookedClassLoaders.remove(classLoader)
                HookLogger.w(TAG, "实时频谱 Hook 不可用: target=IslandIconViewHolder.bind")
                return
            }

            bindMethods.forEach { method ->
                method.isAccessible = true
                xposedModule.managedHook(
                    executable = method,
                    capability = CAPABILITY,
                    hooker = BindHook(dataField, picInfoField, containerField, lottieField),
                )
            }
            HookLogger.d(TAG, "超级岛实时频谱 Hook 已初始化")
        } catch (_: ClassNotFoundException) {
            hookedClassLoaders.remove(classLoader)
        } catch (error: Throwable) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "超级岛实时频谱 Hook 初始化失败: reason=${error.message}")
        }
    }

    fun refresh() {
        runOnMain {
            val holders = synchronized(holderStates) { holderStates.keys.toList() }
            holders.forEach { holder ->
                runCatching { apply(holder) }
                    .onFailure { HookLogger.w(TAG, "刷新实时频谱失败: reason=${it.message}") }
            }
        }
    }

    fun prepareForHotReload(): ArrayList<Any?> {
        check(Looper.myLooper() == Looper.getMainLooper())
        val holders = synchronized(holderStates) { holderStates.keys.toList() }
        holders.forEach { holder -> restoreNative(holder, holderStates[holder]) }
        synchronized(holderStates) { holderStates.clear() }
        hookedClassLoaders.clear()
        releaseIslandMusicWaveSpectrumSource()
        module = null
        return ArrayList<Any?>(holders.size).apply { addAll(holders) }
    }

    fun adoptHotReloadHolders(holders: List<*>) {
        runOnMain {
            holders.filterNotNull().forEach { holder ->
                runCatching { apply(holder) }
                    .onFailure {
                        HookLogger.w(TAG, "接管实时频谱宿主失败: reason=${it.message}")
                    }
            }
        }
    }

    fun cleanup() {
        runOnMain {
            synchronized(holderStates) {
                holderStates.keys.toList().forEach { holder ->
                    restoreNative(holder, holderStates[holder])
                }
                holderStates.clear()
            }
            hookedClassLoaders.clear()
            warningStates.clear()
            releaseIslandMusicWaveSpectrumSource()
            module = null
        }
    }

    private fun beforeBind(holder: Any) {
        val state = synchronized(holderStates) { holderStates[holder] } ?: return
        if (!state.hiddenLottie) return
        state.lottieRef?.get()?.let { lottie ->
            lottie.visibility = state.nativeVisibility ?: View.VISIBLE
        }
        state.hiddenLottie = false
        state.nativeVisibility = null
    }

    private fun apply(
        holder: Any,
        dataField: Field? = null,
        picInfoField: Field? = null,
        containerField: Field? = null,
        lottieField: Field? = null,
    ) {
        val state = synchronized(holderStates) {
            holderStates.getOrPut(holder) { HolderState() }
        }
        val data = field(holder, dataField, "data")?.get(holder)
        val picInfo = field(holder, picInfoField, "picInfo")?.get(holder)
        val container = field(holder, containerField, "iconContainer")?.get(holder) as? ViewGroup
        val lottie = field(holder, lottieField, "lottieView")?.get(holder) as? View
        val holderRoot = IslandProbeUtils.getHolderRootView(holder)
        val pic = getPic(picInfo)
        val isMusicWavePic = pic == PIC_MUSIC_WAVE || pic == PIC_MUSIC_PAUSE

        if (state.containerRef?.get() !== container || state.lottieRef?.get() !== lottie) {
            restoreNative(holder, state)
        }
        state.containerRef = container?.let(::WeakReference)
        state.lottieRef = lottie?.let(::WeakReference)
        state.nativeTopColorField = field(holder, null, "gradientTopColor")
        state.nativeBottomColorField = field(holder, null, "gradientBottomColor")

        val preferences = prefs
        val mode = preferences?.let { SuperIslandContentStylePolicy.readMusicWaveMode(it) }
        val inactiveReason = when {
            preferences == null -> "preferences_unavailable"
            !SystemUiEnhancementGate.isEnabled() -> "enhancements_disabled"
            mode != RootConstants.ISLAND_MUSIC_WAVE_MODE_REALTIME_SPECTRUM ->
                if (mode == RootConstants.ISLAND_MUSIC_WAVE_MODE_HIDDEN) {
                    "mode_hidden"
                } else {
                    "mode_default"
                }

            !isMusicWavePic -> "native_icon"
            container == null -> "icon_container_unavailable"
            lottie == null -> "native_lottie_unavailable"
            !IslandModificationTargetPolicy.allowsCurrentScope(
                data = data,
                hostRoot = holderRoot,
                prefs = preferences
            ) -> "outside_modification_scope"

            else -> null
        }

        if (inactiveReason != null) {
            if (isMusicWavePic || state.hiddenLottie) {
                logDebugTransition(
                    state,
                    "native:$inactiveReason",
                    "实时频谱未接管: reason=$inactiveReason, pic=$pic"
                )
            }
            restoreNative(holder, state)
            return
        }

        val activePrefs = preferences
            ?: return
        val parent = requireNotNull(container)
        val nativeView = requireNotNull(lottie)
        val activeSpectrum = findSpectrum(parent) ?: createSpectrum(parent, nativeView)
        if (activeSpectrum == null) {
            logDebugTransition(
                state,
                "native:spectrum_view_unavailable",
                "实时频谱未接管: reason=spectrum_view_unavailable, pic=$pic"
            )
            restoreNative(holder, state)
            return
        }
        val colorPair = resolveColors(activePrefs, state)
        activeSpectrum.setColors(colorPair.first, colorPair.second)
        activeSpectrum.visibility = View.VISIBLE
        activeSpectrum.setOnCaptureFailure {
            val liveHolder = state.holderRef?.get()
            if (liveHolder != null) restoreNative(liveHolder, state)
        }
        val isPlaying = pic == PIC_MUSIC_WAVE
        if (!activeSpectrum.setPlaying(isPlaying)) {
            warnOnce("visualizer_unavailable", "实时频谱不可用，保留系统律动图标")
            logDebugTransition(
                state,
                "native:visualizer_unavailable",
                "实时频谱未接管: reason=visualizer_unavailable, pic=$pic"
            )
            restoreNative(holder, state)
            return
        }

        if (!state.hiddenLottie) state.nativeVisibility = nativeView.visibility
        nativeView.visibility = View.INVISIBLE
        state.hiddenLottie = true
        state.holderRef = WeakReference(holder)
        logDebugTransition(
            state,
            "spectrum:$isPlaying",
            "实时频谱已接管: pic=$pic, bars=5, response=balanced"
        )
    }

    private fun restoreNative(holder: Any, state: HolderState? = null) {
        val tracked = state ?: synchronized(holderStates) { holderStates[holder] } ?: return
        val lottie = tracked.lottieRef?.get() ?: runCatching {
            holder.javaClass.getDeclaredField("lottieView").apply { isAccessible = true }
                .get(holder) as? View
        }.getOrNull()
        val container = tracked.containerRef?.get()
        findSpectrum(container)?.let { spectrum ->
            spectrum.setOnCaptureFailure(null)
            spectrum.setPlaying(false)
            container?.removeView(spectrum)
        }
        if (tracked.hiddenLottie) {
            lottie?.visibility = tracked.nativeVisibility ?: View.VISIBLE
        }
        tracked.hiddenLottie = false
        tracked.nativeVisibility = null
    }

    private fun createSpectrum(container: ViewGroup, lottie: View): IslandMusicWaveSpectrumView? {
        val spectrum = runCatching { IslandMusicWaveSpectrumView(container.context) }
            .onFailure {
                HookLogger.w(TAG, "创建实时频谱视图失败: reason=${it.message}")
            }
            .getOrNull() ?: return null
        spectrum.isSaveEnabled = false
        spectrum.contentDescription = null
        val sourceParams = lottie.layoutParams
        val params = sourceParams?.let(::copyLayoutParams) ?: run {
            val side = resolveIconSide(container, lottie)
            if (container is FrameLayout) {
                FrameLayout.LayoutParams(side, side, Gravity.CENTER)
            } else {
                ViewGroup.LayoutParams(side, side)
            }
        }
        return runCatching {
            container.addView(spectrum, params)
            spectrum
        }.onFailure { HookLogger.w(TAG, "添加实时频谱视图失败: reason=${it.message}") }
            .getOrNull()
    }

    private fun copyLayoutParams(source: ViewGroup.LayoutParams): ViewGroup.LayoutParams? =
        runCatching {
            source.javaClass.getConstructor(source.javaClass).newInstance(source)
                as ViewGroup.LayoutParams
        }.getOrNull()

    private fun resolveIconSide(container: ViewGroup, lottie: View): Int {
        val width = lottie.width.takeIf { it > 0 } ?: lottie.layoutParams?.width ?: 0
        val height = lottie.height.takeIf { it > 0 } ?: lottie.layoutParams?.height ?: 0
        if (width > 0 && height > 0) return min(width, height)
        if (container.height > 0) return container.height
        return (30f * container.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun findSpectrum(container: ViewGroup?): IslandMusicWaveSpectrumView? {
        if (container == null) return null
        for (index in 0 until container.childCount) {
            (container.getChildAt(index) as? IslandMusicWaveSpectrumView)?.let { return it }
        }
        return null
    }

    private fun isMusicWave(picInfo: Any?): Boolean =
        getPic(picInfo).let { it == PIC_MUSIC_WAVE || it == PIC_MUSIC_PAUSE }

    private fun getPic(picInfo: Any?): String? = runCatching {
        picInfo?.javaClass?.methods?.firstOrNull {
            it.name == "getPic" && it.parameterTypes.isEmpty()
        }?.invoke(picInfo) as? String
    }.getOrNull()

    private fun resolveColors(
        prefs: SharedPreferences,
        state: HolderState
    ): Pair<Int, Int> {
        val colorStyle = SuperIslandContentStylePolicy.readMusicWaveColorStyle(prefs)
        if (!SuperIslandContentStylePolicy.usesMusicWaveCoverColor(colorStyle)) {
            return readNativeDefaultColors(state)
        }
        val palette = CoverColorHelper.currentArtworkRequest()?.let { request ->
            CoverColorHelper.getCachedColors(useGradient = true, request = request)
        } ?: CoverColorHelper.currentSession()?.let { session ->
            CoverColorHelper.getCachedColors(useGradient = true, session = session)
        }?.second?.let { colors -> colors to colors }
        val colors = palette?.second ?: return ISLAND_MUSIC_WAVE_DEFAULT_TOP_COLOR to
                ISLAND_MUSIC_WAVE_DEFAULT_BOTTOM_COLOR
        val first = colors.getOrNull(0) ?: return ISLAND_MUSIC_WAVE_DEFAULT_TOP_COLOR to
                ISLAND_MUSIC_WAVE_DEFAULT_BOTTOM_COLOR
        val second = if (SuperIslandContentStylePolicy.usesMusicWaveGradient(colorStyle)) {
            colors.getOrNull(1) ?: first
        } else {
            first
        }
        return first to second
    }

    private fun readNativeDefaultColors(state: HolderState): Pair<Int, Int> {
        val top = state.nativeTopColorField?.let { field ->
            runCatching { field.getInt(null) }.getOrDefault(-1)
        }?.takeIf { it != -1 } ?: ISLAND_MUSIC_WAVE_DEFAULT_TOP_COLOR
        val bottom = state.nativeBottomColorField?.let { field ->
            runCatching { field.getInt(null) }.getOrDefault(-1)
        }?.takeIf { it != -1 } ?: ISLAND_MUSIC_WAVE_DEFAULT_BOTTOM_COLOR
        return top to bottom
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun field(holder: Any, cached: Field?, name: String): Field? = cached ?: runCatching {
        holder.javaClass.getDeclaredField(name).apply { isAccessible = true }
    }.getOrNull()

    private fun warnOnce(key: String, message: String) {
        if (warningStates.add(key)) HookLogger.w(TAG, message)
    }

    private fun logDebugTransition(state: HolderState, key: String, message: String) {
        if (!HookLogger.isDebugEnabled || state.lastDebugState == key) return
        state.lastDebugState = key
        HookLogger.d(TAG, message)
    }

    private class BindHook(
        private val dataField: Field,
        private val picInfoField: Field,
        private val containerField: Field,
        private val lottieField: Field,
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val holder = chain.thisObject ?: return chain.proceed()
            beforeBind(holder)
            val result = chain.proceed()
            runCatching { apply(holder, dataField, picInfoField, containerField, lottieField) }
                .onFailure { HookLogger.w(TAG, "绑定实时频谱失败: reason=${it.message}") }
            return result
        }
    }

    private class HolderState {
        var holderRef: WeakReference<Any>? = null
        var containerRef: WeakReference<ViewGroup>? = null
        var lottieRef: WeakReference<View>? = null
        var nativeTopColorField: Field? = null
        var nativeBottomColorField: Field? = null
        var nativeVisibility: Int? = null
        var hiddenLottie: Boolean = false
        var lastDebugState: String? = null
    }



}
