@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.QuotesData
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import com.lidesheng.hyperlyric.ui.utils.AdaptiveFloatingActionButton
import com.lidesheng.hyperlyric.ui.utils.AdaptiveIconButton
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTopBarIcon
import com.lidesheng.hyperlyric.ui.utils.adaptiveBackIcon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.glass.glassSearchBarTransition
import top.yukonga.miuix.kmp.glass.rememberGlassSearchBarAlpha
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val SearchResultVerticalPadding = 4.dp

@Composable
fun PoetryPage() {
    val navigator = LocalNavigator.current
    val searchLabel = stringResource(R.string.search)
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val searchBarAlpha = rememberGlassSearchBarAlpha(searchExpanded)
    val searchSafeInsets = WindowInsets.systemBars
        .union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout)
    val searchTopInsets = searchSafeInsets.only(WindowInsetsSides.Top)
    val searchHorizontalInsets = searchSafeInsets.only(WindowInsetsSides.Horizontal)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        Scaffold(
            modifier = Modifier.graphicsLayer { alpha = searchBarAlpha.value },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    color = barColor,
                    contentModifier = Modifier.glassSearchBarTransition(
                        searchExpanded,
                        blurEnabled = blurActive,
                    ) { 1f },
                    title = "HyperLyric",
                    scrollBehavior = topAppBarScrollBehavior,
                    isContentScrolled = listState.canScrollBackward,
                    navigationIcon = {
                        AdaptiveIconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = adaptiveBackIcon(),
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        AdaptiveIconButton(onClick = { searchExpanded = true }) {
                            AdaptiveTopBarIcon(
                                normalIcon = MiuixIcons.Basic.Search,
                                glassIcon = MiuixIcons.Glass.Search,
                                contentDescription = stringResource(R.string.search),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab && !searchExpanded,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                AdaptiveFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    backdrop = backdrop,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = stringResource(R.string.back_to_top),
                        modifier = Modifier.rotate(90f),
                        tint = Color.White,
                    )
                }
            }
        },
        ) { padding ->
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                val contentPadding = pageContentPadding(padding, extraBottom = 16.dp)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                    contentPadding = contentPadding,
                ) {
                    items(QuotesData.list, key = { it }) { quote ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = quote,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(scrollState = listState),
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    trackPadding = contentPadding,
                )
            }
        }
        val searchListState = rememberLazyListState()
        val searchBarSpace = GlassSearchOverlayDefaults.BarHeight +
            GlassSearchOverlayDefaults.VerticalPadding * 2
        GlassSearchOverlay(
            expanded = searchExpanded,
            onDismissRequest = { searchExpanded = false },
            scrollMaterialThreshold = GlassSearchOverlayDefaults.VerticalPadding + SearchResultVerticalPadding,
            label = searchLabel,
            scrollOffset = {
                when {
                    searchListState.layoutInfo.totalItemsCount == 0 -> 0f
                    searchListState.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                    else -> searchListState.firstVisibleItemScrollOffset.toFloat()
                }
            },
            topInsets = searchTopInsets,
            blurEnabled = blurActive,
            horizontalInsetsModifier = Modifier.windowInsetsPadding(searchHorizontalInsets),
        ) { query ->
            val searchQuotes = remember(query) {
                if (query.isBlank()) {
                    emptyList()
                } else {
                    QuotesData.list.filter { it.contains(query, ignoreCase = true) }
                }
            }
            LazyColumn(
                state = searchListState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(searchHorizontalInsets),
                contentPadding = PaddingValues(
                    top = searchTopInsets.asPaddingValues().calculateTopPadding() + searchBarSpace,
                    bottom = 16.dp,
                ),
            ) {
                items(searchQuotes, key = { it }) { quote ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = SearchResultVerticalPadding)
                    ) {
                        Text(
                            text = quote,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
