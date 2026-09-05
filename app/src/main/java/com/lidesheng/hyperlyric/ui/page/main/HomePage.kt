package com.lidesheng.hyperlyric.ui.page.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomePage(
    outerPadding: PaddingValues,
    quote: String,
    isDateSpecificQuote: Boolean,
    onQuoteClick: () -> Unit,
    onQuoteLongPress: () -> Unit,
    onSuperIslandConfigClick: () -> Unit,
    onMediaCardConfigClick: () -> Unit,
    onDynamicIslandConfigClick: () -> Unit,
    onRestartClick: () -> Unit,
    removeFocusWhitelist: Boolean,
    onRemoveFocusWhitelistToggle: (Boolean) -> Unit,
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
                    quote = quote,
                    isDateSpecificQuote = isDateSpecificQuote,
                    onQuoteClick = onQuoteClick,
                    onQuoteLongPress = onQuoteLongPress,
                    onSuperIslandConfigClick = onSuperIslandConfigClick,
                    onMediaCardConfigClick = onMediaCardConfigClick,
                    onDynamicIslandConfigClick = onDynamicIslandConfigClick,
                    onRestartClick = onRestartClick,
                    removeFocusWhitelist = removeFocusWhitelist,
                    onRemoveFocusWhitelistToggle = onRemoveFocusWhitelistToggle,
                    onAppSettingsClick = onAppSettingsClick,
                )
            }
        }
    }
}
