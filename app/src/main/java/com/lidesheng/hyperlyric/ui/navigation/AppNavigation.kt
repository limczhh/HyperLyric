package com.lidesheng.hyperlyric.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lidesheng.hyperlyric.ui.page.MainPage
import com.lidesheng.hyperlyric.ui.page.SetupPage
import com.lidesheng.hyperlyric.ui.page.LicensesPage
import com.lidesheng.hyperlyric.ui.page.LogPage
import com.lidesheng.hyperlyric.ui.page.SettingsPage
import com.lidesheng.hyperlyric.ui.page.PoetryPage
import com.lidesheng.hyperlyric.ui.page.HookSettingsPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.LyricProviderPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.LyricAnimationPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.LyricSettingsPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.SuperIslandSettingsPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.display.LyricDisplayPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.scroll.LyricScrollPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.translation.LyricTranslationPage
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.verbatim.VerbatimLyricPage
import com.lidesheng.hyperlyric.ui.page.DynamicIslandNotificationPage
import com.lidesheng.hyperlyric.ui.page.HelpPage
import com.lidesheng.hyperlyric.ui.page.ChangelogPage

@Composable
fun AppNavigation(startRoute: Route) {
    val backStack = rememberNavBackStack(startRoute)
    val navigator = remember { Navigator(backStack) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        val entryProvider = remember(backStack) {
            entryProvider<NavKey> {
                entry<Route.Setup> {
                    SetupPage(onNavigateToMain = {
                        navigator.popUpTo(Route.Setup, inclusive = true)
                        navigator.navigate(Route.Main)
                    })
                }
                entry<Route.Main> { MainPage() }
                
                entry<Route.Settings> { SettingsPage() }
                entry<Route.HookSettings> { HookSettingsPage() }
                entry<Route.LyricProvider> { LyricProviderPage() }
                entry<Route.LyricAnimation> { LyricAnimationPage() }
                entry<Route.LyricSettings> { LyricSettingsPage() }
                entry<Route.LyricDisplay> { LyricDisplayPage() }
                entry<Route.LyricScroll> { LyricScrollPage() }
                entry<Route.VerbatimLyric> { VerbatimLyricPage() }
                entry<Route.LyricTranslation> { LyricTranslationPage() }
                entry<Route.SuperIslandSettings> { SuperIslandSettingsPage() }
                entry<Route.DynamicIslandNotification> { DynamicIslandNotificationPage() }
                entry<Route.Log> { LogPage() }
                entry<Route.Licenses> { LicensesPage() }
                entry<Route.Poetry> { PoetryPage() }
                entry<Route.Help> { HelpPage() }
                entry<Route.Changelog> { ChangelogPage() }
            }
        }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack, 
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider
        )
        
        NavDisplay(
            entries = entries,
            onBack = { navigator.pop() }
        )
    }
}
