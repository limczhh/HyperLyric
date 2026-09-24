package com.lidesheng.hyperlyric.ui.page.hooksettings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookConfigSaver
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricSourcePage() {
    val navigator = LocalNavigator.current
    val prefs = rememberHookPrefs()
    val saveConfig = rememberHookConfigSaver(prefs)
    var lyricSource by remember {
        mutableStateOf(
            prefs.getString(
                RootConstants.KEY_HOOK_LYRIC_SOURCE,
                RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
            ) ?: RootConstants.DEFAULT_HOOK_LYRIC_SOURCE
        )
    }
    val sourceOptions = listOf(
        stringResource(R.string.lyric_source_lyricon),
        stringResource(R.string.lyric_source_superlyric),
        stringResource(R.string.lyric_source_lyricinfo)
    )
    val sourceIds = listOf("lyricon", "superlyric", "lyricinfo")

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val selectedSourceIndex = sourceIds.indexOf(lyricSource).coerceAtLeast(0)

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.title_lyric_source),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    bottomContent = {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .fillMaxWidth()
                        ) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.title_lyric_source),
                                items = sourceOptions,
                                selectedIndex = selectedSourceIndex,
                                onSelectedIndexChange = { index ->
                                    sourceIds.getOrNull(index)?.let { newSource ->
                                        lyricSource = newSource
                                        saveConfig(
                                            RootConstants.KEY_HOOK_LYRIC_SOURCE,
                                            newSource
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        when (lyricSource) {
            "lyricon" -> LyricProviderSection(
                innerPadding = innerPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                promptContent = { LyricSourcePromptCard(lyricSource) }
            )

            "superlyric" -> SuperLyricAppSection(
                innerPadding = innerPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                promptContent = { LyricSourcePromptCard(lyricSource) }
            )

            else -> LyricSourcePromptContent(
                lyricSource = lyricSource,
                innerPadding = innerPadding,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop
            )
        }
    }
}
