package com.lidesheng.hyperlyric.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.lidesheng.hyperlyric.root.utils.log
import com.lidesheng.hyperlyric.root.utils.logError
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
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

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log("onModuleLoaded: HyperLyric 模块已初始化 (API 101)")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        log("onPackageLoaded: $packageName")
        
        if (packageName == "com.android.systemui") {
            try {
                UnlockIslandWhitelist.hook(this, param.defaultClassLoader)
                UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            } catch (e: Exception) {
                 logError("Failed to hook white-lists", e)
            }

            val prefs = getRemotePreferences(UIConstants.PREF_NAME)
            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            if (!isSuperIslandEnabled) {
                log("Super Island is disabled, skipping island hooks.")
                return
            }

            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
                log("Hooked Application.onCreate for com.android.systemui")
            } catch (e: Exception) {
                logError("Failed to hook Application.onCreate", e)
            }

            // 核心：拦截 ClassLoader 构造，以捕捉 miui.systemui.plugin 等动态加载的插件
            try {
                val clClass = Class.forName("dalvik.system.BaseDexClassLoader")
                for (constructor in clClass.declaredConstructors) {
                    deoptimize(constructor)
                    hook(constructor).intercept(ClassLoaderHooker())
                }
                log("Hooked BaseDexClassLoader constructors successfully")
            } catch (e: Exception) {
                logError("Failed to hook ClassLoader constructors", e)
            }

        } else if (packageName == "miui.systemui.plugin") {
            val prefs = getRemotePreferences(UIConstants.PREF_NAME)
            val isSuperIslandEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND, RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND)
            if (!isSuperIslandEnabled) {
                log("Super Island is disabled, skipping plugin hook.")
                return
            }
            log("miui.systemui.plugin package loaded directly, attempting hook...")
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
            HookIslandLyric.hook(this@HookEntry, cl)
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
                    registerRefreshReceiver(app)
                    log("Lyricon environment initialized in SystemUI")
                } catch (e: Exception) {
                    logError("Receiver init failed", e)
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
                    HookIslandLyric.refreshActiveIsland()
                }

                override fun onPlaybackStateChanged(isPlaying: Boolean) {
                    LyriconDataBridge.isPlaying = isPlaying
                    HookIslandLyric.onPlaybackStateChanged(isPlaying)
                }

                override fun onPositionChanged(position: Long) {
                    val lyricChanged = LyriconDataBridge.updatePosition(position)
                    if (lyricChanged) {
                        HookIslandLyric.updateLyricLine()
                    }
                    HookIslandLyric.updatePosition(position)
                }

                override fun onSeekTo(position: Long) {}

                override fun onSendText(text: String?) {
                    LyriconDataBridge.updateLyric(text)
                    HookIslandLyric.updateLyricLine()
                }

                override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
                    LyriconDataBridge.isDisplayTranslation = isDisplayTranslation
                    HookIslandLyric.refreshActiveIsland()
                }

                override fun onDisplayRomaChanged(displayRoma: Boolean) {}
            })
        }

        private fun registerRefreshReceiver(app: android.app.Application) {
            val filter = IntentFilter("com.lidesheng.hyperlyric.REFRESH_ISLAND")
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    log("Received REFRESH_ISLAND broadcast, refreshing island...")
                    HookIslandLyric.refreshActiveIsland()
                }
            }
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        }
    }
}
