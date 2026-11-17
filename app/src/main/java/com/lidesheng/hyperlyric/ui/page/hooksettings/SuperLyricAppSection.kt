package com.lidesheng.hyperlyric.ui.page.hooksettings

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.utils.SuperLyricApp
import com.lidesheng.hyperlyric.utils.SuperLyricAppManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val APP_ICON_SIZE = 40.dp
private val APP_ICON_SHAPE = RoundedCornerShape(8.dp)
private val APP_CARD_BOTTOM_PADDING = 12.dp
private const val SUPERLYRIC_PACKAGE_NAME = "com.hchen.superlyric"

private fun isSuperLyricInstalled(context: android.content.Context): Boolean {
    return try {
        context.packageManager.getPackageInfo(SUPERLYRIC_PACKAGE_NAME, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

@Composable
internal fun SuperLyricAppSection(
    innerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    promptContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    val contentReady = rememberEntryTransitionContentReady()
    var initialLoadCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            if (!initialLoadCompleted && !SuperLyricAppManager.uiState.value.hasLoaded) {
                SuperLyricAppManager.loadApps(context.applicationContext)
            }
            initialLoadCompleted = true
        }
    }

    val uiState by SuperLyricAppManager.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val refreshPullDown = stringResource(R.string.refresh_pull_down)
    val refreshRelease = stringResource(R.string.refresh_release)
    val refreshing = stringResource(R.string.refreshing)
    val refreshSuccess = stringResource(R.string.refresh_success)
    val refreshTexts = remember(
        refreshPullDown,
        refreshRelease,
        refreshing,
        refreshSuccess
    ) {
        listOf(refreshPullDown, refreshRelease, refreshing, refreshSuccess)
    }
    val showInitialLoading = !contentReady ||
        (!initialLoadCompleted && !uiState.hasLoaded) ||
        (uiState.isInitialLoading && !uiState.hasLoaded)

    if (showInitialLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            promptContent()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator()
            }
        }
    } else {
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            PullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    if (!uiState.isInitialLoading && !uiState.isRefreshing) {
                        coroutineScope.launch {
                            SuperLyricAppManager.loadApps(
                                context = context.applicationContext,
                                forceRefresh = true
                            )
                        }
                    }
                },
                pullToRefreshState = pullToRefreshState,
                contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
                refreshTexts = refreshTexts,
                modifier = Modifier.fillMaxSize()
            ) {
                val lazyListState = rememberLazyListState()
                val top = innerPadding.calculateTopPadding()
                val bottom = innerPadding.calculateBottomPadding()
                val contentPadding = pageContentPadding(innerPadding, extraBottom = 0.dp)
                val isInstalled = if (uiState.hasLoaded) {
                    uiState.superLyricInstalled
                } else {
                    remember { isSuperLyricInstalled(context) }
                }
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                    contentPadding = contentPadding
                ) {
                    item(key = "lyric_source_prompt", contentType = "source_prompt") {
                        promptContent()
                    }
                    if (!isInstalled) {
                        item(key = "superlyric_not_installed", contentType = "not_installed") {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = APP_CARD_BOTTOM_PADDING)
                                    .fillMaxWidth()
                            ) {
                                BasicComponent(
                                    title = stringResource(R.string.title_superlyric_not_installed),
                                    summary = stringResource(R.string.summary_superlyric_not_installed)
                                )
                            }
                        }
                    } else if (uiState.apiApps.isEmpty() && uiState.hookApps.isEmpty()) {
                        item(key = "superlyric_empty", contentType = "empty_state") {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = APP_CARD_BOTTOM_PADDING)
                                    .fillMaxWidth()
                            ) {
                                BasicComponent(
                                    title = stringResource(R.string.title_no_superlyric_apps),
                                    summary = stringResource(R.string.summary_no_superlyric_apps)
                                )
                            }
                        }
                    } else {
                        superLyricAppGroup(
                            titleRes = R.string.title_superlyric_api_apps,
                            keyPrefix = "superlyric_api",
                            apps = uiState.apiApps
                        )
                        superLyricAppGroup(
                            titleRes = R.string.title_superlyric_hook_apps,
                            keyPrefix = "superlyric_hook",
                            apps = uiState.hookApps
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.superLyricAppGroup(
    titleRes: Int,
    keyPrefix: String,
    apps: List<SuperLyricApp>
) {
    if (apps.isEmpty()) return

    item(key = "${keyPrefix}_title", contentType = "section_title") {
        SmallTitle(text = stringResource(titleRes))
    }
    items(
        items = apps,
        key = { app -> "${keyPrefix}_${app.packageName}" },
        contentType = { "superlyric_app" }
    ) { app ->
        SuperLyricAppItem(app)
    }
}

@Composable
private fun SuperLyricAppItem(app: SuperLyricApp) {
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    val versionName = app.apiVersionName ?: app.versionName ?: stringResource(R.string.unknown)
    val versionCode = app.apiVersionCode ?: app.versionCode

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = APP_CARD_BOTTOM_PADDING)
            .fillMaxWidth(),
        onClick = { expanded = !expanded },
        showIndication = false
    ) {
        BasicComponent(
            title = app.label,
            summary = stringResource(
                R.string.format_version_code,
                versionName,
                versionCode
            ),
            startAction = { SuperLyricAppIcon(app) }
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BasicComponent(
                    summary = stringResource(app.usageResId),
                    insideMargin = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 0.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun SuperLyricAppIcon(app: SuperLyricApp) {
    val context = LocalContext.current.applicationContext
    val targetSizePx = with(LocalDensity.current) { APP_ICON_SIZE.roundToPx() }
    val cacheKey = "${app.packageName}:${app.lastUpdateTime}:$targetSizePx"
    val icon by produceState(
        initialValue = SuperLyricAppManager.getCachedIcon(app, targetSizePx),
        key1 = cacheKey
    ) {
        if (value == null) {
            value = SuperLyricAppManager.loadIcon(
                context = context,
                app = app,
                targetSizePx = targetSizePx
            )
        }
    }

    val iconModifier = Modifier
        .size(APP_ICON_SIZE)
        .clip(APP_ICON_SHAPE)
    val loadedIcon = icon
    if (loadedIcon != null) {
        Image(
            bitmap = loadedIcon,
            contentDescription = null,
            modifier = iconModifier
        )
    } else {
        Box(
            modifier = iconModifier.background(
                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f)
            )
        )
    }
}
