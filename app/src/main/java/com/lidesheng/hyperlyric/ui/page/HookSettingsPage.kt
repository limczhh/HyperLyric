package com.lidesheng.hyperlyric.ui.page

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.root.RootApplication
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HookSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val prefs =
        remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    var lyricSource by remember {
        mutableStateOf(
            prefs.getString(
                RootConstants.KEY_HOOK_LYRIC_SOURCE,
                RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
            ) ?: "lyricon"
        )
    }
    var islandWidthMode by remember {
        mutableIntStateOf(
            prefs.getInt(
                RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
                RootConstants.DEFAULT_HOOK_ISLAND_WIDTH_MODE
            ).coerceIn(
                RootConstants.ISLAND_WIDTH_MODE_FIXED,
                RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
            )
        )
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                RootConstants.KEY_HOOK_LYRIC_SOURCE -> {
                    lyricSource = sharedPreferences.getString(
                        RootConstants.KEY_HOOK_LYRIC_SOURCE,
                        RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                    ) ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
                }

                RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE -> {
                    islandWidthMode = sharedPreferences.getInt(
                        RootConstants.KEY_HOOK_ISLAND_WIDTH_MODE,
                        RootConstants.DEFAULT_HOOK_ISLAND_WIDTH_MODE
                    ).coerceIn(
                        RootConstants.ISLAND_WIDTH_MODE_FIXED,
                        RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
                    )
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val lyricSourceLabel = when (lyricSource) {
        "superlyric" -> stringResource(R.string.lyric_source_superlyric)
        "lyricinfo" -> stringResource(R.string.lyric_source_lyricinfo)
        else -> stringResource(R.string.lyric_source_lyricon)
    }
    val widthModeLabel = if (
        islandWidthMode == RootConstants.ISLAND_WIDTH_MODE_DYNAMIC
    ) {
        stringResource(R.string.option_super_island_width_dynamic)
    } else {
        stringResource(R.string.option_super_island_width_fixed)
    }
    var hookEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                RootConstants.DEFAULT_HOOK_ENABLE_SUPER_ISLAND
            )
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val xposedNotActiveMessage = stringResource(R.string.toast_xposed_module_not_active)
    Scaffold(
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_super_island_lyrics),
                    scrollBehavior = topAppBarScrollBehavior,
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
        val lazyListState = rememberLazyListState()
        val top = innerPadding.calculateTopPadding()
        val bottom = innerPadding.calculateBottomPadding()
        val contentPadding = remember(top, bottom) {
            PaddingValues(
                top = top,
                start = 0.dp,
                end = 0.dp,
                bottom = bottom + 16.dp
            )
        }
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(
                    enableScrollEndHaptic = true,
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior
                ),
                contentPadding = contentPadding,
            ) {
                hookSettingsSections(
                    hookEnabled = hookEnabled,
                    onHookEnabledChange = { enabled ->
                        if (enabled) {
                            if (RootApplication.xposedService != null) {
                                hookEnabled = true
                                prefs.edit {
                                    putBoolean(
                                        RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                                        true
                                    )
                                }
                                PrefsBridge.putBoolean(
                                    RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                                    true
                                )
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = xposedNotActiveMessage,
                                        duration = SnackbarDuration.Custom(2000L)
                                    )
                                }
                            }
                        } else {
                            hookEnabled = false
                            prefs.edit {
                                putBoolean(
                                    RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                                    false
                                )
                            }
                            PrefsBridge.putBoolean(
                                RootConstants.KEY_HOOK_ENABLE_SUPER_ISLAND,
                                false
                            )
                        }
                    },
                    lyricSourceLabel = lyricSourceLabel,
                    widthModeLabel = widthModeLabel,
                )
            }
        }
    }
}

private fun LazyListScope.hookSettingsSections(
    hookEnabled: Boolean,
    onHookEnabledChange: (Boolean) -> Unit,
    lyricSourceLabel: String,
    widthModeLabel: String
) {
    item(key = "hook_enable") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            SwitchPreference(
                title = stringResource(R.string.title_enable),
                checked = hookEnabled,
                onCheckedChange = onHookEnabledChange,
            )
        }
    }
    item(key = "custom_config_title") {
        SmallTitle(text = stringResource(R.string.title_custom_config))
    }
    item(key = "custom_config_content") {
        val navigator = LocalNavigator.current
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(R.string.title_lyric_source),
                    enabled = hookEnabled,
                    endActions = {
                        Text(
                            text = lyricSourceLabel,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = { navigator.navigate(Route.LyricSource) }
                )
                ArrowPreference(
                    title = stringResource(R.string.title_super_island),
                    enabled = hookEnabled,
                    endActions = {
                        Text(
                            text = widthModeLabel,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    },
                    onClick = { navigator.navigate(Route.SuperIslandSettings) })
                ArrowPreference(
                    title = stringResource(R.string.title_content_layout),
                    summary = stringResource(R.string.summary_content_layout),
                    enabled = hookEnabled,
                    onClick = { navigator.navigate(Route.SuperIslandContentLayout) })
                ArrowPreference(
                    title = stringResource(R.string.title_text),
                    enabled = hookEnabled,
                    onClick = { navigator.navigate(Route.LyricDisplay) })
                ArrowPreference(
                    title = stringResource(R.string.title_marquee),
                    enabled = hookEnabled,
                    onClick = { navigator.navigate(Route.LyricScroll) })
                ArrowPreference(
                    title = stringResource(R.string.title_verbatim_lyric),
                    enabled = hookEnabled,
                    onClick = { navigator.navigate(Route.VerbatimLyric) })
                ArrowPreference(
                    title = stringResource(R.string.title_double_line_content),
                    summary = stringResource(R.string.summary_double_line_content),
                    enabled = hookEnabled,
                    onClick = { navigator.navigate(Route.LyricTranslation) })
                ArrowPreference(
                    title = stringResource(R.string.title_lyric_anim),
                    enabled = hookEnabled,
                    onClick = { navigator.navigate(Route.LyricAnimation) })
                ArrowPreference(
                    title = stringResource(R.string.title_plugins),
                    onClick = { navigator.navigate(Route.Plugins) })
            }
        }
    }
}
