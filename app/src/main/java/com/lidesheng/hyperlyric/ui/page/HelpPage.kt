package com.lidesheng.hyperlyric.ui.page

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import com.lidesheng.hyperlyric.ui.utils.rememberExternalLinkOpener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import com.lidesheng.hyperlyric.ui.utils.AdaptiveIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
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
    val snackbarHostState = remember { SnackbarHostState() }
    val onOpenDownloadLink = rememberExternalLinkOpener(snackbarHostState)
    val superIslandListState = rememberLazyListState()

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_help),
                    scrollBehavior = topAppBarScrollBehavior,
                    isContentScrolled = superIslandListState.canScrollBackward,
                    navigationIcon = {
                        AdaptiveIconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val contentPadding = pageContentPadding(innerPadding, extraBottom = 16.dp)
            LazyColumn(
                state = superIslandListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding,
            ) {
                superIslandHelpSections(onOpenDownloadLink)
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
