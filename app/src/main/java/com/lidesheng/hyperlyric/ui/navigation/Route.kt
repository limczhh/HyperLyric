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
    data object LyricEnhancement : Route

    @Serializable
    data class LyricEnhancementSettings(val featureId: String) : Route

    @Serializable
    data object AmllTtmlCache : Route

    @Serializable
    data object AiTranslationCache : Route

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

