@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HelpPage() {
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val tabs = listOf(stringResource(R.string.title_super_island_lyrics), stringResource(R.string.title_dynamic_island_lyrics))
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val superIslandListState = rememberLazyListState()
    val dynamicIslandListState = rememberLazyListState()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                Column {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.title_help),
                        scrollBehavior = topAppBarScrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                            }
                        }
                    )
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = pagerState.currentPage,
                        onTabSelected = { index ->
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { page ->
                val listState = if (page == 0) superIslandListState else dynamicIslandListState
                val contentPadding = remember(innerPadding) {
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        start = 0.dp,
                        end = 0.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.pageScrollModifiers(true, true, topAppBarScrollBehavior),
                    contentPadding = contentPadding,
                ) {
                    when (page) {
                        0 -> superIslandHelpSections()
                        1 -> dynamicIslandHelpSections()
                    }
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = contentPadding,
                )
            }
        }
    }
}

private fun LazyListScope.superIslandHelpSections() {
    item(key = "super_island_tips_title") {
        SmallTitle(text = stringResource(R.string.title_help_usage_tips))
    }
    item(key = "super_island_tips_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column {
                BasicComponent(
                    title = stringResource(R.string.summary_super_island_lyrics),
                    summary = stringResource(R.string.summary_help_bug_notice)
                )
            }
        }
    }
    item(key = "super_island_steps_title") {
        SmallTitle(text = stringResource(R.string.title_help_config_steps))
    }
    item(key = "super_island_steps_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column { BasicComponent(summary = stringResource(R.string.summary_help_super_island_steps)) }
        }
    }
}

private fun LazyListScope.dynamicIslandHelpSections() {
    item(key = "dynamic_island_tips_title") {
        SmallTitle(text = stringResource(R.string.title_help_usage_tips))
    }
    item(key = "dynamic_island_tips_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
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
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column { BasicComponent(summary = stringResource(R.string.summary_help_dynamic_island_steps)) }
        }
    }
    item(key = "dynamic_island_warm_tips_title") {
        SmallTitle(text = stringResource(R.string.title_help_warm_tips))
    }
    item(key = "dynamic_island_warm_tips_content") {
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            Column { BasicComponent(summary = stringResource(R.string.summary_help_salt_player)) }
        }
    }
}
