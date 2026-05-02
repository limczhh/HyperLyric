package com.lidesheng.hyperlyric.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HelpPage() {
    val navigator = LocalNavigator.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(
        backgroundColor = MiuixTheme.colorScheme.surface,
        tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
    )

    val tabs = listOf(stringResource(R.string.title_super_island), stringResource(R.string.title_dynamic_island_lyrics))
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 25.dp
                    noiseFactor = 0f
                }
            ) {
                TopAppBar(
                    color = Color.Transparent,
                    title = stringResource(R.string.title_help),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                TabRow(
                    tabs = tabs,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                )
            }
        }
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = true) { page ->
            when (page) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .hazeSource(state = hazeState)
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            start = 12.dp,
                            end = 12.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp
                        ),
                    ) {
                        item {
                            SmallTitle(
                                text = stringResource(R.string.title_help_usage_tips),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    BasicComponent(
                                        title = stringResource(R.string.summary_super_island_lyrics),
                                        summary = stringResource(R.string.summary_help_bug_notice)
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(
                                text = stringResource(R.string.title_help_config_steps),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    BasicComponent(
                                        summary = stringResource(R.string.summary_help_super_island_steps)
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .scrollEndHaptic()
                            .hazeSource(state = hazeState)
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            start = 12.dp,
                            end = 12.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp
                        ),
                    ) {
                        item {
                            SmallTitle(
                                text = stringResource(R.string.title_help_usage_tips),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    BasicComponent(
                                        title = stringResource(R.string.summary_help_dynamic_island_hint),
                                        summary = stringResource(R.string.summary_help_focus_whitelist_hint)
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(
                                text = stringResource(R.string.title_help_config_steps),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    BasicComponent(
                                        summary = stringResource(R.string.summary_help_dynamic_island_steps)
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(
                                text = stringResource(R.string.title_help_warm_tips),
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    BasicComponent(
                                        summary = stringResource(R.string.summary_help_salt_player)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
