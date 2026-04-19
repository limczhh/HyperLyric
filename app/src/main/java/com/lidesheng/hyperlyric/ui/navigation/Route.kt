package com.lidesheng.hyperlyric.ui.navigation

import androidx.navigation3.runtime.NavKey

sealed interface Route : NavKey {
    data object Setup : Route
    data object Main : Route
    data object Settings : Route
    data object HookSettings : Route
    data object DynamicIslandNotification : Route
    data object Log : Route
    data object LyricProvider : Route
    data object LyricAnimation : Route
    data object LyricSettings : Route
    data object SuperIslandSettings : Route
    data object Licenses : Route
    data object Poetry : Route
    data object LyricWhitelist : Route
    data object Help : Route
}
