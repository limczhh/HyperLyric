package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.root.utils.xLog
import com.lidesheng.hyperlyric.root.utils.xLogError
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule

object UnlockIslandWhitelist {
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    private const val TARGET_METHOD = "mediaIslandSupportMiniWindow"

    internal lateinit var module: XposedModule
    private val hookedClassLoaders = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())

    fun hook(xposedModule: XposedModule, defaultClassLoader: ClassLoader) {
        module = xposedModule
        doHookInClassLoader(defaultClassLoader)
    }

    fun doHookInClassLoader(cl: ClassLoader?) {
        if (cl == null || !hookedClassLoaders.add(cl)) return

        runCatching {
            val targetClass = cl.loadClass(TARGET_CLASS)
            // 使用 find 查找目标方法进行特征检测
            val method = targetClass.declaredMethods.find { it.name == TARGET_METHOD }
            
            if (method != null) {
                module.deoptimize(method)
                module.hook(method).intercept(ReturnTrueHooker())
                xLog("ModuleInit : 成功注入超级岛下拉小窗白名单 ($TARGET_METHOD)")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                xLogError("ModuleInit : 超级岛下拉小窗白名单注入失败", e)
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
