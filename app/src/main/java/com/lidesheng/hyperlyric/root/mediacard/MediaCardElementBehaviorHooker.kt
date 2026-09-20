package com.lidesheng.hyperlyric.root.mediacard

import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hooks element behaviors shared by notification-center and Super Island media cards.
 *
 * The hook points intentionally follow XiaomiHelper's CustomElement implementation:
 * suppress the native album shadow at its utility entry point, and complete a disabled
 * flip immediately through the native OnFlipListener.
 */
internal object MediaCardElementBehaviorHooker {
    private const val TAG = "MediaCardElementBehavior"
    private const val NOTIFICATION_UTIL_CLASS =
        "com.android.systemui.statusbar.notification.utils.NotificationUtil"
    private const val ALBUM_ANIMATION_UTIL_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaAlbumAnimationUtils"

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

        var installed = 0
        classLoader.loadClassOrNull(NOTIFICATION_UTIL_CLASS)
            ?.declaredMethods
            ?.filter { it.name == "applyViewShadowForMediaAlbum" }
            ?.forEach { method ->
            installed += xposedModule.hookSafely(
                method,
                "media.card.album_shadow",
                AlbumShadowHook()
            )
            }

        classLoader.loadClassOrNull(ALBUM_ANIMATION_UTIL_CLASS)
            ?.declaredMethods
            ?.filter { it.name == "startFlipAnimation" }
            ?.forEach { method ->
            installed += xposedModule.hookSafely(
                method,
                "media.card.album_flip",
                AlbumFlipHook()
            )
            }

        if (installed == 0) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "媒体卡片元素行为 Hook 未安装: reason=target_method_unavailable")
        } else {
            HookLogger.d(TAG, "媒体卡片元素行为 Hook 已初始化: methods=$installed")
        }
    }

    private class AlbumShadowHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val config = MediaCardRuntimeConfig.current
            return if (config.enabled && config.notification.hideCoverShadow) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    private class AlbumFlipHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val config = MediaCardRuntimeConfig.current
            val listener = chain.args.lastOrNull() ?: return chain.proceed()
            val listenerClass = listener.javaClass.name
            val disabled = when {
                ".mediaisland." in listenerClass ->
                    config.islandExpanded.disableCoverFlip

                ".mediacontrol." in listenerClass ->
                    config.notification.disableCoverFlip

                else ->
                    // New SystemUI releases can use an anonymous listener whose
                    // generated name has neither package segment.  Do not let
                    // that evade an enabled no-flip option.
                    config.notification.disableCoverFlip ||
                            config.islandExpanded.disableCoverFlip
            }
            if (!config.enabled || !disabled) return chain.proceed()
            val onFlip = listener.javaClass.methods.firstOrNull { method ->
                method.name == "onFlip" && method.parameterCount == 0
            } ?: listener.javaClass.declaredMethods.firstOrNull { method ->
                method.name == "onFlip" && method.parameterCount == 0
            }?.apply { isAccessible = true }
            return if (onFlip != null) {
                onFlip.invoke(listener)
                null
            } else {
                chain.proceed()
            }
        }
    }

    private fun XposedModule.hookSafely(
        method: Method,
        capability: String,
        hooker: Hooker,
    ): Int {
        return runCatching {
            method.isAccessible = true
            managedHook(
                executable = method,
                capability = capability,
                hooker = hooker,
            )
            1
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "跳过媒体卡片元素行为 Hook: " +
                        "method=${method.declaringClass.simpleName}.${method.name}, " +
                        "reason=${error.message}"
            )
        }.getOrDefault(0)
    }

    private fun ClassLoader.loadClassOrNull(name: String): Class<*>? {
        return runCatching { loadClass(name) }.getOrNull()
    }
}
