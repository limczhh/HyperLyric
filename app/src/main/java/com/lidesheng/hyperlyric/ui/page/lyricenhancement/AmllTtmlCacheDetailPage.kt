package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.LyricEnhancementConstants
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
import top.yukonga.miuix.kmp.basic.HorizontalDivider
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AmllTtmlCacheDetailPage(route: Route.AmllTtmlCacheDetail) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val sender = remember(context) { LyricEnhancementCacheCommandSender(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val unavailableText = stringResource(R.string.toast_lyric_enhancement_cache_unavailable)
    val failedText = stringResource(R.string.toast_lyric_enhancement_cache_failed)
    val deleteText = stringResource(R.string.title_lyric_enhancement_cache_delete_entry)
    val emptyDetailText = stringResource(R.string.lyric_enhancement_cache_detail_empty)
    var requestInFlight by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun runDeleteEntry() {
        showDeleteDialog = false
        if (requestInFlight) return
        requestInFlight = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                sender.clearEntry(
                    featureId = LyricEnhancementConstants.AMLL_TTML_FEATURE_ID,
                    entryId = route.entryId
                )
            }
            requestInFlight = false
            if (outcome is LyricEnhancementCacheOperationOutcome.Completed &&
                outcome.response.success
            ) {
                // The list page owns the snackbar because this page is popped immediately.
                AmllTtmlCacheEntriesVersion.version++
                navigator.pop()
            } else {
                snackbarHostState.showSnackbar(
                    if (outcome is LyricEnhancementCacheOperationOutcome.Unavailable) {
                        unavailableText
                    } else {
                        failedText
                    },
                    duration = SnackbarDuration.Custom(3000L)
                )
            }
        }
    }

    if (showDeleteDialog) {
        WindowDialog(
            title = deleteText,
            show = true,
            onDismissRequest = { showDeleteDialog = false }
        ) {
            CacheConfirmContent(
                message = stringResource(
                    R.string.dialog_lyric_enhancement_cache_clear_entry_summary,
                    route.title
                ),
                confirmText = stringResource(R.string.confirm),
                cancelText = stringResource(R.string.cancel),
                onConfirm = ::runDeleteEntry,
                onDismiss = { showDeleteDialog = false }
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
                    title = route.title.ifBlank {
                        stringResource(R.string.title_amll_ttml_cache_detail)
                    },
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
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !requestInFlight
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = deleteText
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        )
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding
            ) {
                val metaSummary = formatCacheEntryMeta(route.sizeBytes, route.updatedAtEpochMs)
                if (metaSummary != null) {
                    item(key = "cache_meta") {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                        ) {
                            BasicComponent(title = metaSummary)
                        }
                    }
                }
                item(key = "cache_details") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        if (route.details.isEmpty()) {
                            BasicComponent(title = emptyDetailText)
                        } else {
                            route.details.forEachIndexed { index, line ->
                                DetailLine(
                                    label = line.label,
                                    value = line.value,
                                    showDivider = index > 0
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, showDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 2.dp),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

internal fun formatCacheEntryMeta(
    sizeBytes: Long?,
    updatedAtEpochMs: Long?,
): String? {
    val parts = buildList {
        sizeBytes?.takeIf { it >= 0L }?.let {
            add(if (it < 1024L) "$it B" else "${it / 1024L} KB")
        }
        updatedAtEpochMs?.takeIf { it > 0L }?.let { updatedAt ->
            add(
                DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT
                ).format(Date(updatedAt))
            )
        }
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
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
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.error
                )
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
