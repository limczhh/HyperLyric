package com.lidesheng.hyperlyric.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Setup : Route

    @Serializable
    data object Main : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Plugins : Route

    @Serializable
    data class PluginSettings(val pluginId: String) : Route

    @Serializable
    data class PluginCache(val pluginId: String, val scopeId: String) : Route

    @Serializable
    data class PluginCacheDetail(
        val pluginId: String,
        val scopeId: String,
        val entryId: String,
        val title: String,
        val summary: String? = null,
        val sizeBytes: Long? = null,
        val updatedAtEpochMs: Long? = null,
        val details: List<CacheDetailLine> = emptyList(),
    ) : Route

    /** 宿主侧详情页导航用的可序列化行；由列表页把插件 API 的 PluginCacheDetail 映射而来 */
    @Serializable
    data class CacheDetailLine(val label: String, val value: String)

    @Serializable
    data object HookSettings : Route

    @Serializable
    data object LyricSource : Route

    @Serializable
    data object DynamicIslandNotification : Route

    @Serializable
    data object Log : Route

    @Serializable
    data object LyricAnimation : Route

    @Serializable
    data object LyricSettings : Route

    @Serializable
    data object LyricDisplay : Route

    @Serializable
    data object LyricScroll : Route

    @Serializable
    data object VerbatimLyric : Route

    @Serializable
    data object LyricTranslation : Route

    @Serializable
    data object SuperIslandSettings : Route

    @Serializable
    data object SuperIslandContentLayout : Route

    @Serializable
    data object MediaCardSettings : Route

    @Serializable
    data object NotificationMediaCardSettings : Route

    @Serializable
    data object SuperIslandMediaCardSettings : Route

    @Serializable
    data object AlwaysOnDisplaySettings : Route

    @Serializable
    data object Licenses : Route

    @Serializable
    data object Poetry : Route

    @Serializable
    data object Help : Route

    @Serializable
    data object Changelog : Route

    @Serializable
    data object Contributors : Route
}

