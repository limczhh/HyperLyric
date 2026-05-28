package com.lidesheng.hyperlyric.root

import android.content.Context
import android.os.Bundle
import com.lidesheng.hyperlyric.root.utils.HookLogger
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

        // Hook PluginInstance to intercept other plugins loading
        runCatching {
            val pluginInstanceClass = defaultClassLoader.loadClass(PLUGIN_INSTANCE_CLASS)
            val method = pluginInstanceClass.declaredMethods.find { it.name == "loadPlugin" }
            if (method != null) {
                module.deoptimize(method)
                module.hook(method).intercept(PluginLoadHooker())
                HookLogger.i("UnlockFocusWhitelist","ModuleInit : 插件拦截器已就绪 (PluginInstance)")
            } else {
                HookLogger.w("UnlockFocusWhitelist","ModuleInit : 未找到 PluginInstance.loadPlugin")
            }
        }.onFailure { e ->
            if (e is ClassNotFoundException) {
                HookLogger.w("UnlockFocusWhitelist","ModuleInit : $PLUGIN_INSTANCE_CLASS 未找到")
            } else {
                HookLogger.e("UnlockFocusWhitelist", "ModuleInit : 拦截 PluginInstance 时发生错误", e)
            }
        }

        // Initial hook in default class loader
        doHookInClassLoader(defaultClassLoader)
    }

    private fun doHookInClassLoader(cl: ClassLoader?) {
        if (cl == null || !hookedClassLoaders.add(cl)) return

        // Hook NotificationSettingsManager (canShowFocus, canCustomFocus)
        runCatching {
            val targetClass = cl.loadClass(TARGET_CLASS)
            val methods = targetClass.declaredMethods.filter { 
                it.name == "canShowFocus" || it.name == "canCustomFocus" 
            }
            
            if (methods.isNotEmpty()) {
                methods.forEach { method ->
                    module.deoptimize(method)
                    module.hook(method).intercept(ReturnTrueHooker())
                }
                HookLogger.i("UnlockFocusWhitelist","ModuleInit : 成功注入焦点通知白名单 (${methods.joinToString { it.name }})")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e("UnlockFocusWhitelist", "ModuleInit : 焦点通知白名单注入失败 ($cl)", e)
            }
        }

        // Hook AuthCallback (invokeSuspend)
        runCatching {
            val authClass = cl.loadClass(AUTH_CALLBACK_CLASS)
            val method = authClass.declaredMethods.find { it.name == "invokeSuspend" }
            
            if (method != null) {
                module.deoptimize(method)
                module.hook(method).intercept(AuthResultHooker())
                HookLogger.i("UnlockFocusWhitelist","ModuleInit : 焦点通知白名单授权回调注入成功")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e("UnlockFocusWhitelist", "ModuleInit : 焦点通知白名单授权注入失败 ($cl)", e)
            }
        }
    }

    class PluginLoadHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
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
            }
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
            runCatching {
                val thisObj = chain.thisObject
                thisObj.javaClass.declaredFields.forEach { f ->
                    // Handle obfuscated field name if any, currently checking for "authBundle"
                    if (f.name.contains("authBundle")) {
                        f.isAccessible = true
                        (f.get(thisObj) as? Bundle)?.putInt("result_code", 0)
                    }
                }
            }
            return chain.proceed()
        }
    }
}



