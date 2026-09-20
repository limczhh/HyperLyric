package com.lidesheng.hyperlyric.root

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object UnlockIslandWhitelist {
    private const val TAG = "UnlockIslandWhitelist"
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    private const val TARGET_METHOD = "mediaIslandSupportMiniWindow"

    internal lateinit var module: XposedModule
    private val hookedClassLoaders =
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    private val hookHandles = mutableMapOf<Method, HookHandle>()
    private val knownClassLoaders = mutableSetOf<ClassLoader>()
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun hook(xposedModule: XposedModule, defaultClassLoader: ClassLoader) {
        module = xposedModule
        val prefs = (module as HookEntry).prefs
        val prefKey = RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST

        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == prefKey) {
                val enabled =
                    prefs.getBoolean(prefKey, RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST)
                if (enabled) {
                    hookAllKnownClassLoaders()
                } else {
                    unhookAll()
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        if (prefs.getBoolean(prefKey, RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST)) {
            doHookInClassLoader(defaultClassLoader)
        } else {
            hookedClassLoaders.add(defaultClassLoader)
            knownClassLoaders.add(defaultClassLoader)
        }
    }

    fun doHookInClassLoader(cl: ClassLoader?) {
        if (cl == null || !hookedClassLoaders.add(cl)) return
        knownClassLoaders.add(cl)

        val prefs = (module as? HookEntry)?.prefs ?: return
        val enabled = prefs.getBoolean(
            RootConstants.KEY_HOOK_REMOVE_ISLAND_WHITELIST,
            RootConstants.DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST
        )
        if (!enabled) return

        installHook(cl)
    }

    /** Release only the preference callback; API 102 will hand the hook handles to the next gen. */
    fun prepareForHotReload(): Boolean {
        if (!::module.isInitialized) return true
        val currentModule = module
        val listener = prefsListener
        if (listener != null) {
            val unregistered = runCatching {
                (currentModule as? HookEntry)?.prefs
                    ?.unregisterOnSharedPreferenceChangeListener(listener)
            }.onFailure { error ->
                HookLogger.e(TAG, "注销超级岛白名单偏好监听失败，拒绝热重载", error)
            }.isSuccess
            if (!unregistered) return false
        }
        prefsListener = null
        // Keep the old handle/class-loader indexes until the handoff is accepted.  If the
        // callback declines the reload later, hook() can restore only the listener; clearing these
        // indexes would make that recovery install a second copy of every old hook.
        return true
    }

    private fun installHook(cl: ClassLoader) {
        runCatching {
            val targetClass = cl.loadClass(TARGET_CLASS)
            val method = targetClass.declaredMethods.find { it.name == TARGET_METHOD }

            if (method != null && !hookHandles.containsKey(method)) {
                val handle = module.managedHook(
                    executable = method,
                    capability = "unlock.island.$TARGET_METHOD",
                    hooker = ReturnTrueHooker(),
                )
                hookHandles[method] = handle
                HookLogger.d(
                    TAG,
                    "超级岛下拉小窗白名单 Hook 已安装: method=$TARGET_METHOD"
                )
            }
        }.onFailure { e ->
            if (e !is ClassNotFoundException) {
                HookLogger.e(TAG, "媒体超级岛下拉小窗白名单注入失败", e)
            }
        }
    }

    private fun hookAllKnownClassLoaders() {
        knownClassLoaders.toList().forEach { cl ->
            installHook(cl)
        }
    }

    private fun unhookAll() {
        hookHandles.forEach { (method, handle) ->
            handle.unhook()
            HookRuntimeRegistry.forget(module, handle)
        }
        hookHandles.clear()
        HookLogger.d(TAG, "超级岛下拉小窗白名单 Hook 已移除")
    }

    class ReturnTrueHooker : Hooker {
        override fun intercept(chain: Chain): Any {
            // hook 存在即代表功能开启，无需读取偏好
            return true
        }
    }
}
