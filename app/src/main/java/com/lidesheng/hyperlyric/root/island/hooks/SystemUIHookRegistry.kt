package com.lidesheng.hyperlyric.root.island.hooks

import com.lidesheng.hyperlyric.root.island.effects.album.IslandAlbumCoverStyleHooker
import com.lidesheng.hyperlyric.root.island.effects.color.IslandMusicWaveColorHooker
import com.lidesheng.hyperlyric.root.island.effects.glow.HookIslandGlow
import com.lidesheng.hyperlyric.root.island.effects.glow.IslandProgressGlowHooker
import com.lidesheng.hyperlyric.root.utils.DynamicFinder
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.libxposed.api.XposedModule

/**
 * SystemUI hook registry.
 */
object SystemUIHookRegistry {
    private const val TAG = "SystemUIHookRegistry"

    lateinit var module: XposedModule

    private val hookedClassLoaders = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<ClassLoader, Boolean>())
    )

    @Volatile
    var isHookedSuccess = false
        private set

    fun hook(xposedModule: XposedModule, cl: ClassLoader, lyricsOnly: Boolean = false) {
        if (cl.javaClass.name.contains("BootClassLoader")) return

        val islandPkg = "miui.systemui.dynamicisland"

        runCatching {
            cl.loadClass("$islandPkg.template.IslandTemplateBuilder")
        }.getOrNull() ?: DynamicFinder.findClassByConstantString(
            cl,
            "$islandPkg.template",
            "IslandTemplateBuilder"
        ) ?: return

        if (!hookedClassLoaders.add(cl)) return
        module = xposedModule

        try {
            IslandTextHooker.hook(module, cl)
            if (!lyricsOnly) {
                IslandMediaSwipeHooker.hook(module, cl)
                HookIslandGlow.init(module, cl)
                IslandProgressGlowHooker.hook(module, cl)
                IslandMusicWaveColorHooker.hook(module, cl)
                IslandAlbumCoverStyleHooker.hook(module, cl)
            }

            isHookedSuccess = true
            HookLogger.i(
                TAG,
                if (lyricsOnly) "超级岛歌词 Hook 已初始化" else "超级岛 Hook 已初始化"
            )
        } catch (e: ClassNotFoundException) {
            HookLogger.w(TAG, "跳过不支持的超级岛插件: reason=${e.message}")
        } catch (e: Exception) {
            HookLogger.e(TAG, "注入超级岛插件失败", e)
        }
    }
}
