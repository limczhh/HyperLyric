package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginConfig

/** 插件配置（manifest settings 的运行时读取） */
internal data class AmllTtmlConfig(
    /** 总开关（activationSettingKey，默认关闭） */
    val enabled: Boolean,
    /** 歌曲 ID 平台探测开关（默认开启） */
    val platformProbe: Boolean,
    /** 歌词 API 基础地址；空/空白回退默认值。保存时宿主不做格式校验 */
    val apiBaseUrl: String,
) {
    companion object {
        /** 默认歌词 API 基础地址（与 v1.1.0 硬编码行为一致） */
        const val DEFAULT_API_BASE_URL = "https://api.amll.dev/"

        fun from(config: PluginConfig): AmllTtmlConfig = AmllTtmlConfig(
            enabled = config.getBoolean("enabled", false),
            platformProbe = config.getBoolean("platform_probe", true),
            apiBaseUrl = config.getString("api_base_url", null)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_API_BASE_URL,
        )
    }
}
