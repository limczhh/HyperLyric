package com.lidesheng.hyperlyric.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.lidesheng.hyperlyric.ui.page.ChangelogPage
import com.lidesheng.hyperlyric.ui.page.ContributorsPage
import com.lidesheng.hyperlyric.ui.page.DynamicIslandNotificationPage
import com.lidesheng.hyperlyric.ui.page.HelpPage
import com.lidesheng.hyperlyric.ui.page.HookSettingsPage
import com.lidesheng.hyperlyric.ui.page.LicensesPage
import com.lidesheng.hyperlyric.ui.page.LogPage
import com.lidesheng.hyperlyric.ui.page.MainPage
import com.lidesheng.hyperlyric.ui.page.PoetryPage
import com.lidesheng.hyperlyric.ui.page.plugin.PluginManagerPage
import com.lidesheng.hyperlyric.ui.page.plugin.PluginCachePage
import com.lidesheng.hyperlyric.ui.page.plugin.PluginSettingsPage
import com.lidesheng.hyperlyric.ui.page.SettingsPage
import com.lidesheng.hyperlyric.ui.page.SetupPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.LyricAnimationPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.LyricSettingsPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.LyricSourcePage
import com.lidesheng.hyperlyric.ui.page.hooksettings.SuperIslandSettingsPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.contentlayout.ContentLayoutPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.display.LyricDisplayPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.scroll.LyricScrollPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.translation.LyricTranslationPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.verbatim.VerbatimLyricPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.MediaCardSettingsPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.aod.AlwaysOnDisplayPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.island.IslandExpandedMediaCardPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.media.notification.NotificationCenterMediaCardPage
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius

@Composable
fun AppNavigation(startRoute: Route) {
    val backStack = rememberNavBackStack<Route>(startRoute)
    val navigator = remember { Navigator(backStack) }
    val systemCornerRadius = rememberNavSystemCornerRadius()

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            onBack = { navigator.pop() },
            effects = NavDisplayEffects(cornerClipRadius = systemCornerRadius)
        ) {
            entry<Route.Setup> {
                SetupPage(onNavigateToMain = {
                    navigator.popUpTo(Route.Setup, inclusive = true)
                    navigator.navigate(Route.Main)
                })
            }
            entry<Route.Main> { MainPage() }

            entry<Route.Settings> { SettingsPage() }
            entry<Route.Plugins> { PluginManagerPage() }
            entry<Route.PluginSettings> { PluginSettingsPage(it.pluginId) }
            entry<Route.PluginCache> { PluginCachePage(it.pluginId, it.scopeId) }
            entry<Route.HookSettings> { HookSettingsPage() }
            entry<Route.LyricSource> { LyricSourcePage() }
            entry<Route.LyricAnimation> { LyricAnimationPage() }
            entry<Route.LyricSettings> { LyricSettingsPage() }
            entry<Route.LyricDisplay> { LyricDisplayPage() }
            entry<Route.LyricScroll> { LyricScrollPage() }
            entry<Route.VerbatimLyric> { VerbatimLyricPage() }
            entry<Route.LyricTranslation> { LyricTranslationPage() }
            entry<Route.SuperIslandSettings> { SuperIslandSettingsPage() }
            entry<Route.SuperIslandContentLayout> { ContentLayoutPage() }
            entry<Route.MediaCardSettings> { MediaCardSettingsPage() }
            entry<Route.NotificationMediaCardSettings> { NotificationCenterMediaCardPage() }
            entry<Route.SuperIslandMediaCardSettings> { IslandExpandedMediaCardPage() }
            entry<Route.AlwaysOnDisplaySettings> { AlwaysOnDisplayPage() }
            entry<Route.DynamicIslandNotification> { DynamicIslandNotificationPage() }
            entry<Route.Log> { LogPage() }
            entry<Route.Licenses> { LicensesPage() }
            entry<Route.Poetry> { PoetryPage() }
            entry<Route.Help> { HelpPage() }
            entry<Route.Changelog> { ChangelogPage() }
            entry<Route.Contributors> { ContributorsPage() }
        }
    }
}

