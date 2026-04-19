package com.lidesheng.hyperlyric.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
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

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = "使用帮助",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() }
                    ) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                modifier = Modifier.hazeEffect(hazeState) {
                    style = hazeStyle
                    blurRadius = 25.dp
                    noiseFactor = 0f
                }
            )
        }
    ) { padding ->
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
                    text = "小米超级岛歌词",
                    insideMargin = PaddingValues(10.dp, 4.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        BasicComponent(
                            title = "使用提示",
                            summary = "仅支持HyperOS3设备、lsposed api 101，目前版本存在bug，如果不适配你的机型或系统版本，可用通知型灵动岛歌词代替，还请谅解。"
                        )
                        BasicComponent(
                            title = "配置流程",
                            summary = "1、前往github下载对应lyricon词幕歌词提供器\n" +
                                    "2、在lsposed中启用HyperLyric和歌词提供器\n" +
                                    "3、打开HyperLyric主页的小米超级岛歌词开关\n" +
                                    "4、点击进入歌词白名单页添加音乐软件包名\n" +
                                    "5、重启系统界面和音乐软件"
                        )
                    }
                }
                SmallTitle(
                    text = "通知型灵动岛歌词",
                    insideMargin = PaddingValues(10.dp, 4.dp)
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        BasicComponent(
                            title = "使用提示",
                            summary = "利用实时更新通知和小米焦点通知来实现灵动岛歌词效果，适用于支持这两种通知的设备"
                        )
                        BasicComponent(
                            title = "配置流程",
                            summary = "1、打开HyperLyric主页的通知型灵动岛歌词开关\n" +
                                    "2、点击进入歌词白名单页添加音乐软件包名\n" +
                                    "3、在音乐软件里打开蓝牙歌词功能，并连接蓝牙设备"
                        )
                        BasicComponent(
                            title = "温馨提示",
                            summary = "已支持 Salt Player，更多应用等你发现..."
                        )
                    }
                }
            }
        }
    }
}
