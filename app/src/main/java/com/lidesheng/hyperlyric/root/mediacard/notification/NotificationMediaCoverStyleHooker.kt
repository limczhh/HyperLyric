package com.lidesheng.hyperlyric.root.mediacard.notification

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.lidesheng.hyperlyric.common.RootConstants
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
import kotlin.math.roundToInt

object NotificationMediaCoverStyleHooker {
    private const val TAG = "NotificationMediaCoverStyle"
    private const val VIEW_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"
    private const val LAYOUT_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaNotificationControllerImpl"
    private const val HOLDER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder"
    private const val MEDIA_DATA_CLASS =
        "com.android.systemui.media.controls.shared.model.MediaData"
    private const val CONSTRAINT_START = 6
    private const val CONSTRAINT_END = 7
    private const val CONSTRAINT_TOP = 3

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val activeControllers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val layoutControllers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val viewStates = Collections.synchronizedMap(WeakHashMap<View, CoverViewState>())
    private val nativeApis = Collections.synchronizedMap(WeakHashMap<ClassLoader, NativeApi>())
    private val restoringNativeLayout = ThreadLocal<Boolean>()
    private val circleOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    @Volatile
    private var module: XposedModule? = null

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
            HookLogger.w(TAG, "Native notification media cover API unavailable; hook skipped")
            return
        }
        val handles = mutableListOf<HookHandle>()
        api.hookMethods.forEach { method ->
            runCatching {
                xposedModule.deoptimize(method)
                val hooker = hookerFor(method)
                    ?: error("No hooker for ${method.declaringClass.name}.${method.name}")
                handles += xposedModule.hook(method).intercept(hooker)
            }.onFailure { error ->
                HookLogger.e(TAG, "Failed to hook ${method.declaringClass.simpleName}.${method.name}", error)
            }
        }

        if (handles.size != api.hookMethods.size) {
            handles.forEach(HookHandle::unhook)
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Notification media cover hook was not installed completely")
        } else {
            HookLogger.i(TAG, "Notification media cover hook initialized: methods=${handles.size}")
        }
    }

    fun isTargetMethod(method: Method): Boolean {
        return when (method.declaringClass.name) {
            VIEW_CONTROLLER_CLASS -> when (method.name) {
                "attach", "bindMediaData", "setSeamless" -> method.parameterCount == 1
                "detach" -> method.parameterCount == 0
                else -> false
            }
            LAYOUT_CONTROLLER_CLASS ->
                method.name == "loadLayout\$1" && method.parameterCount == 0
            else -> false
        }
    }

    fun hookerFor(executable: Executable): Hooker? {
        val method = executable as? Method ?: return null
        if (!isTargetMethod(method)) return null
        return when (method.declaringClass.name) {
            VIEW_CONTROLLER_CLASS -> ControllerHook(method.name)
            LAYOUT_CONTROLLER_CLASS -> LayoutLoadHook()
            else -> null
        }
    }

    fun refresh() {
        val refresh = Runnable {
            val layouts = synchronized(layoutControllers) { layoutControllers.toList() }
            layouts.forEach { controller ->
                runCatching {
                    resolveApi(controller.javaClass.classLoader)?.reloadAndApplyLayout(controller)
                }.onFailure { HookLogger.e(TAG, "Failed to refresh media cover layout", it) }
            }
            val controllers = synchronized(activeControllers) { activeControllers.toList() }
            controllers.forEach { controller ->
                runCatching { applyStyle(controller, null) }
                    .onFailure { HookLogger.e(TAG, "Failed to refresh media cover style", it) }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) refresh.run()
        else Handler(Looper.getMainLooper()).post(refresh)
    }

    fun releaseAll() {
        val layouts = synchronized(layoutControllers) { layoutControllers.toList() }
        val controllers = synchronized(activeControllers) { activeControllers.toList() }
        val cleanup = Runnable {
            layouts.forEach { controller ->
                try {
                    restoringNativeLayout.set(true)
                    resolveApi(controller.javaClass.classLoader)?.reloadAndApplyLayout(controller)
                } catch (error: Throwable) {
                    HookLogger.e(TAG, "Failed to restore native media cover layout", error)
                } finally {
                    restoringNativeLayout.remove()
                }
            }
            controllers.forEach(::restoreStyle)
            NotificationMediaCoverRotationController.cleanup()
            layoutControllers.clear()
            activeControllers.clear()
            viewStates.clear()
            nativeApis.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run()
        else Handler(Looper.getMainLooper()).post(cleanup)
    }

    private class ControllerHook(private val methodName: String) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val controller = chain.thisObject ?: return chain.proceed()
            if (methodName == "setSeamless" && hideDeviceSwitch()) return null
            if (methodName == "detach") {
                activeControllers.remove(controller)
                restoreStyle(controller)
            }
            val result = chain.proceed()
            if (methodName == "attach" || methodName == "bindMediaData") {
                runCatching {
                    activeControllers.add(controller)
                    val mediaData = if (methodName == "bindMediaData") {
                        chain.args.firstOrNull()
                    } else {
                        null
                    }
                    applyStyle(controller, mediaData)
                }.onFailure { HookLogger.e(TAG, "Failed to apply media cover style", it) }
            }
            return result
        }
    }

    private class LayoutLoadHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val controller = chain.thisObject ?: return result
            layoutControllers.add(controller)
            if (restoringNativeLayout.get() != true) {
                runCatching {
                    resolveApi(controller.javaClass.classLoader)?.applyLoadedLayout(
                        controller,
                        currentStyle()
                    )
                }.onFailure { HookLogger.e(TAG, "Failed to apply media cover constraints", it) }
            }
            return result
        }
    }

    private fun applyStyle(controller: Any, mediaData: Any?) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        val albumView = api.getAlbumView(holder)
        val albumImage = api.getAlbumImage(holder)
        val style = currentStyle()
        if (style == RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT) {
            restoreStyle(controller)
            return
        }
        val state = viewStates.getOrPut(albumView) {
            CoverViewState(
                albumView = albumView,
                albumImage = albumImage,
                albumOutlineProvider = albumView.outlineProvider,
                albumClipToOutline = albumView.clipToOutline,
                imageOutlineProvider = albumImage.outlineProvider,
                imageClipToOutline = albumImage.clipToOutline
            )
        }
        val isPlaying = api.isPlaying(mediaData ?: api.getMediaData(controller))

        when (style) {
            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_CIRCLE -> {
                NotificationMediaCoverRotationController.detach(albumImage)
                state.applyCircle()
            }
            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_ROTATING_CIRCLE -> {
                state.applyCircle()
                NotificationMediaCoverRotationController.attach(albumImage, isPlaying)
            }
            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN -> {
                NotificationMediaCoverRotationController.detach(albumImage)
                state.restoreOutlines()
                albumView.visibility = View.GONE
            }
            else -> restoreStyle(controller)
        }
    }

    private fun restoreStyle(controller: Any) {
        val api = resolveApi(controller.javaClass.classLoader) ?: return
        val holder = api.getHolder(controller) ?: return
        val albumView = api.getAlbumView(holder)
        val state = viewStates.remove(albumView) ?: return
        NotificationMediaCoverRotationController.detach(state.albumImage)
        state.restoreOutlines()
        state.albumView.visibility = View.VISIBLE
    }

    private fun currentStyle(): Int {
        return prefs?.getInt(
            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
            RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE
        )?.coerceIn(
            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT,
            RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN
        ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE
    }

    private fun hideCoverSource(): Boolean {
        return prefs?.getBoolean(
            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
            RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE
        ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE
    }

    private fun hideDeviceSwitch(): Boolean {
        return prefs?.getBoolean(
            RootConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
            RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH
        ) ?: RootConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH
    }

    private fun resolveApi(classLoader: ClassLoader?): NativeApi? {
        classLoader ?: return null
        nativeApis[classLoader]?.let { return it }
        return runCatching { NativeApi.create(classLoader) }
            .onSuccess { nativeApis[classLoader] = it }
            .onFailure { HookLogger.w(TAG, "Native media cover API unavailable: ${it.message}") }
            .getOrNull()
    }

    private data class CoverViewState(
        val albumView: View,
        val albumImage: ImageView,
        val albumOutlineProvider: ViewOutlineProvider?,
        val albumClipToOutline: Boolean,
        val imageOutlineProvider: ViewOutlineProvider?,
        val imageClipToOutline: Boolean
    ) {
        fun applyCircle() {
            albumView.visibility = View.VISIBLE
            albumView.outlineProvider = circleOutlineProvider
            albumView.clipToOutline = false
            albumImage.outlineProvider = circleOutlineProvider
            albumImage.clipToOutline = true
            albumView.invalidateOutline()
            albumImage.invalidateOutline()
        }

        fun restoreOutlines() {
            albumView.outlineProvider = albumOutlineProvider
            albumView.clipToOutline = albumClipToOutline
            albumImage.outlineProvider = imageOutlineProvider
            albumImage.clipToOutline = imageClipToOutline
            albumView.invalidateOutline()
            albumImage.invalidateOutline()
        }
    }

    private class NativeApi private constructor(
        val hookMethods: List<Method>,
        private val holderField: Field,
        private val controllerMediaDataField: Field,
        private val albumViewField: Field,
        private val albumImageField: Field,
        private val mediaDataIsPlayingField: Field,
        private val layoutContextField: Field,
        private val normalLayoutField: Field,
        private val normalAlbumLayoutField: Field,
        private val loadLayoutMethod: Method,
        private val updateLayoutMethod: Method,
        private val setVisibilityMethod: Method,
        private val setGoneMarginMethod: Method
    ) {
        fun getHolder(controller: Any): Any? = holderField.get(controller)

        fun getMediaData(controller: Any): Any? = controllerMediaDataField.get(controller)

        fun getAlbumView(holder: Any): View = albumViewField.get(holder) as View

        fun getAlbumImage(holder: Any): ImageView = albumImageField.get(holder) as ImageView

        fun isPlaying(mediaData: Any?): Boolean {
            return mediaData?.let { mediaDataIsPlayingField.get(it) == true } ?: false
        }

        fun applyLoadedLayout(controller: Any, style: Int) {
            val hideSource = hideCoverSource()
            val hideDevice = hideDeviceSwitch()
            if (
                style != RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN &&
                !hideSource &&
                !hideDevice
            ) return
            val normalLayout = normalLayoutField.get(controller)
            val context = layoutContextField.get(controller) as Context
            val ids = LayoutResourceIds.from(context)
            if (style == RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN) {
                setGoneMarginMethod.invoke(
                    normalLayout,
                    ids.headerTitle,
                    CONSTRAINT_START,
                    context.dp(26f)
                )
                setGoneMarginMethod.invoke(
                    normalLayout,
                    ids.headerArtist,
                    CONSTRAINT_START,
                    context.dp(26f)
                )
                setGoneMarginMethod.invoke(
                    normalLayout,
                    ids.actions,
                    CONSTRAINT_TOP,
                    context.dp(67.5f)
                )
                setGoneMarginMethod.invoke(
                    normalLayout,
                    ids.action0,
                    CONSTRAINT_TOP,
                    context.dp(78.5f)
                )
                setVisibilityMethod.invoke(normalLayout, ids.albumArt, View.GONE)
            }
            if (hideSource) {
                val normalAlbumLayout = normalAlbumLayoutField.get(controller)
                setVisibilityMethod.invoke(normalAlbumLayout, ids.coverSource, View.GONE)
            }
            if (hideDevice) {
                setVisibilityMethod.invoke(normalLayout, ids.mediaSeamless, View.GONE)
                setGoneMarginMethod.invoke(
                    normalLayout,
                    ids.headerTitle,
                    CONSTRAINT_END,
                    context.dp(26f)
                )
                setGoneMarginMethod.invoke(
                    normalLayout,
                    ids.headerArtist,
                    CONSTRAINT_END,
                    context.dp(26f)
                )
            }
        }

        fun reloadAndApplyLayout(controller: Any) {
            loadLayoutMethod.invoke(controller)
            updateLayoutMethod.invoke(controller)
        }

        private fun Context.dp(value: Float): Int {
            return (value * resources.displayMetrics.density).roundToInt()
        }

        private data class LayoutResourceIds(
            val albumArt: Int,
            val headerTitle: Int,
            val headerArtist: Int,
            val actions: Int,
            val action0: Int,
            val coverSource: Int,
            val mediaSeamless: Int
        ) {
            companion object {
                fun from(context: Context): LayoutResourceIds {
                    return LayoutResourceIds(
                        albumArt = context.requireId("album_art"),
                        headerTitle = context.requireId("header_title"),
                        headerArtist = context.requireId("header_artist"),
                        actions = context.requireId("actions"),
                        action0 = context.requireId("action0"),
                        coverSource = context.requireId("icon"),
                        mediaSeamless = context.requireId("media_seamless")
                    )
                }

                @Suppress("DiscouragedApi")
                private fun Context.requireId(name: String): Int {
                    val id = resources.getIdentifier(name, "id", packageName)
                    require(id != 0) { "Missing SystemUI id resource: $name" }
                    return id
                }
            }
        }

        companion object {
            fun create(classLoader: ClassLoader): NativeApi {
                val viewControllerClass = classLoader.loadClass(VIEW_CONTROLLER_CLASS)
                val layoutControllerClass = classLoader.loadClass(LAYOUT_CONTROLLER_CLASS)
                val holderClass = classLoader.loadClass(HOLDER_CLASS)
                val mediaDataClass = classLoader.loadClass(MEDIA_DATA_CLASS)
                val constraintSetClass = classLoader.loadClass(
                    "androidx.constraintlayout.widget.ConstraintSet"
                )

                val attach = viewControllerClass.getDeclaredMethod(
                    "attach",
                    holderClass
                ).apply { isAccessible = true }
                val bind = viewControllerClass.getDeclaredMethod(
                    "bindMediaData",
                    mediaDataClass
                ).apply { isAccessible = true }
                val detach = viewControllerClass.getDeclaredMethod("detach").apply {
                    isAccessible = true
                }
                val setSeamless = viewControllerClass.getDeclaredMethod(
                    "setSeamless",
                    mediaDataClass
                ).apply { isAccessible = true }
                val loadLayout = layoutControllerClass.getDeclaredMethod("loadLayout\$1").apply {
                    isAccessible = true
                }

                return NativeApi(
                    hookMethods = listOf(attach, bind, detach, setSeamless, loadLayout),
                    holderField = viewControllerClass.getDeclaredField("holder").apply {
                        isAccessible = true
                    },
                    controllerMediaDataField = viewControllerClass.getDeclaredField("mediaData").apply {
                        isAccessible = true
                    },
                    albumViewField = holderClass.getDeclaredField("albumView").apply {
                        isAccessible = true
                    },
                    albumImageField = holderClass.getDeclaredField("albumImageView").apply {
                        isAccessible = true
                    },
                    mediaDataIsPlayingField = mediaDataClass.getDeclaredField("isPlaying").apply {
                        isAccessible = true
                    },
                    layoutContextField = layoutControllerClass.getDeclaredField("context").apply {
                        isAccessible = true
                    },
                    normalLayoutField = layoutControllerClass.getDeclaredField("normalLayout").apply {
                        isAccessible = true
                    },
                    normalAlbumLayoutField = layoutControllerClass.getDeclaredField(
                        "normalAlbumLayout"
                    ).apply { isAccessible = true },
                    loadLayoutMethod = loadLayout,
                    updateLayoutMethod = layoutControllerClass.getDeclaredMethod("updateLayout\$6").apply {
                        isAccessible = true
                    },
                    setVisibilityMethod = constraintSetClass.getDeclaredMethod(
                        "setVisibility",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true },
                    setGoneMarginMethod = constraintSetClass.getDeclaredMethod(
                        "setGoneMargin",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    ).apply { isAccessible = true }
                )
            }
        }
    }
}
