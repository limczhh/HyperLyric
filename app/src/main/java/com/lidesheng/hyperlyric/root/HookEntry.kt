package com.lidesheng.hyperlyric.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.lidesheng.hyperlyric.Constants
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

/**
 * Modern Xposed API 101 入口
 */
class HookEntry : XposedModule() {

    private val tag = "HyperLyricEntry"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        Log.i(tag, "onModuleLoaded: HyperLyric 模块已初始化 (API 101)")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        Log.i(tag, "onPackageLoaded: $packageName")
        
        if (packageName == "com.android.systemui") {
            // 系统 UI 级别的 Hook
            try {
                UnlockIslandWhitelist.hook(this, param.defaultClassLoader)
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 Log.e(tag, "Failed to hook white-lists: ${e.message}")
            }

            // 读取超级岛开关，关闭时跳过 Hook 注入（需重启系统界面生效）
            val prefs = getRemotePreferences(Constants.PREF_NAME)
            val isSuperIslandEnabled = prefs.getBoolean(Constants.KEY_ENABLE_SUPER_ISLAND, Constants.DEFAULT_ENABLE_SUPER_ISLAND)
            if (!isSuperIslandEnabled) {
                Log.i(tag, "Super Island is disabled, skipping island hooks.")
                return
            }

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
                Log.i(tag, "Hooked Application.onCreate for com.android.systemui")
            } catch (e: Exception) {
                Log.e(tag, "Failed to hook Application.onCreate: ${e.message}")
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
                Log.i(tag, "Hooked BaseDexClassLoader constructors successfully")
            } catch (e: Exception) {
                Log.e(tag, "Failed to hook ClassLoader constructors: ${e.message}")
            }

        } else if (packageName == "miui.systemui.plugin") {
            // 虽然是插件，但如果 LSPosed 能够直接识别该包加载，则尝试直接注入
            val prefs = getRemotePreferences(Constants.PREF_NAME)
            val isSuperIslandEnabled = prefs.getBoolean(Constants.KEY_ENABLE_SUPER_ISLAND, Constants.DEFAULT_ENABLE_SUPER_ISLAND)
            if (!isSuperIslandEnabled) {
                Log.i(tag, "Super Island is disabled, skipping plugin hook.")
                return
            }
            Log.i(tag, "miui.systemui.plugin package loaded directly, attempting hook...")
            UniversalIslandHook.hook(this, param.defaultClassLoader)
        }
    }

    /**
     * 动态类加载器劫持
     */
    inner class ClassLoaderHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val cl = chain.thisObject as? ClassLoader ?: return result
            
            // 尝试在每一个新创建的类加载器中寻找超级岛逻辑
            UniversalIslandHook.hook(this@HookEntry, cl)
            return result
        }
    }

    /**
     * Application 生命周期劫持
     */
    inner class AppCreateHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val app = chain.thisObject as? android.app.Application
            if (app != null) {
                try {
                    initializeLyricon(app)
                    registerActivePlayerListener()
                    registerRefreshReceiver(app)
                    Log.i(tag, "Lyricon environment initialized in SystemUI")
                } catch (e: Exception) {
                    Log.e(tag, "Receiver init failed: ${e.message}")
                }
            }
            return chain.proceed()
        }

        private fun initializeLyricon(app: android.app.Application) {
            ScreenStateMonitor.initialize(app)
            BridgeCentral.initialize(app)
            BridgeCentral.sendBootCompleted()
        }

        private fun registerActivePlayerListener() {
            ActivePlayerDispatcher.addActivePlayerListener(object : ActivePlayerListener {
                override fun onActiveProviderChanged(providerInfo: ProviderInfo?) {
                    LyriconDataBridge.clearAll()
                    LyriconDataBridge.activePackageName = providerInfo?.playerPackageName
                }

                override fun onSongChanged(song: Song?) {
                    LyriconDataBridge.updateSong(song)
                    UniversalIslandHook.refreshActiveIsland()
                }

                override fun onPlaybackStateChanged(isPlaying: Boolean) {
                    LyriconDataBridge.isPlaying = isPlaying
                    UniversalIslandHook.onPlaybackStateChanged(isPlaying)
                }

                override fun onPositionChanged(position: Long) {
                    val lyricChanged = LyriconDataBridge.updatePosition(position)
                    if (lyricChanged) {
                        UniversalIslandHook.updateLyricLine()
                    }
                    UniversalIslandHook.updatePosition(position)
                }

                override fun onSeekTo(position: Long) {}

                override fun onSendText(text: String?) {
                    LyriconDataBridge.updateLyric(text)
                    UniversalIslandHook.updateLyricLine()
                }

                override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
                    LyriconDataBridge.isDisplayTranslation = isDisplayTranslation
                    UniversalIslandHook.refreshActiveIsland()
                }

                override fun onDisplayRomaChanged(displayRoma: Boolean) {}
            })
        }

        private fun registerRefreshReceiver(app: android.app.Application) {
            val filter = IntentFilter("com.lidesheng.hyperlyric.REFRESH_ISLAND")
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.i(tag, "Received REFRESH_ISLAND broadcast, refreshing island...")
                    UniversalIslandHook.refreshActiveIsland()
                }
            }
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        }
    }
}
