package com.lidesheng.hyperlyric.root.island

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
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal object IslandAlbumCoverStyleHooker {
    private const val TAG = "IslandAlbumCoverStyleHooker"
    private const val ICON_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val MEDIA_ALBUM_ICON = "miui_media_album_icon"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val trackedHolders = WeakHashMap<Any, TrackedHolder>()
    private val circleOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    @Volatile
    private var module: XposedModule? = null

    private val prefs: SharedPreferences?
        get() = (module as? HookEntry)?.prefs

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        module = xposedModule
        if (!hookedClassLoaders.add(classLoader)) return

        try {
            val holderClass = classLoader.loadClass(ICON_HOLDER_CLASS)
            val fixMethod = holderClass.declaredMethods.firstOrNull {
                it.name == "setFixIcon" && it.parameterTypes.size == 1
            } ?: run {
                hookedClassLoaders.remove(classLoader)
                HookLogger.w(TAG, "setFixIcon(DynamicIslandData) not found; album cover style is unsupported")
                return
            }
            fixMethod.isAccessible = true
            val accessor = CoverAccessor(
                setFixIconMethod = fixMethod,
                setAppIconMethod = holderClass.declaredMethods.firstOrNull {
                    it.name == "setAppIcon" && it.parameterTypes.contentEquals(fixMethod.parameterTypes)
                }?.apply { isAccessible = true },
                picInfoField = holderClass.getDeclaredField("picInfo").apply { isAccessible = true },
                fixIconField = holderClass.getDeclaredField("fixIcon").apply { isAccessible = true },
                appIconField = holderClass.getDeclaredField("appIcon").apply { isAccessible = true },
                iconContainerField = holderClass.getDeclaredField("iconContainer").apply { isAccessible = true }
            )

            xposedModule.deoptimize(fixMethod)
            xposedModule.hook(fixMethod).intercept(SetFixIconHook(accessor))
            HookLogger.i(TAG, "Album cover style hook initialized")
        } catch (e: ClassNotFoundException) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Album cover style is unsupported by this plugin: ${e.message}")
        } catch (e: NoSuchFieldException) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "Album cover fields are unavailable: ${e.message}")
        } catch (e: Throwable) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.e(TAG, "Failed to initialize album cover style hook", e)
        }
    }

    fun refresh() {
        runOnMain {
            val holders = synchronized(trackedHolders) {
                trackedHolders.mapNotNull { (holder, tracked) ->
                    tracked.dataRef.get()?.let { Triple(holder, it, tracked.accessor) }
                }
            }
            holders.forEach { (holder, data, accessor) ->
                runCatching { accessor.setFixIconMethod.invoke(holder, data) }
                    .onFailure { HookLogger.e(TAG, "Failed to refresh album cover style", it) }
            }
        }
    }

    fun onPlaybackStateChanged(isPlaying: Boolean) {
        IslandAlbumCoverRotationController.setPlaybackActive(isPlaying)
    }

    fun cleanup() {
        IslandAlbumCoverRotationController.cleanup()
        synchronized(trackedHolders) {
            trackedHolders.clear()
        }
    }

    private fun applyStyle(accessor: CoverAccessor, holder: Any, dynamicIslandData: Any) {
        if (!isMediaAlbum(accessor, holder)) return
        synchronized(trackedHolders) {
            trackedHolders[holder] = TrackedHolder(WeakReference(dynamicIslandData), accessor)
        }

        val fixIcon = accessor.fixIconField.get(holder) as? ImageView ?: return
        val style = currentStyle()
        if (style != RootConstants.ISLAND_ALBUM_COVER_STYLE_ROTATING_CIRCLE) {
            IslandAlbumCoverRotationController.detach(fixIcon)
        }

        when (style) {
            RootConstants.ISLAND_ALBUM_COVER_STYLE_CIRCLE -> {
                applyCircleOutline(fixIcon)
            }

            RootConstants.ISLAND_ALBUM_COVER_STYLE_APP_ICON -> {
                showAppIcon(accessor, holder, dynamicIslandData)
            }

            RootConstants.ISLAND_ALBUM_COVER_STYLE_ROTATING_CIRCLE -> {
                applyCircleOutline(fixIcon)
                IslandAlbumCoverRotationController.attach(fixIcon)
            }
        }
    }

    private fun applyCircleOutline(fixIcon: ImageView) {
        fixIcon.outlineProvider = circleOutlineProvider
        fixIcon.clipToOutline = true
        fixIcon.invalidateOutline()
    }

    private fun showAppIcon(accessor: CoverAccessor, holder: Any, dynamicIslandData: Any) {
        val fixIcon = accessor.fixIconField.get(holder) as? ImageView
        val method = accessor.setAppIconMethod
        if (method == null) {
            HookLogger.w(TAG, "setAppIcon(DynamicIslandData) not found; keeping the native album cover")
            return
        }

        method.invoke(holder, dynamicIslandData)
        val appIcon = accessor.appIconField.get(holder) as? ImageView
        val iconContainer = accessor.iconContainerField.get(holder) as? View
        if (appIcon?.drawable == null ||
            appIcon.visibility != View.VISIBLE ||
            iconContainer?.visibility != View.VISIBLE
        ) {
            appIcon?.visibility = View.GONE
            fixIcon?.visibility = View.VISIBLE
            iconContainer?.visibility = View.VISIBLE
            HookLogger.w(TAG, "Native app icon was unavailable; keeping the album cover")
        }
    }

    private fun isMediaAlbum(accessor: CoverAccessor, holder: Any): Boolean {
        val picInfo = accessor.picInfoField.get(holder) ?: return false
        val pic = picInfo.javaClass.methods.firstOrNull {
            it.name == "getPic" && it.parameterTypes.isEmpty()
        }?.invoke(picInfo) as? String
        return pic == MEDIA_ALBUM_ICON
    }

    private fun currentStyle(): Int {
        val sharedPrefs = prefs ?: return RootConstants.DEFAULT_HOOK_ISLAND_ALBUM_COVER_STYLE
        if (!sharedPrefs.getBoolean(
                RootConstants.KEY_HOOK_ISLAND_LEFT_ALBUM,
                RootConstants.DEFAULT_HOOK_ISLAND_LEFT_ALBUM
            )
        ) {
            return RootConstants.ISLAND_ALBUM_COVER_STYLE_DEFAULT
        }
        return sharedPrefs.getInt(
            RootConstants.KEY_HOOK_ISLAND_ALBUM_COVER_STYLE,
            RootConstants.DEFAULT_HOOK_ISLAND_ALBUM_COVER_STYLE
        ).coerceIn(
            RootConstants.ISLAND_ALBUM_COVER_STYLE_DEFAULT,
            RootConstants.ISLAND_ALBUM_COVER_STYLE_ROTATING_CIRCLE
        )
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private class SetFixIconHook(
        private val accessor: CoverAccessor
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                val holder = chain.thisObject ?: return@runCatching
                val data = chain.args.firstOrNull() ?: return@runCatching
                applyStyle(accessor, holder, data)
            }.onFailure { HookLogger.e(TAG, "Failed to apply album cover style", it) }
            return result
        }
    }

    private data class TrackedHolder(
        val dataRef: WeakReference<Any>,
        val accessor: CoverAccessor
    )

    private data class CoverAccessor(
        val setFixIconMethod: Method,
        val setAppIconMethod: Method?,
        val picInfoField: Field,
        val fixIconField: Field,
        val appIconField: Field,
        val iconContainerField: Field
    )
}
