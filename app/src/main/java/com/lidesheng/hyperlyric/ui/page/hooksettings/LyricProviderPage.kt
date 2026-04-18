package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.LyricModule
import com.lidesheng.hyperlyric.ui.utils.LyricProviderManager
import com.lidesheng.hyperlyric.ui.utils.ProviderUiState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.PullToRefreshState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LyricProviderPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val providerReleaseHome = stringResource(R.string.provider_release_home)
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val hazeStyle = HazeStyle(backgroundColor = MiuixTheme.colorScheme.surface, tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f)))
    
    val coroutineScope = rememberCoroutineScope()
    val providerUiStateFlow = remember { MutableStateFlow(ProviderUiState()) }
    val providerUiState = providerUiStateFlow.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { LyricProviderManager.loadProviders(context, providerUiStateFlow) }

    Scaffold(
        topBar = {
            TopAppBar(
                color = Color.Transparent,
                title = "歌词提供者",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) { Icon(imageVector = MiuixIcons.Back, contentDescription = "返回") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, providerReleaseHome.toUri())
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_github), contentDescription = "GitHub", tint = MiuixTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
                    }
                },
                modifier = Modifier.hazeEffect(hazeState) { style = hazeStyle; blurRadius = 25.dp; noiseFactor = 0f }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            LyricProviderTab(
                uiState = providerUiState.value,
                padding = padding,
                hazeState = hazeState,
                scrollBehavior = scrollBehavior,
                pullToRefreshState = pullToRefreshState,
                onRefresh = { coroutineScope.launch { LyricProviderManager.loadProviders(context, providerUiStateFlow) } }
            )
        }
    }
}

@Composable
private fun LyricProviderTab(uiState: ProviderUiState, padding: PaddingValues, hazeState: HazeState, scrollBehavior: ScrollBehavior, pullToRefreshState: PullToRefreshState, onRefresh: () -> Unit) {
    val groupedModules = remember(uiState.modules) { LyricProviderManager.categorizeModules(uiState.modules, "其他") }

    PullToRefresh(isRefreshing = uiState.isLoading, onRefresh = onRefresh, pullToRefreshState = pullToRefreshState, topAppBarScrollBehavior = scrollBehavior, refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新...", "刷新成功"), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize().scrollEndHaptic().hazeSource(state = hazeState).overScrollVertical(), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding())) {
                if (!uiState.isLoading && uiState.modules.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            BasicComponent(title = "暂未发现歌词提供者", summary = "请点击右上角图标前往 LyricProvider 仓库下载安装并启用插件")
                        }
                    }
                } else {
                    groupedModules.forEach { category ->
                        if (category.name.isNotBlank()) {
                            item(key = "header_${category.name}") {
                                SmallTitle(text = category.name, insideMargin = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp))
                            }
                        }
                        items(category.items.size) { index ->
                            val module = category.items[index]
                            ModuleCard(module)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(module: LyricModule) {
    Card(modifier = Modifier.fillMaxWidth(), pressFeedbackType = PressFeedbackType.Sink) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = module.label, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "${module.packageInfo.versionName ?: "未知"} | ${module.author ?: "未知作者"}", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantActions)

            if (!module.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline)
                Text(modifier = Modifier.padding(top = 12.dp), text = module.description, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
            }
        }
    }
}
