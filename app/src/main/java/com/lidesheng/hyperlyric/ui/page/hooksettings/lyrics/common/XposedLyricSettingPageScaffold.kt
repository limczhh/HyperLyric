package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.LyricOutputTargetPreferencePolicy
import com.lidesheng.hyperlyric.common.StatusBarLyricPreferences
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.page.hooksettings.rememberEntryTransitionContentReady
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberHookPrefs(statusBarLyrics: Boolean = false): SharedPreferences {
    val context = LocalContext.current
    return remember(context, statusBarLyrics) {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        if (statusBarLyrics) StatusBarLyricPreferences.scoped(prefs) else prefs
    }
}

@Composable
internal fun rememberHookConfigSaver(prefs: SharedPreferences): (String, Any) -> Unit {
    return remember(prefs) {
        { key: String, value: Any ->
            val storedKey = StatusBarLyricPreferences.resolveStoredKey(prefs, key)
            if (value is Boolean && LyricOutputTargetPreferencePolicy.isTargetKey(storedKey)) {
                PrefsBridge.putBoolean(storedKey, value)
            } else {
                prefs.edit {
                    when (value) {
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Float -> putFloat(key, value)
                        is String -> putString(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                when (value) {
                    is Int -> PrefsBridge.putInt(storedKey, value)
                    is Boolean -> PrefsBridge.putBoolean(storedKey, value)
                    is Float -> PrefsBridge.putFloat(storedKey, value)
                    is String -> PrefsBridge.putString(storedKey, value)
                    is Set<*> -> PrefsBridge.putStringSet(
                        storedKey,
                        value.filterIsInstance<String>().toSet()
                    )
                }
            }
        }
    }
}

@Composable
internal fun XposedLyricSettingPage(
    title: String,
    snackbarHostState: SnackbarHostState? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    topBarBottomContent: @Composable () -> Unit = {},
    pageContent: (@Composable (PaddingValues, ScrollBehavior) -> Unit)? = null,
    isInitialLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit = {}
) {
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(state = it) }
        },
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
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = topBarActions,
                    bottomContent = topBarBottomContent,
                )
            }
        }
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding()
        val contentPadding = remember(topPadding, bottomPadding) {
            PaddingValues(
                top = topPadding,
                start = 0.dp,
                end = 0.dp,
                bottom = bottomPadding + 16.dp
            )
        }
        val lazyListState = rememberLazyListState()
        val pullToRefreshState = rememberPullToRefreshState()
        val contentReady = if (onRefresh != null) {
            rememberEntryTransitionContentReady()
        } else {
            true
        }
        val refreshTexts = listOf(
            stringResource(R.string.refresh_pull_down),
            stringResource(R.string.refresh_release),
            stringResource(R.string.refreshing),
            stringResource(R.string.refresh_success)
        )

        val showInitialLoading = onRefresh != null && (!contentReady || isInitialLoading)
        if (showInitialLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator()
            }
        } else {
            Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
                if (pageContent != null) {
                    pageContent(contentPadding, topAppBarScrollBehavior)
                } else if (onRefresh == null) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.pageScrollModifiers(
                            enableScrollEndHaptic = true,
                            showTopAppBar = true,
                            topAppBarScrollBehavior = topAppBarScrollBehavior
                        ),
                        contentPadding = contentPadding,
                        content = content
                    )
                } else {
                    PullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        pullToRefreshState = pullToRefreshState,
                        contentPadding = PaddingValues(top = topPadding),
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                        refreshTexts = refreshTexts,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val refreshContentPadding = remember(topPadding, bottomPadding) {
                            PaddingValues(
                                top = topPadding,
                                start = 0.dp,
                                end = 0.dp,
                                bottom = bottomPadding
                            )
                        }
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.pageScrollModifiers(
                                enableScrollEndHaptic = true,
                                showTopAppBar = false,
                                topAppBarScrollBehavior = topAppBarScrollBehavior
                            ),
                            contentPadding = refreshContentPadding,
                            content = content
                        )
                    }
                }
            }
        }
    }
}
