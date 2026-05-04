@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.hooksettings

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import com.lidesheng.hyperlyric.ui.utils.ModuleCategory
import com.lidesheng.hyperlyric.ui.utils.ProviderUiState
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun LyricProviderPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val providerReleaseHome = stringResource(R.string.provider_release_home)
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val providerUiStateFlow = remember { MutableStateFlow(ProviderUiState()) }
    val providerUiState = providerUiStateFlow.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) { LyricProviderManager.loadProviders(context, providerUiStateFlow) }

    val othersCategoryName = stringResource(id = R.string.category_others)
    val groupedModules = remember(providerUiState.value.modules) {
        LyricProviderManager.categorizeModules(providerUiState.value.modules, othersCategoryName)
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(id = R.string.title_lyric_provider),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, providerReleaseHome.toUri())) } catch (_: Exception) {} }) {
                            Icon(painter = painterResource(id = R.drawable.ic_github), contentDescription = stringResource(id = R.string.github), tint = MiuixTheme.colorScheme.onBackground, modifier = Modifier.size(26.dp))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            PullToRefresh(
                isRefreshing = providerUiState.value.isLoading,
                onRefresh = { coroutineScope.launch { LyricProviderManager.loadProviders(context, providerUiStateFlow) } },
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                refreshTexts = listOf(stringResource(id = R.string.refresh_pull_down), stringResource(id = R.string.refresh_release), stringResource(id = R.string.refreshing), stringResource(id = R.string.refresh_success)),
                modifier = Modifier.fillMaxSize()
            ) {
                val lazyListState = rememberLazyListState()
                val top = innerPadding.calculateTopPadding()
                val bottom = innerPadding.calculateBottomPadding()
                val contentPadding = remember(top, bottom) {
                    PaddingValues(top = top, start = 12.dp, end = 12.dp, bottom = bottom)
                }
                Box {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.pageScrollModifiers(true, false, topAppBarScrollBehavior),
                        contentPadding = contentPadding,
                    ) {
                        providerSections(providerUiState.value, groupedModules)
                    }
                    VerticalScrollBar(adapter = rememberScrollBarAdapter(lazyListState), modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(), trackPadding = contentPadding)
                }
            }
        }
    }
}

private fun LazyListScope.providerSections(uiState: ProviderUiState, groupedModules: List<ModuleCategory>) {
    if (!uiState.isLoading && uiState.modules.isEmpty()) {
        item(key = "no_provider") {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(title = stringResource(id = R.string.title_no_provider), summary = stringResource(id = R.string.summary_no_provider))
            }
        }
    } else {
        groupedModules.forEach { category ->
            if (category.name.isNotBlank()) {
                item(key = "header_${category.name}") {
                    SmallTitle(text = category.name, insideMargin = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp))
                }
            }
            items(category.items.size, key = { "provider_${category.items[it].label}" }) { index ->
                val module = category.items[index]
                Card(modifier = Modifier.fillMaxWidth(), pressFeedbackType = PressFeedbackType.Sink) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = module.label, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val version = module.packageInfo.versionName ?: stringResource(id = R.string.unknown)
                        val author = module.author ?: stringResource(id = R.string.unknown_author)
                        Text(text = stringResource(id = R.string.format_version_author, version, author), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        if (!module.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 0.5.dp, color = MiuixTheme.colorScheme.outline)
                            Text(modifier = Modifier.padding(top = 12.dp), text = module.description, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantActions)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
