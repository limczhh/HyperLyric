package com.lidesheng.hyperlyric.root.island

import com.lidesheng.hyperlyric.root.HookIslandGlow
import com.lidesheng.hyperlyric.root.utils.DynamicFinder
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule

/**
 * SystemUI hook registry.
 */
object SystemUIHookRegistry {
    lateinit var module: XposedModule

    private val hookedClassLoaders = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    )

    @Volatile
    var isHookedSuccess = false
        private set

    fun hook(xposedModule: XposedModule, cl: ClassLoader) {
        if (cl.javaClass.name.contains("BootClassLoader")) return

        val islandPkg = "miui.systemui.dynamicisland"

        runCatching {
            cl.loadClass("$islandPkg.template.IslandTemplateBuilder")
        }.getOrNull() ?: DynamicFinder.findClassByConstantString(cl, "$islandPkg.template", "IslandTemplateBuilder") ?: return

        if (!hookedClassLoaders.add(cl)) return
        module = xposedModule

        try {
            IslandTextHooker.hook(module, cl)
            HookIslandGlow.init(module, cl)
            IslandProgressGlowHooker.hook(module, cl)

            isHookedSuccess = true
            HookLogger.i("SystemUIHookRegistry", "超级岛 Hook 已初始化")
        } catch (e: ClassNotFoundException) {
            HookLogger.w("SystemUIHookRegistry", "跳过不支持的超级岛插件: ${e.message}")
        } catch (e: Exception) {
            HookLogger.e("SystemUIHookRegistry", "注入超级岛插件失败", e)
        }
    }
}
