package com.lidesheng.hyperlyric.ui.page.hooksettings


import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import top.yukonga.miuix.kmp.basic.BasicComponent
import com.lidesheng.hyperlyric.ui.component.TagComponent
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.utils.LyricProviderManager
import com.lidesheng.hyperlyric.utils.LyricModule
import com.lidesheng.hyperlyric.utils.ModuleCategory
import com.lidesheng.hyperlyric.utils.ModuleTag
import com.lidesheng.hyperlyric.utils.ProviderUiState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val PROVIDER_ICON_SIZE = 40.dp
private val PROVIDER_ICON_SHAPE = RoundedCornerShape(8.dp)
private val LYRIC_DELAY_KEY_POINTS = listOf(
    -5000f,
    -4000f,
    -3000f,
    -2000f,
    -1000f,
    0f,
    1000f,
    2000f,
    3000f,
    4000f,
    5000f
)

@Composable
internal fun LyricProviderSection(
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
            if (!initialLoadCompleted && !LyricProviderManager.uiState.value.hasLoaded) {
                LyricProviderManager.loadProviders(context.applicationContext)
            }
            initialLoadCompleted = true
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val providerUiState by LyricProviderManager.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()

    val othersCategoryName = stringResource(id = R.string.category_others)
    val groupedModules = remember(providerUiState.modules, othersCategoryName) {
        LyricProviderManager.categorizeModules(providerUiState.modules, othersCategoryName)
    }

    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    val refreshPullDown = stringResource(id = R.string.refresh_pull_down)
    val refreshRelease = stringResource(id = R.string.refresh_release)
    val refreshing = stringResource(id = R.string.refreshing)
    val refreshSuccess = stringResource(id = R.string.refresh_success)
    val refreshTexts = remember(
        refreshPullDown,
        refreshRelease,
        refreshing,
        refreshSuccess
    ) {
        listOf(refreshPullDown, refreshRelease, refreshing, refreshSuccess)
    }

    val showInitialLoading = !contentReady ||
            (!initialLoadCompleted && !providerUiState.hasLoaded) ||
            (providerUiState.isInitialLoading && !providerUiState.hasLoaded)
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
                isRefreshing = providerUiState.isRefreshing,
                onRefresh = {
                    if (!providerUiState.isInitialLoading && !providerUiState.isRefreshing) {
                        coroutineScope.launch {
                            LyricProviderManager.loadProviders(
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
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.pageScrollModifiers(
                        enableScrollEndHaptic = true,
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior
                    ),
                    contentPadding = contentPadding,
                ) {
                    item(key = "lyric_source_prompt", contentType = "source_prompt") {
                        promptContent()
                    }
                    providerSections(
                        uiState = providerUiState,
                        groupedModules = groupedModules,
                        expandedStates = expandedStates
                    )
                }
            }
        }
    }
}

