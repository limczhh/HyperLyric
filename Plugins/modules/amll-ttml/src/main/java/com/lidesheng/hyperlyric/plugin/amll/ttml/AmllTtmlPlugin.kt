package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.HyperLyricPlugin
import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import com.lidesheng.hyperlyric.plugin.api.PluginContext

/**
 * AMLL TTML 逐字歌词插件入口
 *
 * onLoad 注册 [AmllTtmlProcessor]（LYRIC_REPLACEMENT 阶段）；
 * 无需关闭的持久资源（HttpURLConnection 无连接池保持，缓存/存储由宿主管理）。
 */
class AmllTtmlPlugin : HyperLyricPlugin {
    private var context: PluginContext? = null
    private var processor: AmllTtmlProcessor? = null

    override fun onLoad(context: PluginContext) {
        this.context = context
        val created = AmllTtmlProcessor(context)
        processor = created
        context.registerExtension(created)
        // 缓存管理扩展（id="ttml"，与 manifest cacheScopes 声明一致）
        context.registerExtension(created.cacheExtension())
    }

    override fun onEnable() {
        context?.logger?.info("lifecycle=onEnable")
    }

    override fun onConfigChanged(config: PluginConfig) {
        // 配置修改无需重启即对下一次处理生效；正在进行的处理不中断（宿主语义）
        context?.logger?.info("lifecycle=onConfigChanged")
    }

    override fun onUnload() {
        context?.logger?.info("lifecycle=onUnload")
        processor = null
        context = null
    }
}
