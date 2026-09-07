@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.plugin.app.PluginCacheOperationOutcome
import com.lidesheng.hyperlyric.plugin.app.PluginRepository
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.DateFormat
import java.util.Date

/**
 * 插件缓存条目详情页：展示缓存条目携带的可读元数据（PluginCacheEntry.details），
 * 并承载单条删除交互（顶栏删除图标 + 确认对话框）。无详情的条目显示占位文案。
 */
@Composable
fun PluginCacheDetailPage(route: Route.PluginCacheDetail) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val repository = remember { PluginRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val waitingText = stringResource(R.string.plugin_cache_waiting)
    val unavailableText = stringResource(R.string.plugin_cache_unavailable)
    val deleteText = stringResource(R.string.title_plugin_cache_clear_entry)
    val deleteConfirm = stringResource(R.string.dialog_plugin_cache_clear_entry_summary)
    val emptyDetailText = stringResource(R.string.plugin_cache_detail_empty)
    var requestInFlight by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun describeFailure(reason: String?): String = when (reason) {
        "plugin_not_loaded",
        "scope_not_declared",
        "scope_not_loaded",
        "plugin_not_installed" -> unavailableText

        "system_ui_not_responding",
        "xposed_service_unavailable",
        "request_write_failed",
        "request_queue_full",
        "request_interrupted" -> waitingText

        else -> unavailableText
    }

    fun runDeleteEntry() {
        showDeleteDialog = false
        if (requestInFlight) return
        requestInFlight = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                repository.clearPluginCacheEntry(route.pluginId, route.scopeId, route.entryId)
            }
            requestInFlight = false
            when (outcome) {
                is PluginCacheOperationOutcome.Completed -> {
                    if (outcome.response.success) {
                        // 列表页据此刷新（进程内信号），成功提示也由列表页显示：
                        // 本页 pop 后组合随转场结束销毁，页面内 snackbar 无法存活
                        PluginCacheEntriesVersion.version++
                        navigator.pop()
                    } else {
                        snackbarHostState.showSnackbar(
                            describeFailure(outcome.response.errorCode),
                            duration = SnackbarDuration.Custom(3000L)
                        )
                    }
                }

                is PluginCacheOperationOutcome.Waiting -> snackbarHostState.showSnackbar(
                    describeFailure(outcome.reason),
                    duration = SnackbarDuration.Custom(3000L)
                )

                is PluginCacheOperationOutcome.Rejected -> snackbarHostState.showSnackbar(
                    describeFailure(outcome.reason),
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
                message = "$deleteConfirm\n${route.title}",
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
    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = route.title.ifBlank {
                        stringResource(R.string.title_plugin_cache_detail)
                    },
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
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
            ) {
                val metaSummary = formatCacheEntryMeta(route.sizeBytes, route.updatedAtEpochMs)
                if (metaSummary != null) {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        BasicComponent(title = metaSummary)
                    }
                }
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

@Composable
private fun DetailLine(label: String, value: String, showDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Text(
                text = value,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

/** 缓存条目固有信息（大小/更新时间）；列表页与详情页共用 */
internal fun formatCacheEntryMeta(
    sizeBytes: Long?,
    updatedAtEpochMs: Long?,
): String? {
    val parts = buildList {
        sizeBytes?.let { add("${it / 1024} KB") }
        updatedAtEpochMs?.let { updatedAt ->
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
