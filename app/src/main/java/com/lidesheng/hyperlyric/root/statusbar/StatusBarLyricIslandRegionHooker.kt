package com.lidesheng.hyperlyric.root.statusbar

import android.graphics.Rect
import com.lidesheng.hyperlyric.root.managedHook
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field

/** Observes the same committed island Rect that Xiaomi's status-bar icon containers consume. */
internal object StatusBarLyricIslandRegionHooker {
    private const val TAG = "StatusBarLyricIslandRegionHooker"
    private const val CONTROLLER_CLASS =
        "com.android.systemui.statusbar.StatusBarIslandControllerImpl"
    private const val ISLAND_STATE_HANDLER_CLASS =
        "com.android.systemui.statusbar.StatusBarIslandControllerImpl\$IslandStateHandler"

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        val controllerClass = classLoader.loadClass(CONTROLLER_CLASS)
        val stateHandlerClass = classLoader.loadClass(ISLAND_STATE_HANDLER_CLASS)
        val islandRectField = stateHandlerClass.getDeclaredField("islandRect").apply {
            isAccessible = true
        }

        val stateUpdateMethod = stateHandlerClass.declaredMethods.firstOrNull { method ->
            method.name == "islandUpdate" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    ) && method.returnType == Void.TYPE
        } ?: throw NoSuchMethodException("$ISLAND_STATE_HANDLER_CLASS.islandUpdate(boolean, boolean)")

        module.managedHook(
            executable = stateUpdateMethod.apply { isAccessible = true },
            capability = "status_bar.lyric.native_island_region",
            hooker = IslandRegionUpdatedHook(islandRectField),
        )

        val listenerAddedMethod = runCatching {
            val listenerClass = classLoader.loadClass(
                "com.android.systemui.statusbar.OnIslandStatusChangedListener"
            )
            controllerClass.declaredMethods.firstOrNull { method ->
                method.name == "addOnIslandStatusChangedListener" &&
                        method.parameterTypes.contentEquals(arrayOf(listenerClass)) &&
                        method.returnType == Void.TYPE
            }
        }.onFailure { error ->
            HookLogger.w(TAG, "初始超级岛区域读取不可用: reason=${error.message}")
        }.getOrNull()
        if (listenerAddedMethod != null) {
            runCatching {
                val stateHandlerField = controllerClass.getDeclaredField("islandStateHandler").apply {
                    isAccessible = true
                }
                module.managedHook(
                    executable = listenerAddedMethod.apply { isAccessible = true },
                    capability = "status_bar.lyric.native_island_region.initial_state",
                    hooker = InitialIslandRegionHook(stateHandlerField, islandRectField),
                )
            }.onFailure { error ->
                HookLogger.w(TAG, "初始超级岛区域 Hook 安装失败: reason=${error.message}")
            }
        }

    }

    private fun publishRegion(owner: Any, rectField: Field) {
        val rect = runCatching { rectField.get(owner) as? Rect }.getOrNull()
        StatusBarLyricIslandRegionDispatcher.onNativeIslandRegionChanged(rect)
    }

    private class IslandRegionUpdatedHook(
        private val islandRectField: Field,
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            StatusBarLyricIslandRegionHooker.publishRegion(chain.thisObject, islandRectField)
            return result
        }
    }

    private class InitialIslandRegionHook(
        private val stateHandlerField: Field,
        private val islandRectField: Field,
    ) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val stateHandler = runCatching {
                stateHandlerField.get(chain.thisObject)
            }.getOrNull() ?: return result
            StatusBarLyricIslandRegionHooker.publishRegion(stateHandler, islandRectField)
            return result
        }
    }

}
