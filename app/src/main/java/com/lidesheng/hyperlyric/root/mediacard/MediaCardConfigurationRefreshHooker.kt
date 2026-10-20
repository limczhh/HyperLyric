package com.lidesheng.hyperlyric.root.mediacard

import android.app.Application
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.root.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hooks the SystemUI application implementation because HyperOS bypasses Application callbacks.
 */
internal object MediaCardConfigurationRefreshHooker {
    private const val TAG = "MediaCardConfigurationRefresh"
    private val SYSTEM_UI_APPLICATION_CLASSES = listOf(
        "com.android.systemui.application.impl.SystemUIApplicationImpl",
        "com.android.systemui.SystemUIApplication",
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val nightModes = Collections.synchronizedMap(
        WeakHashMap<Application, Int>()
    )

    fun hook(xposedModule: XposedModule, classLoader: ClassLoader) {
        if (!MediaCardRuntimeConfig.current.enabled || !hookedClassLoaders.add(classLoader)) {
            return
        }

        val method = SYSTEM_UI_APPLICATION_CLASSES.firstNotNullOfOrNull { className ->
            runCatching {
                classLoader.loadClass(className)
                    .declaredMethods
                    .firstOrNull { candidate ->
                        candidate.name == "onConfigurationChanged" &&
                            candidate.parameterTypes.contentEquals(
                                arrayOf(Configuration::class.java)
                            ) &&
                            candidate.returnType == Void.TYPE
                    }
            }.getOrNull()
        }

        if (method == null) {
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(TAG, "媒体卡片主题刷新 Hook 未安装: reason=target_method_unavailable")
            return
        }

        runCatching {
            method.isAccessible = true
            xposedModule.managedHook(
                executable = method,
                capability = "media.card.configuration_changed",
                hooker = ConfigurationChangedHook(),
            )
        }.onSuccess {
            HookLogger.d(
                TAG,
                "媒体卡片主题刷新 Hook 已初始化: target=${method.declaringClass.name}"
            )
        }.onFailure { error ->
            hookedClassLoaders.remove(classLoader)
            HookLogger.w(
                TAG,
                "媒体卡片主题刷新 Hook 未安装: reason=${error.message}"
            )
        }
    }

    private class ConfigurationChangedHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (!MediaCardRuntimeConfig.current.enabled) return result

            val application = chain.thisObject as? Application ?: return result
            val configuration = chain.args.firstOrNull() as? Configuration ?: return result
            val nextNightMode = nightMode(configuration)
            val changed = synchronized(nightModes) {
                val previousNightMode = nightModes.put(application, nextNightMode)
                previousNightMode == null || previousNightMode != nextNightMode
            }
            if (changed) refreshForUiMode(application, nextNightMode)
            return result
        }
    }

    private fun refreshForUiMode(application: Application, expectedNightMode: Int) {
        mainHandler.post {
            val isCurrent = synchronized(nightModes) {
                nightModes[application] == expectedNightMode
            }
            if (!isCurrent || !MediaCardRuntimeConfig.current.enabled) return@post

            MediaCardUiModeRefreshCoordinator.refresh()
        }
    }

    private fun nightMode(configuration: Configuration): Int =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
}
