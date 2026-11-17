package com.lidesheng.hyperlyric.ui.page.hooksettings

import com.lidesheng.hyperlyric.ui.utils.pageContentPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.common.rememberHookPrefs
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricSourcePromptContent(
    lyricSource: String,
    innerPadding: PaddingValues,
    topAppBarScrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?
) {
    val lazyListState = rememberLazyListState()
    val top = innerPadding.calculateTopPadding()
    val bottom = innerPadding.calculateBottomPadding()
    val contentPadding = pageContentPadding(innerPadding, extraBottom = 0.dp)

    Box(
        modifier = (if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
            .fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.pageScrollModifiers(
                enableScrollEndHaptic = true,
                showTopAppBar = true,
                topAppBarScrollBehavior = topAppBarScrollBehavior
            ),
            contentPadding = contentPadding
        ) {
            item(key = "lyric_source_prompt", contentType = "source_prompt") {
                LyricSourcePromptCard(lyricSource)
            }
        }
    }
}

@Composable
internal fun LyricSourcePromptCard(lyricSource: String) {
    val prefs = rememberHookPrefs()
    var isDismissed by remember(lyricSource) {
        mutableStateOf(
            prefs.getBoolean(
                "hide_lyric_source_prompt_$lyricSource",
                false
            )
        )
    }
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
                modifier = Modifier
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    )
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = promptText,
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                IconButton(
                    onClick = {
                        isDismissed = true
                        prefs.edit {
                            putBoolean("hide_lyric_source_prompt_$lyricSource", true)
                        }
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
