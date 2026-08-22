package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginConfig

/** 插件配置（manifest settings 的运行时读取） */
internal data class AmllTtmlConfig(
    /** 总开关（activationSettingKey，默认关闭） */
    val enabled: Boolean,
    /** 歌曲 ID 平台探测开关（默认开启） */
    val platformProbe: Boolean,
) {
    companion object {
        fun from(config: PluginConfig): AmllTtmlConfig = AmllTtmlConfig(
            enabled = config.getBoolean("enabled", false),
            platformProbe = config.getBoolean("platform_probe", true),
        )
    }
}
