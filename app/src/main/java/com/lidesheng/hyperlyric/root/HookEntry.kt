package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.central.provider.player.ActivePlayerListener
import io.github.proify.lyricon.central.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ProviderInfo

class HookEntry : XposedModule() {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val packageName = param.packageName
        
        if (packageName == "com.android.systemui") {
            HookIslandLyric.hookSystemUIDynamicIsland(this, param)
            UnlockIslandWhitelist.hook(this)
            UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
            
            // 劫持 Application.onCreate 以初始化 Lyricon Receiver 所需的环境
            try {
                val appClass = param.defaultClassLoader.loadClass("android.app.Application")
                val onCreateMethod = appClass.getDeclaredMethod("onCreate")
                deoptimize(onCreateMethod)
                hook(onCreateMethod).intercept(AppCreateHooker())
            } catch (e: Exception) {
                log("[HyperLyric] Failed to hook Application.onCreate: ${e.message}")
            }
        } else if (packageName == "miui.systemui.plugin") {
            UnlockFocusWhitelist.hook(this, param.defaultClassLoader)
        }
    }
}

/**
 * Application.onCreate 拦截器
 *
 * 独立顶层类，遵循 libxposed API 101 的 Hooker 规范：
 * 不依赖外部类引用，通过 [HookIslandLyric.module] 获取 XposedModule 实例。
 */
class AppCreateHooker : Hooker {

    override fun intercept(chain: Chain): Any? {
        val app = chain.thisObject as? android.app.Application
        if (app != null) {
            try {
                initializeLyricon(app)
                registerActivePlayerListener()
                registerRefreshReceiver(app)
            } catch (e: Exception) {
                HookIslandLyric.module.log(Log.ERROR, "HyperLyric", "Receiver init failed: ${e.message}")
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
                // 切换播放器时清空旧数据，防止串台
                LyriconDataBridge.clearAll()
                LyriconDataBridge.activePackageName = providerInfo?.playerPackageName
            }

            override fun onSongChanged(song: Song?) {
                val name = song?.name
                LyriconDataBridge.updateSong(song)
                if (name != null && LyriconDataBridge.activePackageName != null) {
                    HookIslandLyric.updateAllActiveIslands(
                        LyriconDataBridge.currentLyric ?: name,
                        LyriconDataBridge.activePackageName!!
                    )
                }
            }

            override fun onPlaybackStateChanged(isPlaying: Boolean) {
                LyriconDataBridge.isPlaying = isPlaying
                HookIslandLyric.onPlaybackStateChanged(isPlaying)
            }

            override fun onPositionChanged(position: Long) {
                // 极速通道：高速下发进度驱动底层 RenderNode 绘制，不触发父 View 重绘
                val activePkg = LyriconDataBridge.activePackageName
                if (activePkg != null) {
                    HookIslandLyric.updateActiveIslandsPosition(position, activePkg)
                }

                // 兼容普通文本更新
                if (LyriconDataBridge.updatePosition(position)) {
                    val currentLyric = LyriconDataBridge.currentLyric
                    if (currentLyric != null && activePkg != null) {
                        HookIslandLyric.updateAllActiveIslands(currentLyric, activePkg)
                    }
                }
            }

            override fun onSeekTo(position: Long) {}

            override fun onSendText(text: String?) {
                LyriconDataBridge.updateLyric(text)
                if (text != null && LyriconDataBridge.activePackageName != null) {
                    HookIslandLyric.updateAllActiveIslands(text, LyriconDataBridge.activePackageName!!)
                }
            }

            override fun onDisplayTranslationChanged(isDisplayTranslation: Boolean) {
                LyriconDataBridge.isDisplayTranslation = isDisplayTranslation
                val currentLyric = LyriconDataBridge.currentLyric
                val activePkg = LyriconDataBridge.activePackageName
                if (currentLyric != null && activePkg != null) {
                    HookIslandLyric.updateAllActiveIslands(currentLyric, activePkg)
                }
            }

            override fun onDisplayRomaChanged(displayRoma: Boolean) {}
        })
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerRefreshReceiver(app: android.app.Application) {
        val filter = IntentFilter("com.lidesheng.hyperlyric.REFRESH_ISLAND")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                HookIslandLyric.refreshAllActiveIslands()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
    }
}
