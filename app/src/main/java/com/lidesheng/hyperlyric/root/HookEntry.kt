package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.root.aitrans.AITranslator
import com.lidesheng.hyperlyric.root.utils.xLog
import com.lidesheng.hyperlyric.root.utils.xLogDebug
import com.lidesheng.hyperlyric.root.utils.xLogError
import com.lidesheng.hyperlyric.root.utils.xLogWarn
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.bridge.LyriconBridge
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.central.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

class HookEntry : XposedModule() {

    companion object {
        var activeMode = 0
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
        com.lidesheng.hyperlyric.root.utils.globalXposedModule = this
        xLog("ModuleInit : 模块已加载")
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
                     xLogWarn("ModuleInit : 此系统版本不支持超级岛下拉小窗白名单")
                 } else {
                     xLogError("ModuleInit : 超级岛下拉小窗白名单注入失败", e)
                 }
            }
            try {
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 if (e is ClassNotFoundException || e is NoSuchMethodException) {
                     xLogWarn("ModuleInit : 此系统版本不支持解锁焦点通知白名单")
                 } else {
                     xLogError("ModuleInit : 焦点通知白名单注入失败", e)
                 }
            }

            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            
            if (!isSuperIslandEnabled) {
                xLog("ModuleInit : 已在设置中禁用超级岛歌词功能")
                return
            }

            activeMode = prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)
            xLog("ModuleInit : 超级岛激活模式 = $activeMode")

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
                xLog("ModuleInit : 系统环境注入成功 (Application.onCreate)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    xLogWarn("ModuleInit : 未找到 Application.onCreate，无法注入环境")
                } else {
                    xLogError("ModuleInit : 注入 Application.onCreate 时发生错误", e)
                }
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
                xLog("ModuleInit : 插件拦截器已就绪 (ClassLoader)")
            } catch (e: Exception) {
                if (e is ClassNotFoundException || e is NoSuchMethodException) {
                    xLogWarn("ModuleInit : 未找到 ClassLoader 构造方法")
                } else {
                    xLogError("ModuleInit : 拦截 ClassLoader 时发生错误", e)
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
                    // xLogWarn("ModuleInit : 插件中未找到超级岛相关类")
                } else {
                    xLogError("ModuleInit : 动态注入超级岛插件失败", e)
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
                    initializeLyricon(app)
                    registerActivePlayerListener()
                    xLog("ModuleInit : 系统环境初始化完成")
                } catch (e: Exception) {
                    xLogError("ModuleInit : 系统环境初始化失败", e)
                }
            }
            return chain.proceed()
        }

        private fun initializeLyricon(app: android.app.Application) {
            ScreenStateMonitor.initialize(app)
            BridgeCentral.initialize(app)
            BridgeCentral.sendBootCompleted()
            AITranslator.init(app)
            initBridgeRouting(app)
        }

        private fun initBridgeRouting(app: android.app.Application) {
            LyriconBridge.routing(app) {
                onCommand(AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE) {
                    xLogDebug("Bridge : 接收到样式更新请求")
                    if (activeMode == 1) HookIslandSpaceGateLyric.refreshActiveIsland() else HookIslandLyric.refreshActiveIsland()
                }
                onCommand("com.lidesheng.hyperlyric.REFRESH_ISLAND") {
                    xLogDebug("Bridge : 接收到超级岛刷新请求")
                    if (activeMode == 1) HookIslandSpaceGateLyric.refreshActiveIsland() else HookIslandLyric.refreshActiveIsland()
                }
                onCommand("com.lidesheng.hyperlyric.UPDATE_LYRIC_ANIM") {
                    xLogDebug("Bridge : 接收到歌词动画刷新请求")
                    if (activeMode == 1) HookIslandSpaceGateLyric.refreshActiveIsland() else HookIslandLyric.refreshActiveIsland()
                }
            }
        }

        private fun registerActivePlayerListener() {
            ActivePlayerDispatcher.addActivePlayerListener(object : ActivePlayerListener {
                override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
                    LyriconDataBridge.clearAll()
                    LyriconDataBridge.activePackageName = providerInfo?.playerPackageName
                }

                override fun onSongChanged(song: Song?) {
                    val hookEntry = if (activeMode == 1) HookIslandSpaceGateLyric.module as? HookEntry else HookIslandLyric.module as? HookEntry
                    LyriconDataBridge.updateSong(song, hookEntry?.prefs)
                    if (activeMode == 1) HookIslandSpaceGateLyric.refreshActiveIsland() else HookIslandLyric.refreshActiveIsland()
                }

                override fun onPlaybackStateChanged(isPlaying: Boolean) {
                    LyriconDataBridge.isPlaying = isPlaying
                    if (activeMode == 1) HookIslandSpaceGateLyric.onPlaybackStateChanged(isPlaying) else HookIslandLyric.onPlaybackStateChanged(isPlaying)
                }

                override fun onPositionChanged(position: Long) {
                    val lyricChanged = LyriconDataBridge.updatePosition(position)
                    if (lyricChanged) {
                        if (activeMode == 1) HookIslandSpaceGateLyric.updateLyricLine() else HookIslandLyric.updateLyricLine()
                    }
                    if (activeMode == 1) HookIslandSpaceGateLyric.updatePosition(position) else HookIslandLyric.updatePosition(position)
                }

                override fun onSeekTo(position: Long) {}

                override fun onSendText(text: String?) {
                    LyriconDataBridge.updateLyric(text)
                    if (activeMode == 1) HookIslandSpaceGateLyric.updateLyricLine() else HookIslandLyric.updateLyricLine()
                }

                override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
                    LyriconDataBridge.isDisplayTranslation = isDisplayTranslation
                    if (activeMode == 1) HookIslandSpaceGateLyric.refreshActiveIsland() else HookIslandLyric.refreshActiveIsland()
                }

                override fun onDisplayRomaChanged(displayRoma: Boolean) {
                    LyriconDataBridge.isDisplayRoma = displayRoma
                    if (activeMode == 1) HookIslandSpaceGateLyric.refreshActiveIsland() else HookIslandLyric.refreshActiveIsland()
                }
            })
        }

    }
}
