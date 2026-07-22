package com.lidesheng.hyperlyric.ui.page

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.UIConstants
import com.lidesheng.hyperlyric.common.PrefsBridge
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.navigation.Route
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HookSettingsPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
    var lyricSource by remember {
        mutableStateOf(prefs.getString(RootConstants.KEY_HOOK_LYRIC_SOURCE, RootConstants.DEFAULT_HOOK_LYRIC_SOURCE) ?: "lyricon")
    }
    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_super_island_lyrics),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(R.string.back))
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
                hookSettingsSections(lyricSource, onLyricSourceChange = { lyricSource = it })
            }
        }
    }
}

private fun LazyListScope.hookSettingsSections(
    lyricSource: String,
    onLyricSourceChange: (String) -> Unit
) {
    item(key = "lyric_mode") {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
        var lyricMode by remember { mutableIntStateOf(prefs.getInt(RootConstants.KEY_HOOK_LYRIC_MODE, RootConstants.DEFAULT_HOOK_LYRIC_MODE)) }
        val lyricModeOptions = listOf(
            stringResource(R.string.lyric_mode_verbatim),
            stringResource(R.string.lyric_mode_separated)
        )
        val sourceOptions = listOf(
            stringResource(R.string.lyric_source_lyricon),
            stringResource(R.string.lyric_source_superlyric),
            stringResource(R.string.lyric_source_lyricinfo)
        )
        val sourceIds = listOf("lyricon", "superlyric", "lyricinfo")
        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
            OverlayDropdownPreference(
                title = stringResource(R.string.title_lyric_mode),
                items = lyricModeOptions,
                selectedIndex = lyricMode,
                    onSelectedIndexChange = { index ->
                    lyricMode = index
                    prefs.edit { putInt(RootConstants.KEY_HOOK_LYRIC_MODE, index) }
                    PrefsBridge.putInt(RootConstants.KEY_HOOK_LYRIC_MODE, index)
                }
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.title_lyric_source),
                items = sourceOptions,
                selectedIndex = sourceIds.indexOf(lyricSource).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    val newSource = sourceIds[index]
                    onLyricSourceChange(newSource)
                    prefs.edit { putString(RootConstants.KEY_HOOK_LYRIC_SOURCE, newSource) }
                    PrefsBridge.putString(RootConstants.KEY_HOOK_LYRIC_SOURCE, newSource)
                }
            )
        }
    }
    item(key = "lyric_source_prompt") {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) }
        var isDismissed by remember(lyricSource) { mutableStateOf(prefs.getBoolean("hide_lyric_source_prompt_$lyricSource", false)) }
        val promptText = when (lyricSource) {
            "superlyric" -> stringResource(R.string.summary_help_source_superlyric)
            "lyricinfo" -> stringResource(R.string.summary_help_source_lyricinfo)
            else -> stringResource(R.string.summary_help_source_lyricon)
        }
        
        AnimatedVisibility(
            visible = !isDismissed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.tertiaryContainer,
                    contentColor = MiuixTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = promptText,
                        color = MiuixTheme.colorScheme.onTertiaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    IconButton(
                        onClick = {
                            isDismissed = true
                            prefs.edit { putBoolean("hide_lyric_source_prompt_$lyricSource", true) }
                        },
                        minWidth = 16.dp,
                        minHeight = 16.dp
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Demibold.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MiuixTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
    item(key = "custom_config_title") {
        SmallTitle(text = stringResource(R.string.title_custom_config))
    }
    item(key = "custom_config_content") {
        val navigator = LocalNavigator.current
        Card(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Column {
                ArrowPreference(title = stringResource(R.string.title_super_island), onClick = { navigator.navigate(Route.SuperIslandSettings) })
                ArrowPreference(title = stringResource(R.string.title_text), onClick = { navigator.navigate(Route.LyricDisplay) })
                ArrowPreference(title = stringResource(R.string.title_marquee), onClick = { navigator.navigate(Route.LyricScroll) })
                ArrowPreference(title = stringResource(R.string.title_verbatim_lyric), onClick = { navigator.navigate(Route.VerbatimLyric) })
                ArrowPreference(title = stringResource(R.string.title_translation), onClick = { navigator.navigate(Route.LyricTranslation) })
                ArrowPreference(title = stringResource(R.string.title_lyric_anim), onClick = { navigator.navigate(Route.LyricAnimation) })
                AnimatedVisibility(visible = lyricSource == "lyricon") {
                    ArrowPreference(title = stringResource(R.string.title_lyric_provider), onClick = { navigator.navigate(Route.LyricProvider) })
                }
            }
        }
    }
}
