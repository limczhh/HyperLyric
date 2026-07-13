package com.lidesheng.hyperlyric.root.mediacard.island

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
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
    private const val ORIGINAL_ALPHA_TAG_KEY = 0x7e48594c

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val binderStates = Collections.synchronizedMap(WeakHashMap<Any, BinderState>())
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
                else -> false
            }

            MUSIC_BG_VIEW_CLASS ->
                (method.name == "start" || method.name == "resume") &&
                    method.parameterCount == 0

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
                else -> null
            }

            MUSIC_BG_VIEW_CLASS -> PlaybackStartHook()
            else -> null
        }
    }

    fun releaseAll() {
        binderStates.clear()
        colorExecutor.shutdownNow()
    }

    private enum class Action { ATTACH, BIND, DETACH }

    private class BinderHook(private val action: Action) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val binder = chain.thisObject ?: return chain.proceed()
            if (action == Action.DETACH) cleanupBinder(binder)
            val result = chain.proceed()
            runCatching {
                when (action) {
                    Action.ATTACH -> applyMode(binder, allowCoverColor = false)
                    Action.BIND -> applyMode(binder, allowCoverColor = true)
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

    private class NativeApi private constructor(
        val hookMethods: List<Method>,
        private val holderField: Field,
        private val dummyHolderField: Field,
        private val artworkField: Field,
        private val mediaBgViewField: Field,
        private val pauseMethod: Method,
        private val setGradientColorMethod: Method,
        private val drawableToBitmapMethod: Method
    ) {
        fun getMusicBgViews(binder: Any): List<View> {
            return listOfNotNull(
                holderField.get(binder),
                dummyHolderField.get(binder)
            ).mapNotNull { holder -> mediaBgViewField.get(holder) as? View }
                .distinct()
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

                val attach = binderClass.declaredMethods.single {
                    it.name == "attach" && it.parameterCount == 2
                }.apply { isAccessible = true }
                val bind = binderClass.declaredMethods.single {
                    it.name == "bindMediaData" && it.parameterCount == 1
                }.apply { isAccessible = true }
                val detach = binderClass.declaredMethods.single {
                    it.name == "detach" && it.parameterCount == 0
                }.apply { isAccessible = true }
                val start = musicBgViewClass.getDeclaredMethod("start").apply {
                    isAccessible = true
                }
                val resume = musicBgViewClass.getDeclaredMethod("resume").apply {
                    isAccessible = true
                }

                return NativeApi(
                    hookMethods = listOf(attach, bind, detach, start, resume),
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
}
