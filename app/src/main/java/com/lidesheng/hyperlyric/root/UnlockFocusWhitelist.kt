package com.lidesheng.hyperlyric.root

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.common.RootConstants
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object UnlockFocusWhitelist {
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    private const val AUTH_CALLBACK_CLASS =
        $$"miui.systemui.notification.auth.AuthManager$AuthServiceCallback$onAuthResult$1"
    private const val PLUGIN_INSTANCE_CLASS = "com.android.systemui.shared.plugins.PluginInstance"

    internal lateinit var module: XposedModule
    private val hookedClassLoaders = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    private val whitelistHandles = mutableListOf<HookHandle>()
    private val knownClassLoaders = mutableSetOf<ClassLoader>()
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun hook(xposedModule: XposedModule, defaultClassLoader: ClassLoader) {
        module = xposedModule
        val prefs = (module as HookEntry).prefs
        val prefKey = RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST

        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == prefKey) {
                val enabled = prefs.getBoolean(prefKey, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST)
                if (enabled) {
                    hookAllKnownClassLoaders()
                } else {
                    unhookWhitelist()
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        // PluginInstance 拦截器始终安装（检测动态加载的插件）
        runCatching {
            val pluginInstanceClass = defaultClassLoader.loadClass(PLUGIN_INSTANCE_CLASS)
            val method = pluginInstanceClass.declaredMethods.find { it.name == "loadPlugin" }
            if (method != null) {
                module.deoptimize(method)
                module.hook(method).intercept(PluginLoadHooker())
                HookLogger.i("UnlockFocusWhitelist", "插件拦截器已就绪 (PluginInstance)")
            } else {
                HookLogger.w("UnlockFocusWhitelist", "未找到 PluginInstance.loadPlugin")
            }
        }.onFailure { e ->
            if (e is ClassNotFoundException) {
                HookLogger.w("UnlockFocusWhitelist", "$PLUGIN_INSTANCE_CLASS 未找到")
            } else {
                HookLogger.e("UnlockFocusWhitelist", "拦截 PluginInstance 时发生错误", e)
            }
        }

        if (prefs.getBoolean(prefKey, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST)) {
            doHookInClassLoader(defaultClassLoader)
        } else {
            hookedClassLoaders.add(defaultClassLoader)
            knownClassLoaders.add(defaultClassLoader)
        }
    }

    private fun doHookInClassLoader(cl: ClassLoader?) {
        if (cl == null || !hookedClassLoaders.add(cl)) return
        knownClassLoaders.add(cl)

        val prefs = (module as? HookEntry)?.prefs ?: return
        val enabled = prefs.getBoolean(RootConstants.KEY_HOOK_REMOVE_FOCUS_WHITELIST, RootConstants.DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST)
        if (!enabled) return

        installWhitelistHooks(cl)
    }

    private fun installWhitelistHooks(cl: ClassLoader) {
        // Hook NotificationSettingsManager (canShowFocus, canCustomFocus)
        runCatching {
            val targetClass = cl.loadClass(TARGET_CLASS)
            val methods = targetClass.declaredMethods.filter {
                it.name == "canShowFocus" || it.name == "canCustomFocus"
            }

            if (methods.isNotEmpty()) {
                methods.forEach { method ->
                    module.deoptimize(method)
                    val handle = module.hook(method).intercept(ReturnTrueHooker())
                    whitelistHandles.add(handle)
                }
                HookLogger.i("UnlockFocusWhitelist", "焦点通知白名单: hook (${methods.joinToString { it.name }})")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e("UnlockFocusWhitelist", "焦点通知白名单注入失败 ($cl)", e)
            }
        }

        // Hook AuthCallback (invokeSuspend)
        runCatching {
            val authClass = cl.loadClass(AUTH_CALLBACK_CLASS)
            val method = authClass.declaredMethods.find { it.name == "invokeSuspend" }

            if (method != null) {
                module.deoptimize(method)
                val handle = module.hook(method).intercept(AuthResultHooker())
                whitelistHandles.add(handle)
                HookLogger.i("UnlockFocusWhitelist", "焦点通知白名单: hook (authCallback)")
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e("UnlockFocusWhitelist", "焦点通知白名单授权注入失败 ($cl)", e)
            }
        }
    }

    private fun hookAllKnownClassLoaders() {
        knownClassLoaders.toList().forEach { cl ->
            installWhitelistHooks(cl)
        }
    }

    private fun unhookWhitelist() {
        whitelistHandles.forEach { it.unhook() }
        whitelistHandles.clear()
        HookLogger.i("UnlockFocusWhitelist", "焦点通知白名单: unhook")
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
            // hook 存在即代表功能开启，无需读取偏好
            return true
        }
    }

    class AuthResultHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            // hook 存在即代表功能开启，直接执行注入逻辑
            runCatching {
                val thisObj = chain.thisObject
                thisObj.javaClass.declaredFields.forEach { f ->
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