@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop

@Composable
fun HomePage(
    outerPadding: PaddingValues,
    randomQuote: String,
    onQuoteClick: () -> Unit,
    onQuoteLongPress: () -> Unit,
    enableSuperIsland: Boolean,
    onSuperIslandToggle: (Boolean) -> Unit,
    enableDynamicIsland: Boolean,
    onDynamicIslandToggle: (Boolean) -> Unit,
    onSuperIslandConfigClick: () -> Unit,
    onDynamicIslandConfigClick: () -> Unit,
    onRestartClick: () -> Unit,
    removeFocusWhitelist: Boolean,
    onRemoveFocusWhitelistToggle: (Boolean) -> Unit,
    removeIslandWhitelist: Boolean,
    onRemoveIslandWhitelistToggle: (Boolean) -> Unit,
    onAppSettingsClick: () -> Unit,
) {
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = "HyperLyric",
                    scrollBehavior = topAppBarScrollBehavior,
                )
            }
        }
    ) { innerPadding ->
        val contentPadding = pageContentPadding(
            innerPadding = innerPadding,
            outerPadding = outerPadding,
            isWideScreen = false,
            extraStart = 0.dp,
            extraEnd = 0.dp,
        )

        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                ),
                contentPadding = contentPadding,
            ) {
                homePageSections(
                    randomQuote = randomQuote,
                    onQuoteClick = onQuoteClick,
                    onQuoteLongPress = onQuoteLongPress,
                    enableSuperIsland = enableSuperIsland,
                    onSuperIslandToggle = onSuperIslandToggle,
                    enableDynamicIsland = enableDynamicIsland,
                    onDynamicIslandToggle = onDynamicIslandToggle,
                    onSuperIslandConfigClick = onSuperIslandConfigClick,
                    onDynamicIslandConfigClick = onDynamicIslandConfigClick,
                    onRestartClick = onRestartClick,
                    removeFocusWhitelist = removeFocusWhitelist,
                    onRemoveFocusWhitelistToggle = onRemoveFocusWhitelistToggle,
                    removeIslandWhitelist = removeIslandWhitelist,
                    onRemoveIslandWhitelistToggle = onRemoveIslandWhitelistToggle,
                    onAppSettingsClick = onAppSettingsClick,
                )
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