private fun LazyListScope.providerSections(
    uiState: ProviderUiState,
    groupedModules: List<ModuleCategory>,
    expandedStates: MutableMap<String, Boolean>
) {
    if (!uiState.hasLoaded && uiState.modules.isEmpty()) {
        return
    }

    if (uiState.modules.isEmpty()) {
        item(key = "no_provider", contentType = "empty_state") {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                BasicComponent(
                    title = stringResource(id = R.string.title_no_provider),
                    summary = stringResource(id = R.string.summary_no_provider)
                )
            }
        }
    } else {
        item(key = "provider_title", contentType = "section_title") {
            SmallTitle(text = stringResource(id = R.string.title_lyric_provider))
        }
        groupedModules.forEach { category ->
            if (category.name.isNotBlank()) {
                item(
                    key = "header_${category.name}",
                    contentType = "category_header"
                ) {
                    SmallTitle(
                        text = category.name,
                        insideMargin = PaddingValues(
                            start = 10.dp,
                            end = 10.dp,
                            top = 12.dp,
                            bottom = 4.dp
                        )
                    )
                }
            }
            items(
                items = category.items,
                key = { "provider_${it.packageName}" },
                contentType = { "provider_card" }
            ) { module ->
                val packageName = module.packageName
                val isExpanded = expandedStates[packageName] ?: false

                val delayKey = RootConstants.KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX + packageName
                val initialDelay = remember(packageName) {
                    PrefsBridge.getInt(delayKey, RootConstants.DEFAULT_HOOK_LYRICON_PROVIDER_DELAY)
                }
                var currentDelay by remember(packageName) { mutableIntStateOf(initialDelay) }
                var sliderPosition by remember(packageName) { mutableFloatStateOf(initialDelay.toFloat()) }

                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                        .fillMaxWidth(),
                    onClick = { expandedStates[packageName] = !isExpanded }
                ) {

                    Column {
                        ProviderHeader(
                            module = module,
                            currentDelay = currentDelay
                        )

                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                BasicComponent(
                                    summary = module.description,
                                    insideMargin = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 10.dp,
                                        bottom = 0.dp
                                    )
                                )
                                if (module.tags.isNotEmpty()) {
                                    ModuleTagsFlow(module.tags)
                                }

                                Column(
                                    modifier = Modifier.padding(
                                        PaddingValues(
                                            start = 16.dp,
                                            top = 16.dp,
                                            end = 16.dp,
                                            bottom = 0.dp
                                        )
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.title_lyric_delay),
                                            color = MiuixTheme.colorScheme.onBackground,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.summary_lyric_delay),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            fontSize = 14.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Slider(
                                        value = sliderPosition,
                                        onValueChange = { sliderValue ->
                                            sliderPosition = sliderValue
                                            currentDelay = (sliderValue / 50f).roundToInt() * 50
                                        },
                                        onValueChangeFinished = {
                                            val finalValue =
                                                (sliderPosition / 50f).roundToInt() * 50
                                            sliderPosition = finalValue.toFloat()
                                            currentDelay = finalValue
                                            PrefsBridge.putInt(delayKey, finalValue)
                                        },
                                        valueRange = RootConstants.MIN_HOOK_LYRICON_PROVIDER_DELAY.toFloat()..RootConstants.MAX_HOOK_LYRICON_PROVIDER_DELAY.toFloat(),
                                        steps = 199,
                                        showKeyPoints = true,
                                        keyPoints = LYRIC_DELAY_KEY_POINTS,
                                        hapticEffect = SliderDefaults.SliderHapticEffect.Step
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleTagsFlow(tags: List<ModuleTag>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        tags.forEach { tag ->
            val title =
                if (tag.titleRes != -1) stringResource(tag.titleRes) else tag.title.orEmpty()
            TagComponent(
                text = title,
                iconRes = tag.iconRes,
                imageVector = tag.imageVector,
                isRainbow = tag.isRainbow,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
    }
}

@Composable
private fun ProviderIcon(module: LyricModule) {
    val context = LocalContext.current.applicationContext
    val targetSizePx = with(LocalDensity.current) { PROVIDER_ICON_SIZE.roundToPx() }
    val cacheKey = "${module.packageName}:${module.lastUpdateTime}:$targetSizePx"
    val icon by produceState(
        initialValue = LyricProviderManager.getCachedIcon(module, targetSizePx),
        key1 = cacheKey
    ) {
        if (value == null) {
            value = LyricProviderManager.loadIcon(
                context = context,
                module = module,
                targetSizePx = targetSizePx
            )
        }
    }

    val iconModifier = Modifier
        .size(PROVIDER_ICON_SIZE)
        .clip(PROVIDER_ICON_SHAPE)

    val loadedIcon = icon
    if (loadedIcon != null) {
        Image(
            bitmap = loadedIcon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
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

@Composable
private fun ProviderHeader(
    module: LyricModule,
    currentDelay: Int
) {
    val versionName = module.versionName ?: stringResource(id = R.string.unknown)
    val author = module.author ?: stringResource(id = R.string.unknown_author)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(BasicComponentDefaults.InsideMargin),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderIcon(module)
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = module.label,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(
                    id = R.string.format_version_author,
                    versionName,
                    author
                ),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = if (currentDelay > 0) "+$currentDelay ms" else "$currentDelay ms",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp
        )
    }
}
