package com.lidesheng.hyperlyric.root.island

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.color.ColorExtractor
import com.lidesheng.hyperlyric.root.HookEntry
import com.lidesheng.hyperlyric.root.mediacard.MediaArtworkSampler
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object IslandMusicWaveColorHooker {
    private const val TAG = "IslandMusicWaveColorHooker"
    private const val ICON_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val NATIVE_COLOR_ALPHA = 230

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val trackedLottieViews = WeakHashMap<View, Boolean>()
    private val colorRequest = AtomicInteger()
    private val colorCache = LruCache<String, WaveColors>(8)

    @Volatile
    private var colorExecutor: ExecutorService = newColorExecutor()

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    private var colorAccessor: ColorAccessor? = null

    @Volatile
    private var desiredColors: WaveColors? = null

    @Volatile
    private var desiredToken: String? = null

    @Volatile
    private var pendingToken: String? = null

    @Volatile
    private var inputToken: String? = null

    @Volatile
    private var nativeColors: WaveColors? = null

    @Volatile
    private var overrideApplied = false

    private val prefs: SharedPreferences?
        get() = (module as? HookEntry)?.prefs

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        module = xposedModule
        if (colorExecutor.isShutdown) colorExecutor = newColorExecutor()
        if (!hookedClassLoaders.add(classLoader)) return

        try {
            val holderClass = classLoader.loadClass(ICON_HOLDER_CLASS)
            colorAccessor = ColorAccessor(
                topField = holderClass.getDeclaredField("gradientTopColor").apply {
                    isAccessible = true
                },
                bottomField = holderClass.getDeclaredField("gradientBottomColor").apply {
                    isAccessible = true
                }
            )

            val setLottieColorMethod = holderClass.declaredMethods.firstOrNull {
                it.name == "setLottieColor" &&
                    it.parameterTypes.contentEquals(arrayOf(Bitmap::class.java))
            }
            if (setLottieColorMethod != null) {
                setLottieColorMethod.isAccessible = true
                xposedModule.deoptimize(setLottieColorMethod)
                xposedModule.hook(setLottieColorMethod).intercept(SetLottieColorHook())
            } else {
                HookLogger.w(TAG, "setLottieColor(Bitmap) not found; native color updates cannot be observed")
            }

            val lottieViewField = holderClass.getDeclaredField("lottieView").apply {
                isAccessible = true
            }
            val picInfoField = holderClass.getDeclaredField("picInfo").apply {
                isAccessible = true
            }
            val registerCallbackMethod = holderClass.declaredMethods.firstOrNull {
                it.name == "registerLottieCallback" && it.parameterTypes.isEmpty()
            }
            if (registerCallbackMethod != null) {
                registerCallbackMethod.isAccessible = true
                xposedModule.deoptimize(registerCallbackMethod)
                xposedModule.hook(registerCallbackMethod).intercept(
                    RegisterLottieCallbackHook(lottieViewField, picInfoField)
                )
            } else {
                HookLogger.w(TAG, "registerLottieCallback() not found; color refresh may be delayed")
            }

            HookLogger.i(TAG, "Music wave cover color hook initialized")
        } catch (e: ClassNotFoundException) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Music wave color is unsupported by this plugin: ${e.message}")
        } catch (e: NoSuchFieldException) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Music wave color fields are unavailable: ${e.message}")
        } catch (e: Throwable) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.e(TAG, "Failed to initialize music wave color hook", e)
        }
    }

    fun refresh() {
        runOnMain {
            val sharedPrefs = prefs
            if (sharedPrefs == null || !isEnabled(sharedPrefs)) {
                restoreNativeColors()
            } else {
                desiredColors?.let { colorAccessor?.write(it) }
                invalidateTrackedLottieViews()
            }
        }
    }

    fun cleanup() {
        colorRequest.incrementAndGet()
        colorExecutor.shutdown()
        runOnMain {
            restoreNativeColors()
            synchronized(trackedLottieViews) {
                trackedLottieViews.clear()
            }
            nativeColors = null
            colorAccessor = null
        }
    }

    private fun scheduleOptimizedColors(
        bitmap: Bitmap,
        sharedPrefs: SharedPreferences
    ) {
        val useGradient = sharedPrefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_GRADIENT,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_GRADIENT
        )
        val nextInputToken =
            "${System.identityHashCode(bitmap)}:${bitmap.generationId}:" +
                "${bitmap.width}x${bitmap.height}:$useGradient"
        if (inputToken == nextInputToken) {
            if (pendingToken != null) return
            val token = desiredToken
            val colors = desiredColors
            if (token != null && colors != null) applyOptimizedColors(colors, token)
            return
        }
        inputToken = nextInputToken

        val sample = MediaArtworkSampler.sample(bitmap) ?: return
        val token = "${MediaArtworkSampler.fingerprint(sample)}:$useGradient"

        if (desiredToken == token) {
            sample.recycle()
            desiredColors?.let { applyOptimizedColors(it, token) }
            return
        }
        if (pendingToken == token) {
            sample.recycle()
            return
        }

        colorCache.get(token)?.let { colors ->
            sample.recycle()
            applyOptimizedColors(colors, token)
            return
        }

        val request = colorRequest.incrementAndGet()
        pendingToken = token
        runCatching {
            colorExecutor.execute {
                if (colorRequest.get() != request || pendingToken != token) {
                    sample.recycle()
                    return@execute
                }
                val colors = try {
                    colorsFromPalette(
                        ColorExtractor.extractThemePalette(
                            sample,
                            if (useGradient) 4 else 1
                        ).onBlackBackground,
                        useGradient
                    )
                } catch (e: Throwable) {
                    HookLogger.e(TAG, "Failed to extract music wave colors", e)
                    null
                } finally {
                    sample.recycle()
                }

                runOnMain {
                    if (colors != null) colorCache.put(token, colors)
                    if (colorRequest.get() != request || pendingToken != token) return@runOnMain
                    pendingToken = null
                    val currentPrefs = prefs
                    if (colors == null || currentPrefs == null || !isEnabled(currentPrefs)) {
                        if (colors == null) inputToken = null
                        return@runOnMain
                    }
                    val currentGradient = currentPrefs.getBoolean(
                        RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_GRADIENT,
                        RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_GRADIENT
                    )
                    if (currentGradient != useGradient) return@runOnMain
                    applyOptimizedColors(colors, token)
                }
            }
        }.onFailure { e ->
            sample.recycle()
            if (colorRequest.get() == request && pendingToken == token) pendingToken = null
            HookLogger.e(TAG, "Failed to schedule music wave color extraction", e)
        }
    }

    private fun applyOptimizedColors(colors: WaveColors, token: String) {
        desiredColors = colors
        desiredToken = token
        if (pendingToken == token) pendingToken = null
        val accessor = colorAccessor ?: return
        if (!overrideApplied && nativeColors == null) {
            nativeColors = accessor.read()
        }
        accessor.write(colors)
        overrideApplied = true
        invalidateTrackedLottieViews()
    }

    private fun restoreNativeColors(rootView: ViewGroup? = null) {
        colorRequest.incrementAndGet()
        desiredColors = null
        desiredToken = null
        pendingToken = null
        inputToken = null
        if (overrideApplied) {
            nativeColors?.let { colorAccessor?.write(it) }
            overrideApplied = false
        }
        invalidateTrackedLottieViews()
        rootView?.let(::invalidateLottieViews)
    }

    private fun isEnabled(sharedPrefs: SharedPreferences): Boolean {
        return sharedPrefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_RIGHT_ICON,
            RootConstants.DEFAULT_HOOK_ISLAND_RIGHT_ICON
        ) && sharedPrefs.getBoolean(
            RootConstants.KEY_HOOK_ISLAND_MUSIC_WAVE_COLOR,
            RootConstants.DEFAULT_HOOK_ISLAND_MUSIC_WAVE_COLOR
        )
    }

    private fun withNativeAlpha(color: Int): Int {
        return (color and 0x00FFFFFF) or (NATIVE_COLOR_ALPHA shl 24)
    }

    private fun colorsFromPalette(colors: List<Int>, useGradient: Boolean): WaveColors? {
        val primary = colors.firstOrNull() ?: return null
        val secondary = if (useGradient) colors.getOrNull(1) ?: primary else primary
        return WaveColors(
            top = withNativeAlpha(primary),
            bottom = withNativeAlpha(secondary)
        )
    }

    private fun newColorExecutor(): ExecutorService {
        return Executors.newSingleThreadExecutor { task ->
            Thread(task, "HyperLyric-MusicWaveColor").apply { isDaemon = true }
        }
    }

    private fun invalidateTrackedLottieViews() {
        val views = synchronized(trackedLottieViews) {
            trackedLottieViews.keys.toList()
        }
        views.forEach(View::invalidate)
    }

    private fun invalidateLottieViews(view: View) {
        if (view.javaClass.name == "com.airbnb.lottie.LottieAnimationView") {
            view.invalidate()
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                invalidateLottieViews(view.getChildAt(index))
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private class SetLottieColorHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val bitmap = chain.args.firstOrNull() as? Bitmap ?: return@runCatching
                nativeColors = colorAccessor?.read()

                val sharedPrefs = prefs ?: return@runCatching
                if (!isEnabled(sharedPrefs)) {
                    colorRequest.incrementAndGet()
                    desiredColors = null
                    desiredToken = null
                    pendingToken = null
                    inputToken = null
                    overrideApplied = false
                    invalidateTrackedLottieViews()
                    return@runCatching
                }

                runOnMain {
                    runCatching { scheduleOptimizedColors(bitmap, sharedPrefs) }.onFailure { e ->
                        HookLogger.e(TAG, "Failed to apply music wave colors", e)
                    }
                }
            }.onFailure { e ->
                HookLogger.e(TAG, "Failed to observe native music wave colors", e)
            }
            return result
        }
    }

    private class RegisterLottieCallbackHook(
        private val lottieViewField: Field,
        private val picInfoField: Field
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val holder = chain.thisObject ?: return@runCatching
                if (!isMusicWave(picInfoField.get(holder))) return@runCatching

                val lottieView = lottieViewField.get(holder) as? View ?: return@runCatching
                synchronized(trackedLottieViews) {
                    trackedLottieViews[lottieView] = true
                }
                val sharedPrefs = prefs
                if (sharedPrefs != null && isEnabled(sharedPrefs)) {
                    desiredColors?.let { colorAccessor?.write(it) }
                }
                lottieView.invalidate()
            }.onFailure { e ->
                HookLogger.e(TAG, "Failed to refresh music wave Lottie callback", e)
            }
            return result
        }

        private fun isMusicWave(picInfo: Any?): Boolean {
            val pic = picInfo?.javaClass?.methods
                ?.firstOrNull { it.name == "getPic" && it.parameterTypes.isEmpty() }
                ?.invoke(picInfo) as? String
            return pic == "musicWave" || pic == "musicPause"
        }
    }

    private data class WaveColors(
        val top: Int,
        val bottom: Int
    )

    private data class ColorAccessor(
        val topField: Field,
        val bottomField: Field
    ) {
        fun read(): WaveColors = WaveColors(
            top = topField.getInt(null),
            bottom = bottomField.getInt(null)
        )

        fun write(colors: WaveColors) {
            topField.setInt(null, colors.top)
            bottomField.setInt(null, colors.bottom)
        }
    }
}
