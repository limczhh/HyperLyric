package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.common.LyricEnhancementConstants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun AmllTtmlCachePage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val sender = remember(context) { LyricEnhancementCacheCommandSender(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val loadingText = stringResource(R.string.lyric_enhancement_cache_loading)
    val emptyText = stringResource(R.string.lyric_enhancement_cache_empty)
    val failedText = stringResource(R.string.toast_lyric_enhancement_cache_failed)
    val unavailableText = stringResource(R.string.toast_lyric_enhancement_cache_unavailable)
    val retryText = stringResource(R.string.lyric_enhancement_cache_retry)
    val clearAllText = stringResource(R.string.title_lyric_enhancement_cache_clear_all)
    val deleteSuccessText = stringResource(R.string.toast_lyric_enhancement_cache_entry_cleared)
    val deleteAllSuccessText = stringResource(
        R.string.toast_lyric_enhancement_cache_all_cleared
    )

    var state by remember {
        mutableStateOf<AmllTtmlCachePageState>(AmllTtmlCachePageState.Loading)
    }
    var busy by remember { mutableStateOf(false) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var seenEntriesVersion by remember { mutableStateOf(AmllTtmlCacheEntriesVersion.version) }

    fun loadEntries(refresh: Boolean = false) {
        if (busy) return
        busy = true
        if (refresh) {
            isRefreshing = true
        } else {
            state = AmllTtmlCachePageState.Loading
        }
        scope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    sender.listEntries(LyricEnhancementConstants.AMLL_TTML_FEATURE_ID)
                }
                state = when (outcome) {
                    is LyricEnhancementCacheOperationOutcome.Completed -> {
                        if (outcome.response.success) {
                            outcome.response.entries.takeIf { it.isNotEmpty() }
                                ?.let(AmllTtmlCachePageState::Entries)
                                ?: AmllTtmlCachePageState.Empty
                        } else {
                            AmllTtmlCachePageState.Failure(unavailableText)
                        }
                    }

                    is LyricEnhancementCacheOperationOutcome.Unavailable ->
                        AmllTtmlCachePageState.Failure(unavailableText)

                    is LyricEnhancementCacheOperationOutcome.Failed ->
                        AmllTtmlCachePageState.Failure(failedText)
                }
                initialLoadCompleted = true
            } finally {
                busy = false
                isRefreshing = false
            }
        }
    }

    fun clearAll() {
        if (busy) return
        showClearAllDialog = false
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                sender.clearAll(LyricEnhancementConstants.AMLL_TTML_FEATURE_ID)
            }
            busy = false
            if (outcome is LyricEnhancementCacheOperationOutcome.Completed &&
                outcome.response.success
            ) {
                state = AmllTtmlCachePageState.Empty
                snackbarHostState.showSnackbar(
                    deleteAllSuccessText,
                    duration = SnackbarDuration.Custom(2500L)
                )
            } else {
                snackbarHostState.showSnackbar(
                    if (outcome is LyricEnhancementCacheOperationOutcome.Unavailable) {
                        unavailableText
                    } else {
                        failedText
                    },
                    duration = SnackbarDuration.Custom(2500L)
                )
            }
        }
    }

    LaunchedEffect(AmllTtmlCacheEntriesVersion.version) {
        val deletedSinceLastLoad =
            AmllTtmlCacheEntriesVersion.version != seenEntriesVersion
        seenEntriesVersion = AmllTtmlCacheEntriesVersion.version
        loadEntries(refresh = deletedSinceLastLoad)
        if (deletedSinceLastLoad) {
            snackbarHostState.showSnackbar(
                deleteSuccessText,
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
                message = stringResource(
                    R.string.dialog_lyric_enhancement_cache_clear_all_summary
                ),
                confirmText = stringResource(R.string.confirm),
                cancelText = stringResource(R.string.cancel),
                onConfirm = ::clearAll,
                onDismiss = { showClearAllDialog = false }
            )
        }
    }

    XposedLyricSettingPage(
        title = stringResource(R.string.title_amll_ttml_cache),
        snackbarHostState = snackbarHostState,
        isInitialLoading = !initialLoadCompleted && state is AmllTtmlCachePageState.Loading,
        isRefreshing = isRefreshing,
        onRefresh = { loadEntries(refresh = true) },
        topBarActions = {
            IconButton(
                onClick = { showClearAllDialog = true },
                enabled = !busy
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = clearAllText
                )
            }
        }
    ) {
        when (val current = state) {
            AmllTtmlCachePageState.Loading -> cacheStatusItem("loading", loadingText, true)
            AmllTtmlCachePageState.Empty -> cacheStatusItem("empty", emptyText)
            is AmllTtmlCachePageState.Failure -> {
                cacheStatusItem("failure", current.message)
                item(key = "retry") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        ArrowPreference(
                            title = retryText,
                            enabled = !busy,
                            onClick = { loadEntries() }
                        )
                    }
                }
            }

            is AmllTtmlCachePageState.Entries -> {
                items(current.entries, key = { it.id }) { entry ->
                    itemCard(entry, busy) {
                        navigator.navigate(
                            Route.AmllTtmlCacheDetail(
                                entryId = entry.id,
                                title = entry.title,
                                artist = entry.artist,
                                sizeBytes = entry.sizeBytes,
                                updatedAtEpochMs = entry.updatedAtEpochMs,
                                details = entry.details.map {
                                    Route.CacheDetailLine(it.label, it.value)
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.itemCard(
    entry: LyricEnhancementCacheEntry,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val summary = entry.artist?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.lyric_enhancement_cache_unknown_artist)
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
    ) {
        ArrowPreference(
            title = entry.title,
            summary = summary,
            enabled = !busy,
            holdDownState = false,
            onClick = onClick
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cacheStatusItem(
    key: String,
    message: String,
    loading: Boolean = false,
) {
    item(key = key) {
        if (loading) {
            Box(
                modifier = Modifier.fillParentMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
            ) {
                BasicComponent(title = message)
            }
        }
    }
}

private sealed interface AmllTtmlCachePageState {
    data object Loading : AmllTtmlCachePageState
    data object Empty : AmllTtmlCachePageState
    data class Entries(val entries: List<LyricEnhancementCacheEntry>) : AmllTtmlCachePageState
    data class Failure(val message: String) : AmllTtmlCachePageState
}

/** 详情页删除单条缓存后递增，列表页据此刷新并显示成功提示。 */
internal object AmllTtmlCacheEntriesVersion {
    var version by mutableStateOf(0)
}
