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

/** Reuses the native MiPlay device-button click path, preferring the Super Island source. */
internal object StatusBarLyricMiPlayLauncher {
    private const val TAG = "StatusBarLyricMiPlayLauncher"
    private const val TRANSFER_MANAGER_CLASS =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaTransferManagerImpl"
    private const val CAPABILITY = "status_bar.lyric.miplay_transfer_manager"
    private const val DEVICE_BUTTON_RESOURCE = "media_seamless_image"
    private const val SUPER_ISLAND_ROOT_CLASS = "PlayerIslandConstraintLayout"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val managers = WeakHashMap<ClassLoader, Any>()
    // true means that the button belongs to the Super Island media view.
    private val deviceButtons = WeakHashMap<View, Boolean>()

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        hookDeviceButtonListener(module)

        val managerClass = runCatching {
            classLoader.loadClass(TRANSFER_MANAGER_CLASS)
        }.getOrNull() ?: return

        val constructors = managerClass.declaredConstructors
        if (constructors.isEmpty()) return

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
        val source = findDeviceButton() ?: synchronized(managers) {
            managers.values.firstOrNull()?.let(::findManagerDeviceButton)
        } ?: run {
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

    private fun hookDeviceButtonListener(module: XposedModule) {
        val method = runCatching {
            View::class.java.getDeclaredMethod(
                "setOnClickListener",
                View.OnClickListener::class.java,
            ).apply { isAccessible = true }
        }.getOrNull() ?: return

        runCatching {
            module.managedHook(
                executable = method,
                capability = "$CAPABILITY.device_button_listener",
                hooker = DeviceButtonListenerHook(),
            )
        }.onFailure { error ->
            HookLogger.w(TAG, "通知媒体设备按钮监听 Hook 安装失败", error)
        }
    }

    private fun register(manager: Any, classLoader: ClassLoader) {
        synchronized(managers) {
            managers[classLoader] = manager
        }
    }

    private fun findDeviceButton(): View? {
        synchronized(deviceButtons) {
            val candidates = deviceButtons.keys.toList()
            return chooseDeviceButton(candidates)
        }
    }

    private fun findManagerDeviceButton(manager: Any): View? {
        val views = findField(manager.javaClass, "mViews")?.let { field ->
            runCatching { field.get(manager) as? Iterable<*> }.getOrNull()
        } ?: return null
        return chooseDeviceButton(views.filterIsInstance<View>().toList())
    }

    private fun chooseDeviceButton(candidates: List<View>): View? {
        fun List<View>.firstActive(): View? = firstOrNull {
            it.isAttachedToWindow && it.visibility == View.VISIBLE
        }

        fun List<View>.firstAttached(): View? = firstOrNull { it.isAttachedToWindow }

        val island = candidates.filter(::isSuperIslandButton)
        val notification = candidates.filterNot(::isSuperIslandButton)
        return island.firstActive()
            ?: notification.firstActive()
            ?: island.firstAttached()
            ?: notification.firstAttached()
            ?: island.firstOrNull()
            ?: notification.firstOrNull()
    }

    private fun findField(type: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            val owner = current
            runCatching {
                owner.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun isDeviceButton(view: View): Boolean {
        val id = view.id
        return id != View.NO_ID && runCatching {
            view.resources.getResourceEntryName(id) == DEVICE_BUTTON_RESOURCE
        }.getOrDefault(false)
    }

    private fun isSuperIslandButton(view: View): Boolean =
        generateSequence(view) { it.parent as? View }.any { candidate ->
            val name = candidate.javaClass.name
            name == SUPER_ISLAND_ROOT_CLASS ||
                name.endsWith(".$SUPER_ISLAND_ROOT_CLASS") ||
                name.contains(".notification.mediaisland.")
        }

    private class DeviceButtonListenerHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.thisObject as? View
            val matches = view?.let(::isDeviceButton) == true
            val result = chain.proceed()
            if (matches) {
                synchronized(deviceButtons) {
                    if (chain.args.firstOrNull() == null) {
                        deviceButtons.remove(view)
                    } else {
                        deviceButtons[view] = isSuperIslandButton(view)
                    }
                }
            }
            return result
        }
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
