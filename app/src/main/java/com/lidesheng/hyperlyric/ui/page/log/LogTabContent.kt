@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LogTabContent(
    logs: List<LogEntry>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    topAppBarScrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    copiedMsg: String,
    snackbarHostState: SnackbarHostState
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefresh(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        pullToRefreshState = pullToRefreshState,
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding()),
        topAppBarScrollBehavior = topAppBarScrollBehavior,
        refreshTexts = listOf(
            stringResource(R.string.refresh_pull_down),
            stringResource(R.string.refresh_release),
            stringResource(R.string.refreshing),
            stringResource(R.string.refresh_success)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        val lazyListState = rememberLazyListState()
        Box {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding
            ) {
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (logs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_logs_found), color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    }
                } else {
                    items(logs, key = { it.id.ifEmpty { "${it.timestamp}_${it.level}_${it.tag}_${it.message}" } }) { entry ->
                        LogItem(entry = entry, copiedMsg = copiedMsg, snackbarHostState = snackbarHostState)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
