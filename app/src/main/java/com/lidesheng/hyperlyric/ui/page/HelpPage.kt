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

    val tabs = listOf(stringResource(R.string.title_super_island_lyrics), stringResource(R.string.title_dynamic_island_lyrics))
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
                        IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = "返回") }
                    }
                )
                TabRow(
                    tabs = tabs,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
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
                                text = "使用提示",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card {
                                Column {
                                    BasicComponent(
                                        title = "仅支持HyperOS 3、LSPosed v2.0",
                                        summary = "目前版本存在bug，如果不适配你的机型或系统版本，可用通知型灵动岛歌词代替，还请谅解"
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(
                                text = "配置流程",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card {
                                Column {
                                    BasicComponent(
                                        summary = "1、前往github下载对应lyricon词幕歌词提供器\n" +
                                                "2、在lsposed中启用HyperLyric和歌词提供器\n" +
                                                "3、打开HyperLyric主页的小米超级岛歌词开关\n" +
                                                "4、点击进入歌词白名单页添加音乐软件包名\n" +
                                                "5、重启系统界面和音乐软件"
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
                                text = "使用提示",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card {
                                Column {
                                    BasicComponent(
                                        title = "利用实时通知和小米焦点通知来实现灵动岛歌词效果，适用于支持这两种通知的设备",
                                        summary = "小米的焦点通知有白名单验证，需要你自己想办法绕过"
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(
                                text = "配置流程",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card {
                                Column {
                                    BasicComponent(
                                        summary = "1、打开HyperLyric主页的通知型灵动岛歌词开关\n" +
                                                "2、点击进入歌词白名单页添加音乐软件包名\n" +
                                                "3、在音乐软件里打开蓝牙歌词功能，并连接蓝牙设备"
                                    )
                                }
                            }
                        }
                        item {
                            SmallTitle(
                                text = "温馨提示",
                                insideMargin = PaddingValues(10.dp, 4.dp)
                            )
                            Card {
                                Column {
                                    BasicComponent(
                                        summary = "已支持 Salt Player，更多应用等你发现..."
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
