package com.lidesheng.hyperlyric.root

import android.content.Context
import android.os.Bundle
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule

object UnlockFocusWhitelist {
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    private const val AUTH_CALLBACK_CLASS =
        $$"miui.systemui.notification.auth.AuthManager$AuthServiceCallback$onAuthResult$1"
    private const val PLUGIN_INSTANCE_CLASS = "com.android.systemui.shared.plugins.PluginInstance"

    internal lateinit var module: XposedModule
    private val hookedClassLoaders = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())

    fun hook(xposedModule: XposedModule, defaultClassLoader: ClassLoader) {
        module = xposedModule

        try {
            val pluginInstanceClass = defaultClassLoader.loadClass(PLUGIN_INSTANCE_CLASS)
            for (method in pluginInstanceClass.declaredMethods) {
                if (method.name == "loadPlugin") {
                    module.deoptimize(method)
                    module.hook(method).intercept(PluginLoadHooker())
                }
            }
        } catch (_: Exception) {
        }

        runCatching {
            doHookInClassLoader(defaultClassLoader)
        }
    }

    private fun doHookInClassLoader(cl: ClassLoader?) {
        if (cl == null || hookedClassLoaders.contains(cl)) return

        runCatching {
            val targetClass = cl.loadClass(TARGET_CLASS)
            hookedClassLoaders.add(cl)
            for (method in targetClass.declaredMethods) {
                if (method.name == "canShowFocus" || method.name == "canCustomFocus") {
                    module.deoptimize(method)
                    module.hook(method).intercept(ReturnTrueHooker())
                }
            }
        }

        runCatching {
            val authClass = cl.loadClass(AUTH_CALLBACK_CLASS)
            for (method in authClass.declaredMethods) {
                if (method.name == "invokeSuspend") {
                    module.deoptimize(method)
                    module.hook(method).intercept(AuthResultHooker())
                }
            }
        }
    }

    class PluginLoadHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            try {
                val thisObj = chain.thisObject ?: return result
                thisObj.javaClass.declaredFields.forEach { f ->
                    if (f.name == "mPluginContext" || f.name == "mContext") {
                        f.isAccessible = true
                        (f.get(thisObj) as? Context)?.let { context ->
                            doHookInClassLoader(context.classLoader)
                            UnlockIslandWhitelist.doHookInClassLoader(context.classLoader)
                        }
                    }
                }
            } catch (_: Exception) {}
            return result
        }
    }

    class ReturnTrueHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val prefs = (module as HookEntry).prefs
            val enabled = prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST)
            if (!enabled) {
                return chain.proceed()
            }
            return true
        }
    }

    class AuthResultHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val prefs = (module as HookEntry).prefs
            val enabled = prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST)
            if (!enabled) {
                return chain.proceed()
            }
            try {
                val thisObj = chain.thisObject
                thisObj.javaClass.declaredFields.forEach { f ->
                    if (f.name.contains("authBundle")) {
                        f.isAccessible = true
                        (f.get(thisObj) as? Bundle)?.putInt("result_code", 0)
                    }
                }
            } catch (_: Exception) {}
            return chain.proceed()
        }
    }
}


