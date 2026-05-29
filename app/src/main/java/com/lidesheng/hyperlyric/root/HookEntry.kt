package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.lyric.source.SourceManager
import com.lidesheng.hyperlyric.root.source.LyriconSource
import com.lidesheng.hyperlyric.root.source.RootLyricSink
import com.lidesheng.hyperlyric.root.source.SuperLyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class HookEntry : XposedModule() {

    companion object {
        var activeMode = 0
        val lyriconSource = LyriconSource()
        val superLyricSource = SuperLyricSource()
        var sourceManager: SourceManager? = null
            private set

        @JvmStatic
        var instance: HookEntry? = null
            private set
    }

    private var _prefs: android.content.SharedPreferences? = null

    val prefs: android.content.SharedPreferences
        get() {
            if (_prefs == null) {
                _prefs = getRemotePreferences(UIConstants.PREF_NAME)
            }
            return _prefs!!
        }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        instance = this
        HookLogger.module = this
        HookLogger.i("HookEntry","ModuleInit : 模块已加载")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val processName = runCatching { android.app.Application.getProcessName() }.getOrNull() ?: ""
        
        // 仅在主进程注入
        if (processName.contains(":")) return
        
        val packageName = param.packageName
        
        if (packageName == "com.android.systemui") {
            try {
                UnlockIslandWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 if (e is ClassNotFoundException || e is NoSuchMethodException) {
                     HookLogger.w("HookEntry","ModuleInit : 此系统版本不支持超级岛下拉小窗白名单")
                 } else {
                     HookLogger.e("HookEntry", "ModuleInit : 超级岛下拉小窗白名单注入失败", e)
                 }
            }
            try {
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 if (e is ClassNotFoundException || e is NoSuchMethodException) {
                     HookLogger.w("HookEntry","ModuleInit : 此系统版本不支持解锁焦点通知白名单")
                 } else {
                     HookLogger.e("HookEntry", "ModuleInit : 焦点通知白名单注入失败", e)
                 }
            }

            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            
            if (!isSuperIslandEnabled) {
                HookLogger.i("HookEntry","ModuleInit : 已在设置中禁用超级岛歌词功能")
                return
            }

            activeMode = prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
            HookLogger.i("HookEntry","ModuleInit : 超级岛激活模式 = $activeMode")

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
                HookLogger.i("HookEntry","ModuleInit : 系统环境注入成功 (Application.onCreate)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w("HookEntry","ModuleInit : 未找到 Application.onCreate，无法注入环境")
                } else {
                    HookLogger.e("HookEntry", "ModuleInit : 注入 Application.onCreate 时发生错误", e)
                }
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
                HookLogger.i("HookEntry","ModuleInit : 插件拦截器已就绪 (ClassLoader)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w("HookEntry","ModuleInit : 未找到 ClassLoader 构造方法")
                } else {
                    HookLogger.e("HookEntry", "ModuleInit : 拦截 ClassLoader 时发生错误", e)
                }
            }

        } else if (packageName == "miui.systemui.plugin") {
            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            
            if (!isSuperIslandEnabled) {
                return
            }
            if (activeMode == 1) {
                HookIslandSpaceGateLyric.hook(this, param.defaultClassLoader)
            } else {
                HookIslandLyric.hook(this, param.defaultClassLoader)
            }
        }
    }

    /**
     * 动态类加载器劫持
     */
    inner class ClassLoaderHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val cl = chain.thisObject as? ClassLoader ?: return result
            try {
                if (activeMode == 1) {
                    HookIslandSpaceGateLyric.hook(this@HookEntry, cl)
                } else {
                    HookIslandLyric.hook(this@HookEntry, cl)
                }
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    // HookLogger.w("HookEntry","ModuleInit : 插件中未找到超级岛相关类")
                } else {
                    HookLogger.e("HookEntry", "ModuleInit : 动态注入超级岛插件失败", e)
                }
            }
            return result
        }
    }

    /**
     * Application 生命周期劫持
     */
    class AppCreateHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val app = chain.thisObject as? android.app.Application
            if (app != null) {
                try {
                    val renderer = if (activeMode == 1) HookIslandSpaceGateLyric else HookIslandLyric
                    val entry = instance!!
                    val sink = RootLyricSink(renderer, entry.prefs)

                    lyriconSource.initialize(app, entry.prefs, activeMode)
                    superLyricSource.initialize(app)

                    sourceManager = SourceManager(
                        sources = listOf(lyriconSource, superLyricSource),
                        prefs = entry.prefs,
                        sink = sink,
                        prefKey = RootConstants.KEY_HOOK_LYRIC_SOURCE,
                        defaultSourceId = RootConstants.DEFAULT_HOOK_LYRIC_SOURCE,
                        stateResetter = LyriconDataBridge,
                        logger = HookLogger
                    )
                    sourceManager?.start()

                    HookLogger.i("HookEntry", "ModuleInit : 歌词源 = ${sourceManager?.getActiveSource()?.displayName}")
                    HookLogger.i("HookEntry", "ModuleInit : 系统环境初始化完成")
                } catch (e: Exception) {
                    HookLogger.e("HookEntry", "ModuleInit : 系统环境初始化失败", e)
                }
            }
            return chain.proceed()
        }
    }
}
