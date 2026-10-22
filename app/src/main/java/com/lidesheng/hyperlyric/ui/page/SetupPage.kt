package com.lidesheng.hyperlyric.ui.page

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.LyricTypePreference
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.root.RootApplication
import com.lidesheng.hyperlyric.ui.utils.rememberExternalLinkOpener
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

private const val SETUP_PAGE_COUNT = 4

@Composable
fun SetupPage(onNavigateToMain: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { SETUP_PAGE_COUNT })
    val prefs =
        remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val onOpenLink = rememberExternalLinkOpener(snackbarHostState)

    var outputTarget by remember {
        mutableIntStateOf(LyricTypePreference.read(prefs))
    }
    var selectedSource by remember {
        mutableStateOf(
            prefs.getString(
                RootConstants.KEY_HOOK_LYRIC_SOURCE,
                RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
            ) ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
        )
    }

    val completeSetup = {
        prefs.edit { putBoolean(UIConstants.KEY_SETUP_COMPLETED, true) }
        onNavigateToMain()
    }
    val finishSetup = {
        if (RootApplication.xposedService != null) {
            PrefsBridge.putBoolean(RootConstants.KEY_HOOK_ENABLE, true)
        }
        completeSetup()
    }
    val pageTitle = when (pagerState.currentPage) {
        0 -> R.string.app_name
        1 -> R.string.title_disclaimer_notice
        2 -> R.string.setup_lyric_settings_title
        3 -> R.string.title_setup_complete
        else -> R.string.app_name
    }

    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            TopAppBar(title = stringResource(pageTitle))
        },
        bottomBar = {
            Column {
                SetupPageIndicator(
                    currentPage = pagerState.currentPage,
                    pageCount = SETUP_PAGE_COUNT
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 16.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isFirstPage = pagerState.currentPage == 0
                    val isLastPage = pagerState.currentPage == SETUP_PAGE_COUNT - 1

                    if (!isFirstPage) {
                        TextButton(
                            text = stringResource(R.string.back),
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TextButton(
                        text = stringResource(
                            if (isLastPage) R.string.setup_go_home else R.string.next
                        ),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            if (isLastPage) {
                                finishSetup()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        modifier = if (isFirstPage) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.weight(1f)
                        }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> SetupIntroPage()
                1 -> DisclaimerPage()
                2 -> LyricConfigurationPage(
                    outputTarget = outputTarget,
                    selectedSource = selectedSource,
                    onOutputTargetSelected = { target ->
                        outputTarget = target
                        PrefsBridge.putInt(RootConstants.KEY_HOOK_LYRIC_TYPE, target)
                    },
                    onSourceSelected = { source ->
                        selectedSource = source
                        PrefsBridge.putString(RootConstants.KEY_HOOK_LYRIC_SOURCE, source)
                    },
                    onOpenLink = onOpenLink
                )
                3 -> CompletionPage()
            }
        }
    }
}

@Composable
private fun SetupPageIndicator(currentPage: Int, pageCount: Int) {
    val selectedColor = if (MiuixTheme.colorScheme.background.luminance() < 0.5f) {
        Color.White
    } else {
        Color.Black
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) {
                            selectedColor
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun SetupIntroPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            clip = true
                            shape = RoundedCornerShape(24.dp)
                        }
                        .squircleClip(24.dp)
                        .background(colorResource(R.color.app_icon_red)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.5f)
                    )
                }
                Text(
                    text = stringResource(R.string.app_description),
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    style = MiuixTheme.textStyles.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DisclaimerPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.title_disclaimer),
                    summary = stringResource(R.string.summary_disclaimer)
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    BasicComponent(
                        title = stringResource(R.string.setup_app_list_permission_title),
                        summary = stringResource(R.string.setup_app_list_permission_summary)
                    )
                    BasicComponent(
                        title = stringResource(R.string.setup_root_permission_title),
                        summary = stringResource(R.string.setup_root_permission_summary)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricConfigurationPage(
    outputTarget: Int,
    selectedSource: String,
    onOutputTargetSelected: (Int) -> Unit,
    onSourceSelected: (String) -> Unit,
    onOpenLink: (String) -> Unit
) {
    val outputOptions = listOf(
        stringResource(R.string.option_lyric_type_super_island),
        stringResource(R.string.option_lyric_type_status_bar)
    )
    val sourceOptions = listOf(
        stringResource(R.string.lyric_source_lyricon),
        stringResource(R.string.lyric_source_superlyric),
        stringResource(R.string.lyric_source_lyricinfo)
    )
    val sourceIds = listOf("lyricon", "superlyric", "lyricinfo")
    val selectedSourceIndex = sourceIds.indexOf(selectedSource).coerceAtLeast(0)
    val sourceSummary = when (selectedSource) {
        "superlyric" -> stringResource(R.string.summary_help_source_superlyric)
        "lyricinfo" -> stringResource(R.string.summary_help_source_lyricinfo)
        else -> stringResource(R.string.summary_help_source_lyricon)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_lyric_type),
                    items = outputOptions,
                    selectedIndex = outputTarget.coerceIn(
                        RootConstants.LYRIC_TYPE_SUPER_ISLAND,
                        RootConstants.LYRIC_TYPE_STATUS_BAR
                    ),
                    onSelectedIndexChange = { index ->
                        onOutputTargetSelected(
                            index.coerceIn(
                                RootConstants.LYRIC_TYPE_SUPER_ISLAND,
                                RootConstants.LYRIC_TYPE_STATUS_BAR
                            )
                        )
                    }
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.title_lyric_source),
                    items = sourceOptions,
                    selectedIndex = selectedSourceIndex,
                    onSelectedIndexChange = { index ->
                        sourceIds.getOrNull(index)?.let(onSourceSelected)
                    }
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = sourceOptions[selectedSourceIndex],
                    summary = sourceSummary
                )
            }
        }
        item {
            when (selectedSource) {
                "lyricon" -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            ArrowPreference(
                                title = stringResource(R.string.setup_download_lyric_core),
                                summary = stringResource(R.string.setup_download_lyric_core_summary),
                                onClick = {
                                    onOpenLink("https://github.com/tomakino/lyricon/releases/tag/core")
                                }
                            )
                            ArrowPreference(
                                title = stringResource(R.string.setup_download_provider),
                                summary = stringResource(R.string.setup_download_provider_summary),
                                onClick = {
                                    onOpenLink("https://github.com/proify/LyricProvider/releases")
                                }
                            )
                        }
                    }
                }

                "superlyric" -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.setup_download_superlyric),
                            summary = stringResource(R.string.setup_download_superlyric_summary),
                            onClick = {
                                onOpenLink("https://github.com/HChenX/SuperLyric")
                            }
                        )
                    }
                }

                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        ArrowPreference(
                            title = stringResource(R.string.tag_download_lyricinfo),
                            summary = stringResource(R.string.setup_no_dependency_summary),
                            onClick = {
                                onOpenLink("https://github.com/limczhh/LyricInfo")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionPage() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}
