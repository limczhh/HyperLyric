package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.root.utils.xLog
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule

object UnlockIslandWhitelist {
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"

    internal lateinit var module: XposedModule
    private val hookedClassLoaders = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())

    fun hook(xposedModule: XposedModule, defaultClassLoader: ClassLoader) {
        module = xposedModule
        runCatching {
            doHookInClassLoader(defaultClassLoader)
        }
    }

    fun doHookInClassLoader(cl: ClassLoader?) {
        if (cl == null || !hookedClassLoaders.add(cl)) return

        runCatching {
            val targetClass = cl.loadClass(TARGET_CLASS)
            for (method in targetClass.declaredMethods) {
                if (method.name == "mediaIslandSupportMiniWindow") {
                    module.deoptimize(method)
                    module.hook(method).intercept(ReturnTrueHooker())
                    xLog("[Whitelist] 成功拦截下发方法 mediaIslandSupportMiniWindow")
                }
            }
        }
    }

    class ReturnTrueHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val prefs = (module as HookEntry).prefs
            val enabled = prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST)
            if (!enabled) {
                return chain.proceed()
            }
            // 直接放行，覆盖系统及云控白名单校验
            return true
        }
    }
}
