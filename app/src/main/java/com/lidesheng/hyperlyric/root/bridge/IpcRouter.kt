package com.lidesheng.hyperlyric.root.bridge

import android.app.Application
import com.lidesheng.hyperlyric.root.source.IslandRenderer
import com.lidesheng.hyperlyric.root.utils.HookLogger

object IpcRouter {

    private const val TAG = "IpcRouter"

    fun initialize(app: Application, renderer: IslandRenderer) {
        LyriconBridge.routing(app) {
            onCommand(AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE) {
                HookLogger.d(TAG, "Bridge : 接收到样式更新请求")
                renderer.refreshActiveIsland()
            }
            onCommand("com.lidesheng.hyperlyric.REFRESH_ISLAND") {
                HookLogger.d(TAG, "Bridge : 接收到超级岛刷新请求")
                renderer.refreshActiveIsland()
            }
            onCommand("com.lidesheng.hyperlyric.UPDATE_LYRIC_ANIM") {
                HookLogger.d(TAG, "Bridge : 接收到歌词动画刷新请求")
                renderer.refreshActiveIsland()
            }
        }
    }
}
