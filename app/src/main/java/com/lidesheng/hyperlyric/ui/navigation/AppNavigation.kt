package com.lidesheng.hyperlyric.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
import com.lidesheng.hyperlyric.ui.page.DynamicIslandNotificationPage

@Composable
fun AppNavigation(startRoute: Route) {
    val backStack = remember { mutableStateListOf<NavKey>(startRoute) }
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
                entry<Route.SuperIslandSettings> { SuperIslandSettingsPage() }
                entry<Route.DynamicIslandNotification> { DynamicIslandNotificationPage() }
                entry<Route.Log> { LogPage() }
                entry<Route.Licenses> { LicensesPage() }
                entry<Route.Poetry> { PoetryPage() }
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
