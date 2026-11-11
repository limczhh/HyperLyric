package com.lidesheng.hyperlyric.root.statusbar

import android.os.Handler
import android.os.Looper
import android.view.View
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

/** Reuses the notification media card's native MiPlay device-button click path. */
internal object StatusBarLyricMiPlayLauncher {
    private const val TAG = "StatusBarLyricMiPlayLauncher"
    private const val TRANSFER_MANAGER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaTransferManagerImpl"
    private const val CAPABILITY = "status_bar.lyric.miplay_transfer_manager"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val managers = WeakHashMap<ClassLoader, Any>()

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        val managerClass = runCatching {
            classLoader.loadClass(TRANSFER_MANAGER_CLASS)
        }.getOrNull() ?: run {
            HookLogger.w(TAG, "通知媒体设备按钮 Hook 未安装: reason=manager_class_unavailable")
            return
        }

        val constructors = managerClass.declaredConstructors
        if (constructors.isEmpty()) {
            HookLogger.w(TAG, "通知媒体设备按钮 Hook 未安装: reason=constructor_unavailable")
            return
        }

        runCatching {
            constructors.forEach { constructor ->
                constructor.isAccessible = true
                module.managedHook(
                    executable = constructor,
                    capability = "$CAPABILITY.constructor.${constructor.parameterCount}",
                    hooker = ManagerConstructorHook(classLoader),
                )
            }
        }.onFailure { error ->
            HookLogger.w(TAG, "通知媒体设备按钮 Hook 安装失败", error)
        }
    }

    fun open(): Boolean {
        val manager = synchronized(managers) {
            managers.values.firstOrNull()
        } ?: run {
            HookLogger.w(TAG, "无法打开通知媒体妙播界面: transfer_manager_unavailable")
            return false
        }

        val source = findDeviceButton(manager) ?: run {
            HookLogger.w(TAG, "无法打开通知媒体妙播界面: device_button_unavailable")
            return false
        }

        val click = Runnable {
            runCatching {
                if (!source.performClick()) {
                    HookLogger.w(TAG, "通知媒体设备按钮未接受点击")
                }
            }.onFailure { error ->
                HookLogger.w(TAG, "执行通知媒体设备按钮点击失败", error)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            click.run()
        } else {
            mainHandler.post(click)
        }
        return true
    }

    private fun register(manager: Any, classLoader: ClassLoader) {
        synchronized(managers) {
            managers[classLoader] = manager
        }
    }

    private fun findDeviceButton(manager: Any): View? {
        val views = findField(manager.javaClass, "mViews")?.let { field ->
            runCatching { field.get(manager) as? Iterable<*> }.getOrNull()
        } ?: return null
        val candidates = views.filterIsInstance<View>().toList()
        return candidates.firstOrNull { it.isAttachedToWindow && it.visibility == View.VISIBLE }
            ?: candidates.firstOrNull { it.isAttachedToWindow }
            ?: candidates.firstOrNull()
    }

    private fun findField(type: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            runCatching {
                current!!.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()?.let { return it }
            current = current.superclass
        }
        return null
    }

    private class ManagerConstructorHook(
        private val classLoader: ClassLoader,
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            chain.thisObject?.let { register(it, classLoader) }
            return result
        }
    }
}
