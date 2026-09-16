package com.lidesheng.hyperlyric.ui.page.lyricenhancement

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.common.LyricEnhancementConstants
import com.lidesheng.hyperlyric.ui.component.SimpleDialog
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.XposedLyricSettingPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun AiTranslationCachePage() {
    val context = LocalContext.current
    val sender = remember(context) { LyricEnhancementCacheCommandSender(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val loadingText = stringResource(R.string.lyric_enhancement_cache_loading)
    val emptyText = stringResource(R.string.lyric_enhancement_cache_empty)
    val failedText = stringResource(R.string.toast_lyric_enhancement_cache_failed)
    val unavailableText = stringResource(R.string.toast_lyric_enhancement_cache_unavailable)
    val retryText = stringResource(R.string.lyric_enhancement_cache_retry)
    val deleteEntryText = stringResource(R.string.title_lyric_enhancement_cache_delete_entry)
    val deleteAllText = stringResource(R.string.title_lyric_enhancement_cache_clear_all)
    val deleteSuccessText = stringResource(R.string.toast_lyric_enhancement_cache_entry_cleared)
    val deleteAllSuccessText = stringResource(
        R.string.toast_lyric_enhancement_cache_all_cleared
    )

    var state by remember {
        mutableStateOf<AiTranslationCachePageState>(AiTranslationCachePageState.Loading)
    }
    var busy by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<LyricEnhancementCacheEntry?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    fun loadEntries() {
        if (busy) return
        busy = true
        state = AiTranslationCachePageState.Loading
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                sender.listEntries(LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID)
            }
            busy = false
            state = when (outcome) {
                is LyricEnhancementCacheOperationOutcome.Completed -> {
                    if (outcome.response.success) {
                        outcome.response.entries.takeIf { it.isNotEmpty() }
                            ?.let(AiTranslationCachePageState::Entries)
                            ?: AiTranslationCachePageState.Empty
                    } else {
                        AiTranslationCachePageState.Failure(unavailableText)
                    }
                }

                is LyricEnhancementCacheOperationOutcome.Unavailable ->
                    AiTranslationCachePageState.Failure(unavailableText)

                is LyricEnhancementCacheOperationOutcome.Failed ->
                    AiTranslationCachePageState.Failure(failedText)
            }
        }
    }

    fun deleteEntry(entry: LyricEnhancementCacheEntry) {
        if (busy) return
        selectedEntry = null
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                sender.clearEntry(
                    featureId = LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID,
                    entryId = entry.id
                )
            }
            busy = false
            if (outcome is LyricEnhancementCacheOperationOutcome.Completed &&
                outcome.response.success
            ) {
                val current = state
                state = if (current is AiTranslationCachePageState.Entries) {
                    current.entries.filterNot { it.id == entry.id }
                        .takeIf { it.isNotEmpty() }
                        ?.let(AiTranslationCachePageState::Entries)
                        ?: AiTranslationCachePageState.Empty
                } else {
                    current
                }
                snackbarHostState.showSnackbar(
                    deleteSuccessText,
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

    fun clearAll() {
        if (busy) return
        showClearAllDialog = false
        busy = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                sender.clearAll(LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID)
            }
            busy = false
            if (outcome is LyricEnhancementCacheOperationOutcome.Completed &&
                outcome.response.success
            ) {
                state = AiTranslationCachePageState.Empty
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

    LaunchedEffect(Unit) { loadEntries() }

    XposedLyricSettingPage(
        title = stringResource(R.string.title_ai_translation_cache),
        snackbarHostState = snackbarHostState,
        topBarActions = {
            IconButton(
                onClick = { showClearAllDialog = true },
                enabled = !busy
            ) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    ) {
        item(key = "ai_cache_description") {
            SmallTitle(text = stringResource(R.string.title_lyric_enhancement_cache))
        }
        when (val current = state) {
            AiTranslationCachePageState.Loading -> cacheStatusItem("loading", loadingText)
            AiTranslationCachePageState.Empty -> cacheStatusItem("empty", emptyText)
            is AiTranslationCachePageState.Failure -> {
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
                            onClick = ::loadEntries
                        )
                    }
                }
            }

            is AiTranslationCachePageState.Entries -> {
                items(current.entries, key = { it.id }) { entry ->
                    itemCard(entry, busy) { selectedEntry = entry }
                }
            }
        }
    }

    val entry = selectedEntry
    val entrySummary = if (entry == null) {
        null
    } else {
        stringResource(
            R.string.dialog_lyric_enhancement_cache_clear_entry_summary,
            entry.title
        )
    }
    SimpleDialog(
        show = entry != null,
        title = deleteEntryText,
        summary = entrySummary,
        onDismiss = { selectedEntry = null },
        onConfirm = { entry?.let(::deleteEntry) }
    )

    SimpleDialog(
        show = showClearAllDialog,
        title = deleteAllText,
        summary = stringResource(R.string.dialog_lyric_enhancement_cache_clear_all_summary),
        onDismiss = { showClearAllDialog = false },
        onConfirm = ::clearAll
    )
}

@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.itemCard(
    entry: LyricEnhancementCacheEntry,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
    ) {
        ArrowPreference(
            title = entry.title,
            summary = entry.artist ?: stringResource(R.string.lyric_enhancement_cache_unknown_artist),
            enabled = !busy,
            holdDownState = false,
            onClick = onClick
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.cacheStatusItem(
    key: String,
    message: String,
) {
    item(key = key) {
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

private sealed interface AiTranslationCachePageState {
    data object Loading : AiTranslationCachePageState
    data object Empty : AiTranslationCachePageState
    data class Entries(val entries: List<LyricEnhancementCacheEntry>) : AiTranslationCachePageState
    data class Failure(val message: String) : AiTranslationCachePageState
}
