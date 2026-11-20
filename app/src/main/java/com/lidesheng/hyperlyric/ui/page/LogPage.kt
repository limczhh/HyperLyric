@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.page.log.LogEntry
import com.lidesheng.hyperlyric.ui.page.log.LogTabContent
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import com.lidesheng.hyperlyric.utils.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import com.lidesheng.hyperlyric.ui.utils.LocalGlassStyle
import com.lidesheng.hyperlyric.ui.utils.AdaptiveIconButton
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTabRow
import com.lidesheng.hyperlyric.ui.utils.rememberNavPagerState
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import com.lidesheng.hyperlyric.ui.utils.AdaptiveTopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun LogPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs =
        listOf(stringResource(R.string.title_app_logs), stringResource(R.string.title_module_logs))
    val pagerState = rememberPagerState { tabs.size }
    val navPagerState = rememberNavPagerState(pagerState)
    val isAppTab = navPagerState.selectedIndex == 0
    val appListState = rememberLazyListState()
    val moduleListState = rememberLazyListState()

    // 应用日志状态
    val appLogs = remember { mutableStateListOf<LogEntry>() }
    var appIsLoading by remember { mutableStateOf(true) }
    var appHasLoaded by remember { mutableStateOf(false) }
    var appSelectedLevel by remember { mutableStateOf("ALL") }

    // 模块日志状态
    val moduleLogs = remember { mutableStateListOf<LogEntry>() }
    var moduleIsLoading by remember { mutableStateOf(true) }
    var moduleHasLoaded by remember { mutableStateOf(false) }
    var moduleSelectedLevel by remember { mutableStateOf("ALL") }

    var showMorePopup by remember { mutableStateOf(false) }
    val moreMenuAnchor = rememberGlassPopupAnchor()
    val glassStyle = LocalGlassStyle.current
    var showClearDialog by remember { mutableStateOf(false) }

    val copiedMsg = stringResource(R.string.copied)
    val exportHeader = stringResource(R.string.export_header)
    val exportTimeFormat = stringResource(R.string.format_export_time)
    val exportSuccessMsg = stringResource(R.string.export_success)
    val exportFailedMsg = stringResource(R.string.format_export_failed)

    val reloadAppLogs = remember {
        {
            coroutineScope.launch {
                appIsLoading = true
                val startTime = System.currentTimeMillis()
                val logs = LogManager.readAppLogs()
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 300) kotlinx.coroutines.delay(300 - elapsed)
                appLogs.clear()
                appLogs.addAll(logs)
                appHasLoaded = true
                appIsLoading = false
            }
        }
    }

    val reloadModuleLogs = remember {
        {
            coroutineScope.launch {
                moduleIsLoading = true
                val startTime = System.currentTimeMillis()
                val logs = LogManager.readModuleLogs(context)
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 300) kotlinx.coroutines.delay(300 - elapsed)
                moduleLogs.clear()
                moduleLogs.addAll(logs)
                moduleHasLoaded = true
                moduleIsLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadAppLogs()
        reloadModuleLogs()
    }

    val filteredAppLogs by remember {
        derivedStateOf {
            if (appSelectedLevel == "ALL") appLogs
            else appLogs.filter { it.level == appSelectedLevel }.distinct()
        }
    }

    val filteredModuleLogs by remember {
        derivedStateOf {
            if (moduleSelectedLevel == "ALL") moduleLogs
            else moduleLogs.filter { it.level == moduleSelectedLevel }.distinct()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val sb = StringBuilder()
                    sb.appendLine(exportHeader)
                    sb.appendLine(
                        String.format(
                            exportTimeFormat,
                            LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        )
                    )
                    sb.appendLine()
                    val logsToExport =
                        if (isAppTab) filteredAppLogs.toList() else filteredModuleLogs.toList()
                    logsToExport.forEach {
                        sb.appendLine(it.rawLog)
                        sb.appendLine()
                    }
                    val output = context.contentResolver.openOutputStream(uri)
                    if (output != null) {
                        output.use {
                            it.write(sb.toString().toByteArray(Charsets.UTF_8))
                            it.flush()
                        }
                        withContext(Dispatchers.Main) {
                            snackbarHostState.showSnackbar(
                                message = exportSuccessMsg,
                                duration = SnackbarDuration.Custom(2000L)
                            )
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(
                            message = String.format(exportFailedMsg, e.message),
                            duration = SnackbarDuration.Custom(2000L)
                        )
                    }
                }
            }
        }
    )

    val filterLabel = stringResource(R.string.module_logs_level)
    val exportLabel = stringResource(R.string.export_all)
    val clearLabel = stringResource(R.string.clear_logs)
    val allLabel = stringResource(R.string.all)
    val levelDebug = stringResource(R.string.level_debug)
    val levelInfo = stringResource(R.string.level_info)
    val levelWarn = stringResource(R.string.level_warn)
    val levelError = stringResource(R.string.level_error)
    val levelCrash = stringResource(R.string.level_crash)

    val currentSelectedLevel = if (isAppTab) appSelectedLevel else moduleSelectedLevel
    val logEntries = remember(
        currentSelectedLevel,
        isAppTab,
        filterLabel,
        exportLabel,
        clearLabel,
        allLabel,
        levelDebug,
        levelInfo,
        levelWarn,
        levelError,
        levelCrash
    ) {
        val levels = listOf("ALL", "D", "I", "W", "E", "C")
        val levelNames = listOf(allLabel, levelDebug, levelInfo, levelWarn, levelError, levelCrash)
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = filterLabel,
                        children = levels.mapIndexed { index, level ->
                            DropdownItem(
                                text = levelNames[index],
                                selected = currentSelectedLevel == level,
                                onClick = {
                                    if (isAppTab) appSelectedLevel =
                                        level else moduleSelectedLevel = level
                                }
                            )
                        }
                    )
                )
            ),
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = exportLabel,
                        onClick = {
                            val dateTime = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                            val prefix = if (isAppTab) "hyperlyric_app" else "hyperlyric_module"
                            exportLauncher.launch("${prefix}_logs_$dateTime.txt")
                        }
                    ),
                    DropdownItem(
                        text = clearLabel,
                        onClick = {
                            showClearDialog = true
                        }
                    )
                )
            )
        )
    }

    val activeListState = if (isAppTab) appListState else moduleListState
    val visualContentOffset: () -> Float = remember(activeListState, topAppBarScrollBehavior) {
        {
            if (activeListState.firstVisibleItemIndex > 0) {
                -1000f
            } else if (activeListState.firstVisibleItemScrollOffset > 0) {
                -activeListState.firstVisibleItemScrollOffset.toFloat() + topAppBarScrollBehavior.state.overscrollOffset
            } else {
                topAppBarScrollBehavior.state.overscrollOffset
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_view_logs),
                    scrollBehavior = topAppBarScrollBehavior,
                    visualContentOffset = visualContentOffset,
                    navigationIcon = {
                        AdaptiveIconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (glassStyle) {
                            GlassIconButton(
                                onClick = { showMorePopup = !showMorePopup },
                                modifier = Modifier.glassPopupAnchor(
                                    anchor = moreMenuAnchor,
                                    cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2,
                                    floating = true,
                                ),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = stringResource(R.string.more),
                                    modifier = Modifier.glassPopupAnchorContent(moreMenuAnchor),
                                )
                            }
                        } else {
                            OverlayIconCascadingDropdownMenu(
                                entries = logEntries,
                                collapseOnSelection = true,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = stringResource(R.string.more),
                                )
                            }
                        }
                    },
                    bottomContent = {
                        AdaptiveTabRow(
                            tabs = tabs,
                            selectedTabIndex = navPagerState.selectedIndex,
                            onTabSelected = { index ->
                                navPagerState.animateToPage(index)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                            colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)
                        )
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            HorizontalPager(
                state = pagerState,
                overscrollEffect = null,
                verticalAlignment = Alignment.Top,
                beyondViewportPageCount = 1
            ) { page ->
                val isApp = page == 0
                val currentLogs = if (isApp) filteredAppLogs else filteredModuleLogs
                val currentLoading = if (isApp) appIsLoading else moduleIsLoading
                val currentHasLoaded = if (isApp) appHasLoaded else moduleHasLoaded
                val top = padding.calculateTopPadding()
                val bottom = padding.calculateBottomPadding()
                val contentPadding = pageContentPadding(padding, extraBottom = 16.dp)

                LogTabContent(
                    logs = currentLogs,
                    isInitialLoading = currentLoading && !currentHasLoaded,
                    isRefreshing = currentLoading && currentHasLoaded,
                    onRefresh = { if (isApp) reloadAppLogs() else reloadModuleLogs() },
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    lazyListState = if (isApp) appListState else moduleListState,
                    contentPadding = contentPadding,
                    copiedMsg = copiedMsg,
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }

    if (glassStyle) {
        GlassIconDropdownMenu(
            show = showMorePopup,
            onDismissRequest = { showMorePopup = false },
            anchor = moreMenuAnchor,
            anchorContent = {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = null,
                )
            },
            entries = logEntries,
            backdrop = backdrop,
            collapseOnSelection = true,
        )
    }
    }

    SimpleDialog(
        show = showClearDialog,
        title = stringResource(R.string.clear_logs),
        summary = stringResource(R.string.clear_logs_confirm),
        onDismiss = { showClearDialog = false },
        onConfirm = {
            LogManager.clearLogs()
            reloadAppLogs()
        }
    )
}
