@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.lidesheng.hyperlyric.plugin.api.PluginCacheEntry
import com.lidesheng.hyperlyric.plugin.app.InstalledPlugin
import com.lidesheng.hyperlyric.plugin.app.PluginCacheOperationOutcome
import com.lidesheng.hyperlyric.plugin.app.PluginRepository
import com.lidesheng.hyperlyric.plugin.core.PluginCacheScope
import com.lidesheng.hyperlyric.root.utils.ShellUtils
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun PluginCachePage(pluginId: String, scopeId: String) {
    val context = LocalContext.current
    val repository = remember { PluginRepository(context) }
    val plugin = remember(pluginId) {
        repository.listInstalled().firstOrNull { it.manifest.id == pluginId }
    }
    val cacheScope = remember(pluginId, scopeId, plugin) {
        plugin?.manifest?.cacheScopes?.firstOrNull { it.id == scopeId }
    }
    if (plugin == null || cacheScope == null) {
        PluginCacheUnavailablePage()
        return
    }
    PluginCachePageContent(plugin, cacheScope, repository)
}

@Composable
private fun PluginCacheUnavailablePage() {
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_plugin_cache),
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            BasicComponent(title = stringResource(R.string.plugin_cache_unavailable))
        }
    }
}

@Composable
private fun PluginCachePageContent(
    plugin: InstalledPlugin,
    cacheScope: PluginCacheScope,
    repository: PluginRepository,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val title = cacheScope.localizedTitle(context)
    val loadingText = stringResource(R.string.plugin_cache_loading)
    val emptyText = stringResource(R.string.plugin_cache_empty)
    val waitingText = stringResource(R.string.plugin_cache_waiting)
    val unavailableText = stringResource(R.string.plugin_cache_unavailable)
    val retryText = stringResource(R.string.plugin_cache_retry)
    val clearAllText = stringResource(R.string.title_plugin_cache_clear_all)
    val clearAllSuccess = stringResource(R.string.toast_plugin_cache_cleared)
    val clearAllConfirm = stringResource(R.string.dialog_plugin_cache_clear_all_summary)
    val deleteSuccess = stringResource(R.string.toast_plugin_cache_entry_cleared)
    val rootQueryText = stringResource(R.string.plugin_cache_root_query)
    val rootUnavailableText = stringResource(R.string.plugin_cache_root_unavailable)
    val rootResultTemplate = stringResource(R.string.plugin_cache_root_result)
    val rootFileTemplate = stringResource(R.string.plugin_cache_root_file)
    val rootLegacyFileTemplate = stringResource(R.string.plugin_cache_root_legacy_file)
    var state by remember(plugin.manifest.id, cacheScope.id) {
        mutableStateOf<PluginCachePageState>(PluginCachePageState.Loading)
    }
    var requestInFlight by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    fun describeFailure(reason: String?): String = when (reason) {
        "plugin_not_loaded" -> unavailableText
        "scope_not_declared", "scope_not_loaded", "plugin_not_installed" -> unavailableText
        "system_ui_not_responding", "xposed_service_unavailable", "request_write_failed",
        "request_queue_full", "request_interrupted" -> waitingText
        else -> unavailableText
    }

    fun publishFailure(reason: String?) {
        state = PluginCachePageState.Failure(describeFailure(reason))
    }

    fun loadEntries() {
        if (requestInFlight) return
        requestInFlight = true
        state = PluginCachePageState.Loading
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repository.listPluginCache(plugin.manifest.id, cacheScope.id)
            }
            requestInFlight = false
            when (outcome) {
                is PluginCacheOperationOutcome.Completed -> {
                    if (outcome.response.success) {
                        state = outcome.response.entries
                            .takeIf { it.isNotEmpty() }
                            ?.let(PluginCachePageState::Entries)
                            ?: PluginCachePageState.Empty
                    } else {
                        publishFailure(outcome.response.errorCode)
                    }
                }

                is PluginCacheOperationOutcome.Waiting -> {
                    state = PluginCachePageState.Waiting(
                        message = describeFailure(outcome.reason),
                        canInspectWithRoot = outcome.reason in ROOT_QUERY_REASONS
                    )
                }

                is PluginCacheOperationOutcome.Rejected -> publishFailure(outcome.reason)
            }
        }
    }

    fun runClearAll() {
        showClearAllDialog = false
        if (requestInFlight) return
        requestInFlight = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repository.clearPluginCache(plugin.manifest.id, cacheScope.id)
            }
            requestInFlight = false
            when (outcome) {
                is PluginCacheOperationOutcome.Completed -> {
                    if (outcome.response.success) {
                        state = PluginCachePageState.Empty
                        snackbarHostState.showSnackbar(
                            clearAllSuccess,
                            duration = SnackbarDuration.Custom(2500L)
                        )
                    } else {
                        publishFailure(outcome.response.errorCode)
                    }
                }

                is PluginCacheOperationOutcome.Waiting -> {
                    state = PluginCachePageState.Waiting(
                        message = describeFailure(outcome.reason),
                        canInspectWithRoot = outcome.reason in ROOT_QUERY_REASONS
                    )
                }

                is PluginCacheOperationOutcome.Rejected -> publishFailure(outcome.reason)
            }
        }
    }

    fun queryFilesWithRoot() {
        if (requestInFlight) return
        requestInFlight = true
        state = PluginCachePageState.Loading
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.queryPluginCacheFilesWithRoot(plugin.manifest.id)
            }
            requestInFlight = false
            state = when (result) {
                is ShellUtils.RootPluginCacheQuery.Available -> {
                    PluginCachePageState.RootFiles(result.files)
                }

                ShellUtils.RootPluginCacheQuery.InvalidPluginId,
                ShellUtils.RootPluginCacheQuery.RootUnavailable -> PluginCachePageState.Failure(
                    rootUnavailableText
                )
            }
        }
    }

    // 本页在详情页之下保持组合（miuix 可见窗口 opaqueDepth=1），LaunchedEffect 的 key
    // 不变不会自动重跑；version 是删除后刷新的唯一触发信号。首见 version 记录后，
    // 变化即代表详情页删除成功返回，刷新同时显示成功提示
    var seenEntriesVersion by remember(plugin.manifest.id, cacheScope.id) {
        mutableStateOf(PluginCacheEntriesVersion.version)
    }
    LaunchedEffect(
        plugin.manifest.id,
        cacheScope.id,
        PluginCacheEntriesVersion.version
    ) {
        val deletedSinceLastLoad = PluginCacheEntriesVersion.version != seenEntriesVersion
        seenEntriesVersion = PluginCacheEntriesVersion.version
        loadEntries()
        if (deletedSinceLastLoad) {
            snackbarHostState.showSnackbar(
                deleteSuccess,
                duration = SnackbarDuration.Custom(2500L)
            )
        }
    }

    if (showClearAllDialog) {
        WindowDialog(
            title = clearAllText,
            show = true,
            onDismissRequest = { showClearAllDialog = false }
        ) {
            CacheConfirmContent(
                message = clearAllConfirm,
                confirmText = stringResource(R.string.confirm),
                cancelText = stringResource(R.string.cancel),
                onConfirm = ::runClearAll,
                onDismiss = { showClearAllDialog = false }
            )
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = title,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        if (state is PluginCachePageState.Entries && !requestInFlight) {
                            IconButton(onClick = { showClearAllDialog = true }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = clearAllText
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        )
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding
            ) {
                if (requestInFlight && state !is PluginCachePageState.Loading) {
                    cacheStatusItem("cache_operation_in_flight", loadingText)
                }
                when (val current = state) {
                    PluginCachePageState.Loading -> cacheStatusItem("cache_loading", loadingText)
                    PluginCachePageState.Empty -> cacheStatusItem("cache_empty", emptyText)
                    is PluginCachePageState.Waiting -> {
                        cacheStatusItem(
                            "cache_waiting",
                            "${current.message} · $retryText"
                        ) {
                            loadEntries()
                        }
                        if (current.canInspectWithRoot) {
                            cacheStatusItem("cache_root_query", rootQueryText) {
                                queryFilesWithRoot()
                            }
                        }
                    }

                    is PluginCachePageState.Failure -> cacheStatusItem(
                        "cache_failure",
                        "${current.message} · $retryText"
                    ) {
                        loadEntries()
                    }

                    is PluginCachePageState.Entries -> {
                        item(key = "cache_entries") {
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                                    .fillMaxWidth()
                            ) {
                                current.entries.forEach { entry ->
                                    ArrowPreference(
                                        title = entry.title,
                                        summary = entry.summary
                                            ?: formatCacheEntryMeta(
                                                entry.sizeBytes,
                                                entry.updatedAtEpochMs
                                            ),
                                        enabled = !requestInFlight,
                                        onClick = {
                                            navigator.navigate(
                                                Route.PluginCacheDetail(
                                                    pluginId = plugin.manifest.id,
                                                    scopeId = cacheScope.id,
                                                    entryId = entry.id,
                                                    title = entry.title,
                                                    summary = entry.summary,
                                                    sizeBytes = entry.sizeBytes,
                                                    updatedAtEpochMs = entry.updatedAtEpochMs,
                                                    details = entry.details.map {
                                                        Route.CacheDetailLine(it.label, it.value)
                                                    }
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    is PluginCachePageState.RootFiles -> {
                        cacheStatusItem(
                            "cache_root_result",
                            rootResultTemplate.format(current.files.size)
                        )
                        if (current.files.isNotEmpty()) {
                            item(key = "cache_root_files") {
                                Card(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 12.dp)
                                        .fillMaxWidth()
                                ) {
                                    current.files.forEachIndexed { index, file ->
                                        ArrowPreference(
                                            title = (if (file.legacyPreferences) {
                                                rootLegacyFileTemplate
                                            } else {
                                                rootFileTemplate
                                            }).format(index + 1),
                                            summary = "${file.absolutePath}\n${file.sizeBytes / 1024} KB",
                                            enabled = false,
                                            onClick = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                trackPadding = contentPadding
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cacheStatusItem(
    key: String,
    message: String,
    retry: (() -> Unit)? = null,
) {
    item(key = key) {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            if (retry == null) {
                BasicComponent(title = message)
            } else {
                ArrowPreference(title = message, onClick = retry)
            }
        }
    }
}

@Composable
internal fun CacheConfirmContent(
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = message, modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error)
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = cancelText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private sealed interface PluginCachePageState {
    data object Loading : PluginCachePageState
    data object Empty : PluginCachePageState
    data class Waiting(
        val message: String,
        val canInspectWithRoot: Boolean = false,
    ) : PluginCachePageState
    data class Failure(val message: String) : PluginCachePageState
    data class Entries(val entries: List<PluginCacheEntry>) : PluginCachePageState
    data class RootFiles(val files: List<ShellUtils.RootPluginCacheFile>) : PluginCachePageState
}

private val ROOT_QUERY_REASONS = setOf(
    "system_ui_not_responding",
    "xposed_service_unavailable",
    "request_write_failed",
    "request_interrupted"
)

/** 详情页删除缓存条目后递增，缓存列表页据此刷新（宿主进程内信号，不跨进程） */
internal object PluginCacheEntriesVersion {
    var version by mutableStateOf(0)
}
