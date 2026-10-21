package com.lidesheng.hyperlyric.root.statusbar

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Uses the notification container's own island monitor from the main SystemUI. */
internal object StatusBarNotificationIconPositionHooker {
    private const val TAG = "StatusBarNotificationIcons"
    private var bridge: Bridge? = null
    private val layouts = WeakHashMap<View, WeakReference<StatusBarNotificationIconPosition>>()
    private val icons = WeakHashMap<View, WeakReference<StatusBarNotificationIconPosition>>()

    fun hook(module: XposedModule, loader: ClassLoader) {
        bridge = null
        runCatching {
            val api = Bridge(loader)
            val capability = "status_bar.lyric.notification_icon_position"
            module.managedHook(api.updateParent, capability, RootLayoutHook())
            module.managedHook(api.updateCutout, capability, RootLayoutHook())
            module.managedHook(api.layout, capability, RightAreaLayoutHook())
            module.managedHook(api.calculate, capability, IconCalculationHook(api))
            // Partial installation must never enable moving a container.
            bridge = api
        }.onFailure {
            HookLogger.w(TAG, "通知图标右侧模式不可用: reason=${it.message}")
        }
    }

    fun create(root: ViewGroup): StatusBarNotificationIconPosition? =
        bridge?.takeIf { it.rootClass.isInstance(root) }
            ?.let { StatusBarNotificationIconPosition(root, it) }

    fun currentIcons(root: ViewGroup): View? =
        bridge?.takeIf { it.rootClass.isInstance(root) }?.inner?.get(root) as? View

    fun track(owner: StatusBarNotificationIconPosition, layout: View, container: View) {
        layouts[layout] = WeakReference(owner)
        icons[container] = WeakReference(owner)
    }

    fun untrack(layout: View?, container: View?) {
        if (layout != null) layouts.remove(layout)
        if (container != null) icons.remove(container)
    }

    private class RootLayoutHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val root = chain.thisObject as? ViewGroup ?: return result
            StatusBarLyricHostRegistry.onNotificationIconLayoutUpdated(root)
            return result
        }
    }

    private class RightAreaLayoutHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val view = chain.thisObject as? View ?: return result
            layouts[view]?.get()?.onSystemIconsLaidOut()
            return result
        }
    }

    private class IconCalculationHook(private val api: Bridge) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val container = chain.thisObject as? View ?: return chain.proceed()
            val owner = icons[container]?.get()?.takeIf { it.isMoved } ?: return chain.proceed()
            // The native LTR island cutoff is measured to the island's LEFT edge. Our
            // viewport already starts after its RIGHT edge. Give the native island
            // algorithm the remaining width, keeping its island-suppression state instead of overflow dots.
            val inject = api.inject.get(container)
            val monitor = api.monitor.get(inject)
            val oldWidth = monitor?.let(api.islandWidth::getInt)
            val oldLayoutWidth = api.actualWidth.getInt(container)
            val oldStart = api.actualStart.getFloat(container)
            val oldEnd = api.actualEnd.getFloat(container)
            try {
                val nativeWidth = maxOf(owner.unconstrainedContentWidth, container.width)
                if (monitor != null) api.islandWidth.setInt(monitor,
                    if (nativeWidth > container.width) container.width - container.paddingEnd else -1)
                api.actualWidth.setInt(container, if (monitor != null) nativeWidth else container.width)
                api.actualStart.setFloat(container, container.paddingStart.toFloat())
                api.actualEnd.setFloat(container, container.paddingEnd.toFloat())
                return chain.proceed()
            } finally {
                if (monitor != null && oldWidth != null) api.islandWidth.setInt(monitor, oldWidth)
                api.actualWidth.setInt(container, oldLayoutWidth)
                api.actualStart.setFloat(container, oldStart)
                api.actualEnd.setFloat(container, oldEnd)
            }
        }
    }

    internal class Bridge(loader: ClassLoader) {
        val rootClass = loader.loadClass("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView")
        private val areaClass = loader.loadClass("com.android.systemui.statusbar.views.MiuiNotificationStatusContainer")
        val iconClass = loader.loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer")
        private val injectClass = loader.loadClass("com.android.systemui.statusbar.phone.NotificationIconContainerInject")
        private val islandControllerClass = loader.loadClass("com.android.systemui.statusbar.StatusBarIslandControllerImpl")
        val inner = field(rootClass, "mNotificationIconAreaInner", View::class.java)
        val updateParent = method(rootClass, "updateNotificationIconAreaInnnerParent")
        val updateCutout = method(rootClass, "updateCutoutLocation")
        val layout = method(areaClass, "onLayout", Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        private val systemIconsClass = loader.loadClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer")
        private val systemIconStates = field(systemIconsClass, "layoutStates", List::class.java)
        private val systemIconsBoundary = systemIconsClass.getDeclaredMethod("getRealIconWidth").apply {
            check(returnType == Int::class.javaPrimitiveType)
            isAccessible = true
        }
        val calculate = method(iconClass, "calculateIconXTranslations")
        val inject = field(iconClass, "mInject", injectClass)
        val monitor = injectClass.getDeclaredField("_islandMonitor").apply {
            check(type.name in setOf(
                "com.android.systemui.statusbar.IslandMonitor\$NotificationContainerIslandMonitor",
                "com.android.systemui.statusbar.IslandMonitor\$RealContainerIslandMonitor",
            )) { "$name: unexpected field type $type" }
            isAccessible = true
        }
        private val monitorClass = monitor.type
        val islandWidth = field(monitorClass, "islandWidth", Int::class.javaPrimitiveType!!)
        private val islandController = field(monitorClass, "islandController", islandControllerClass)
        private val translation = islandControllerClass.getDeclaredMethod("getTranslationX").apply {
            check(returnType == Int::class.javaPrimitiveType)
            isAccessible = true
        }
        private val supportsTranslation = islandControllerClass.getDeclaredMethod(
            "supportIslandTranslation", Int::class.javaPrimitiveType!!).apply {
            check(returnType == Boolean::class.javaPrimitiveType)
            isAccessible = true
        }
        val actualWidth = field(iconClass, "mActualLayoutWidth", Int::class.javaPrimitiveType!!)
        val actualStart = field(iconClass, "mActualPaddingStart", Float::class.javaPrimitiveType!!)
        val actualEnd = field(iconClass, "mActualPaddingEnd", Float::class.javaPrimitiveType!!)

        fun supportsArea(view: View): Boolean = areaClass.isInstance(view)

        fun supportsSystemIcons(view: View): Boolean = systemIconsClass.isInstance(view)

        fun systemIconsStart(view: View): Int =
            if ((systemIconStates.get(view) as List<*>).isEmpty()) {
                // Xiaomi clears layoutStates on every layout but does not reset the
                // leftover-space offset when all slots are absent/blocked.
                view.width
            } else {
                systemIconsBoundary.invoke(view) as Int
            }

        fun islandTranslationX(container: View): Int {
            val monitor = monitor.get(inject.get(container)) ?: return 0
            val controller = islandController.get(monitor) ?: return 0
            return if (supportsTranslation.invoke(controller, 0) == true) {
                translation.invoke(controller) as Int
            } else 0
        }

        private fun field(type: Class<*>, name: String, expected: Class<*>): Field =
            type.getDeclaredField(name).apply {
                check(this.type == expected) { "$name: unexpected field type ${this.type}" }
                isAccessible = true
            }

        private fun method(type: Class<*>, name: String, vararg args: Class<*>): Method =
            type.getDeclaredMethod(name, *args).apply {
                check(returnType == Void.TYPE) { "$name: unexpected return type" }
                isAccessible = true
            }
    }
}
