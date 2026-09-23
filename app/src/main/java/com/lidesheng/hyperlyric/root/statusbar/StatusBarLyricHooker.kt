package com.lidesheng.hyperlyric.root.statusbar

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule

/** Installs the Xiaomi HyperOS status-bar lyric host hook. */
internal object StatusBarLyricHooker {
    private const val TAG = "StatusBarLyricHooker"
    private const val STATUS_BAR_VIEW_CLASS =
        "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView"
    private const val MIUI_CLOCK_CLASS = "com.android.systemui.statusbar.views.MiuiClock"

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            StatusBarLyricIslandRegionHooker.hook(module, classLoader)
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "原生超级岛区域监听未安装: reason=${error.message}"
            )
        }
        hookClockVisibility(module, classLoader)

        val method = runCatching {
            classLoader.loadClass(STATUS_BAR_VIEW_CLASS)
                .declaredMethods
                .firstOrNull { candidate ->
                    candidate.name == "onFinishInflate" &&
                            candidate.parameterCount == 0 &&
                            candidate.returnType == Void.TYPE
                }
        }.getOrNull()

        if (method == null) {
            HookLogger.w(TAG, "状态栏歌词 Hook 未安装: reason=target_method_unavailable")
            return
        }

        runCatching {
            method.isAccessible = true
            module.managedHook(
                executable = method,
                capability = "status_bar.lyric.clock_host",
                hooker = StatusBarInflatedHook(),
            )
        }.onFailure { error ->
            HookLogger.w(
                TAG,
                "状态栏歌词 Hook 未安装: reason=${error.message}"
            )
        }
    }

    private fun hookClockVisibility(module: XposedModule, classLoader: ClassLoader) {
        val methods = runCatching {
            classLoader.loadClass(MIUI_CLOCK_CLASS).declaredMethods.filter { method ->
                method.name == "updateClockVisibility" &&
                        method.parameterCount == 0 && method.returnType == Void.TYPE
            }
        }.getOrNull().orEmpty()

        if (methods.isEmpty()) {
            HookLogger.w(TAG, "状态栏歌词时钟保护未安装: reason=visibility_method_unavailable")
            return
        }

        methods.forEach { method ->
            runCatching {
                method.isAccessible = true
                module.managedHook(
                    executable = method,
                    capability = "status_bar.lyric.clock_visibility_guard",
                    hooker = ClockVisibilityHook(),
                )
            }.onFailure { error ->
                HookLogger.w(TAG, "状态栏歌词时钟保护未安装: reason=${error.message}")
            }
        }
    }

    private class StatusBarInflatedHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val root = chain.thisObject as? ViewGroup ?: return result
            StatusBarLyricHostRegistry.registerInflatedRoot(root)
            return result
        }
    }

    private class ClockVisibilityHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val clock = chain.thisObject as? android.view.View ?: return result
            StatusBarLyricRenderer.onClockVisibilityUpdated(
                clock = clock,
                systemVisibility = systemRequestedVisibility(clock),
            )
            return result
        }

        private fun systemRequestedVisibility(clock: android.view.View): Int? = runCatching {
            val policyVisibility = clock.javaClass.getField("mPolicyVisibility").getInt(clock)
            val multiTaskVisibility = clock.javaClass.getField("mMultiTaskVisibility").getInt(clock)
            when {
                policyVisibility == android.view.View.GONE ||
                        multiTaskVisibility == android.view.View.GONE -> android.view.View.GONE

                policyVisibility == android.view.View.INVISIBLE ||
                        multiTaskVisibility == android.view.View.INVISIBLE -> android.view.View.INVISIBLE

                else -> android.view.View.VISIBLE
            }
        }.getOrNull()
    }
}
