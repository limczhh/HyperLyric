package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.lyric.source.SourceManager
import com.lidesheng.hyperlyric.root.bridge.IpcRouter
import com.lidesheng.hyperlyric.root.source.IslandRenderer
import com.lidesheng.hyperlyric.root.source.LyriconSource
import com.lidesheng.hyperlyric.root.source.LyricInfoSource
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
        @Volatile
        var activeMode = 0
        val lyriconSource = LyriconSource()
        val superLyricSource = SuperLyricSource()
        var lyricInfoSource: LyricInfoSource? = null
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
        HookLogger.i("HookEntry","模块已加载")
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
                     HookLogger.w("HookEntry","此系统版本不支持超级岛下拉小窗白名单")
                 } else {
                     HookLogger.e("HookEntry", "超级岛下拉小窗白名单注入失败", e)
                 }
            }
            try {
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 if (e is ClassNotFoundException || e is NoSuchMethodException) {
                     HookLogger.w("HookEntry","此系统版本不支持解锁焦点通知白名单")
                 } else {
                     HookLogger.e("HookEntry", "焦点通知白名单注入失败", e)
                 }
            }

            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            
            if (!isSuperIslandEnabled) {
                HookLogger.i("HookEntry","已在设置中禁用超级岛歌词功能")
                return
            }

            activeMode = prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
            HookLogger.i("HookEntry","超级岛激活模式 = $activeMode")

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
                HookLogger.i("HookEntry","系统环境注入成功 (Application.onCreate)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w("HookEntry","未找到 Application.onCreate，无法注入环境")
                } else {
                    HookLogger.e("HookEntry", "注入 Application.onCreate 时发生错误", e)
                }
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
                HookLogger.i("HookEntry","插件拦截器已就绪 (ClassLoader)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    HookLogger.w("HookEntry","未找到 ClassLoader 构造方法")
                } else {
                    HookLogger.e("HookEntry", "拦截 ClassLoader 时发生错误", e)
                }
            }

        } else if (packageName == "miui.systemui.plugin") {
            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            
            if (!isSuperIslandEnabled) {
                return
            }
            HookIslandLyric.hook(this, param.defaultClassLoader)
        }
    }

    /**
     * 动态类加载器劫持
     */
    inner class ClassLoaderHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val cl = chain.thisObject as? ClassLoader ?: return result
            if (!prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)) return result
            try {
                HookIslandLyric.hook(this@HookEntry, cl)
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    // HookLogger.w("HookEntry","插件中未找到超级岛相关类")
                } else {
                    HookLogger.e("HookEntry", "动态注入超级岛插件失败", e)
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
                    val entry = instance!!
                    // 两个 renderer 的 module 都需要初始化（热切换时两个都会被用到）
                    HookIslandLyric.module = entry
                    HookIslandSpaceGateLyric.module = entry
                    val renderer = if (activeMode == 1) HookIslandSpaceGateLyric else HookIslandLyric
                    val sink = RootLyricSink(renderer, entry.prefs)

                    IpcRouter.initialize(app)

                    lyriconSource.initialize(app, entry.prefs, activeMode)
                    superLyricSource.initialize(app)
                    lyricInfoSource = LyricInfoSource(app)

                    sourceManager = SourceManager(
                        sources = listOf(lyriconSource, superLyricSource, lyricInfoSource!!),
                        prefs = entry.prefs,
                        sink = sink,
                        prefKey = RootConstants.KEY_HOOK_LYRIC_SOURCE,
                        defaultSourceId = RootConstants.DEFAULT_HOOK_LYRIC_SOURCE,
                        stateResetter = LyriconDataBridge,
                        logger = HookLogger
                    )
                    sourceManager?.start()

                    val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            RootConstants.KEY_HOOK_LYRIC_SOURCE -> {
                                val newSourceId = entry.prefs.getString(key, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE) ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                                HookLogger.i("HookEntry", "歌词源切换: $newSourceId")
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    sourceManager?.switchSource(newSourceId)
                                }
                            }
                            RootConstants.KEY_HOOK_LYRIC_MODE -> {
                                val newMode = entry.prefs.getInt(key, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
                                if (newMode == activeMode) return@OnSharedPreferenceChangeListener
                                HookLogger.i("HookEntry", "歌词模式切换: $newMode")
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    // 1. 清除旧视图
                                    if (activeMode == 1) HookIslandSpaceGateLyric.clearAllViews() else HookIslandLyric.clearAllViews()
                                    // 2. 传递 activeIslandPkgNames 到新 renderer
                                    val oldPkgNames = if (activeMode == 1) HookIslandSpaceGateLyric.activeIslandPkgNames else HookIslandLyric.activeIslandPkgNames
                                    val newPkgNames = if (newMode == 1) HookIslandSpaceGateLyric.activeIslandPkgNames else HookIslandLyric.activeIslandPkgNames
                                    synchronized(oldPkgNames) { newPkgNames.putAll(oldPkgNames) }
                                    // 3. 先更新 renderer 引用，再更新 activeMode（保证线程安全顺序）
                                    val newRenderer: IslandRenderer = if (newMode == 1) HookIslandSpaceGateLyric else HookIslandLyric
                                    lyriconSource.updateRenderer(newRenderer, newMode)
                                    sink.updateRenderer(newRenderer)
                                    activeMode = newMode
                                    // 4. 新 renderer 注入视图
                                    newRenderer.refreshActiveIsland()
                                }
                            }
                        }
                    }
                    entry.prefs.registerOnSharedPreferenceChangeListener(prefListener)

                    HookLogger.i("HookEntry", "歌词源 = ${sourceManager?.getActiveSource()?.displayName}")
                    HookLogger.i("HookEntry", "系统环境初始化完成")
                } catch (e: Exception) {
                    HookLogger.e("HookEntry", "系统环境初始化失败", e)
                }
            }
            return chain.proceed()
        }
    }
}
