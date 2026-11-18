package com.lidesheng.hyperlyric.ui.page

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import com.lidesheng.hyperlyric.ui.utils.rememberExternalLinkOpener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.component.TagComponent
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import com.lidesheng.hyperlyric.ui.utils.AdaptiveIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HelpPage() {
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val tabs = listOf(
        stringResource(R.string.title_super_island_lyrics),
        stringResource(R.string.title_dynamic_island_lyrics)
    )
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val onOpenDownloadLink = rememberExternalLinkOpener(snackbarHostState)

    val superIslandListState = rememberLazyListState()
    val dynamicIslandListState = rememberLazyListState()

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_help),
                    scrollBehavior = topAppBarScrollBehavior,
                    isContentScrolled = (if (pagerState.currentPage == 0) superIslandListState else dynamicIslandListState).canScrollBackward,
                    navigationIcon = {
                        AdaptiveIconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    bottomContent = {
                        AdaptiveTabRow(
                            tabs = tabs,
                            selectedTabIndex = pagerState.targetPage,
                            onTabSelected = { index ->
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1
            ) { page ->
                val listState = if (page == 0) superIslandListState else dynamicIslandListState
                val topPadding = innerPadding.calculateTopPadding()
                val bottomPadding = innerPadding.calculateBottomPadding()
                val contentPadding = pageContentPadding(innerPadding, extraBottom = 16.dp)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                    contentPadding = contentPadding,
                ) {
                    when (page) {
                        0 -> superIslandHelpSections(onOpenDownloadLink)
                        1 -> dynamicIslandHelpSections()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.superIslandHelpSections(onOpenDownloadLink: (String) -> Unit) {
    // 1. 配置流程
    item(key = "config_steps_title") {
        SmallTitle(text = stringResource(R.string.title_help_config_steps))
    }
    item(key = "config_steps_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                BasicComponent(
                    title = stringResource(R.string.summary_help_supported_devices),
                    summary = stringResource(R.string.summary_help_prerequisites)
                )
            }
        }
    }

    // 2. 歌词源
    item(key = "lyric_sources_title") {
        SmallTitle(text = stringResource(R.string.title_help_lyric_sources))
    }
    item(key = "source_lyricon") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                BasicComponent(
                    title = "Lyricon",
                    summary = stringResource(R.string.summary_help_source_lyricon)
                )
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagComponent(
                        text = stringResource(R.string.tag_download_lyricon),
                        onClick = {
                            onOpenDownloadLink("https://github.com/tomakino/lyricon/releases/tag/core")
                        }
                    )
                    TagComponent(
                        text = stringResource(R.string.tag_download_providers),
                        onClick = {
                            onOpenDownloadLink("https://github.com/proify/LyricProvider/releases")
                        }
                    )
                }
            }
        }
    }
    item(key = "source_superlyric") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                BasicComponent(
                    title = "SuperLyric",
                    summary = stringResource(R.string.summary_help_source_superlyric)
                )
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagComponent(
                        text = stringResource(R.string.tag_download_superlyric),
                        onClick = {
                            onOpenDownloadLink("https://github.com/HChenX/SuperLyric")
                        }
                    )
                }
            }
        }
    }
    item(key = "source_lyricinfo") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                BasicComponent(
                    title = "LyricInfo",
                    summary = stringResource(R.string.summary_help_source_lyricinfo)
                )
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TagComponent(
                        text = stringResource(R.string.tag_download_lyricinfo),
                        onClick = {
                            onOpenDownloadLink("https://github.com/limczhh/LyricInfo")
                        }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.dynamicIslandHelpSections() {
    item(key = "dynamic_island_tips_title") {
        SmallTitle(text = stringResource(R.string.title_help_usage_tips))
    }
    item(key = "dynamic_island_tips_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                BasicComponent(
                    title = stringResource(R.string.summary_help_dynamic_island_hint),
                    summary = stringResource(R.string.summary_help_focus_whitelist_hint)
                )
            }
        }
    }
    item(key = "dynamic_island_steps_title") {
        SmallTitle(text = stringResource(R.string.title_help_config_steps))
    }
    item(key = "dynamic_island_steps_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column { BasicComponent(summary = stringResource(R.string.summary_help_dynamic_island_steps)) }
        }
    }
    item(key = "dynamic_island_warm_tips_title") {
        SmallTitle(text = stringResource(R.string.title_help_warm_tips))
    }
    item(key = "dynamic_island_warm_tips_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column { BasicComponent(summary = stringResource(R.string.summary_help_salt_player)) }
        }
    }
}
