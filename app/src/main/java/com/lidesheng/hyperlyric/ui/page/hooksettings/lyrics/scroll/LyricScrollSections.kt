package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.scroll

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun LazyListScope.lyricScrollSections(
    lyricMode: Int,
    marqueeMode: Boolean,
    onMarqueeModeChange: (Boolean) -> Unit,
    marqueeSpeed: Int,
    onMarqueeSpeedClick: () -> Unit,
    marqueeDelay: Int,
    onMarqueeDelayClick: () -> Unit,
    marqueeInfinite: Boolean,
    onMarqueeInfiniteChange: (Boolean) -> Unit,
    marqueeLoop: Int,
    onMarqueeLoopClick: () -> Unit,
    marqueeStopEnd: Boolean,
    onMarqueeStopEndChange: (Boolean) -> Unit,
    marqueeMetadataMode: Boolean,
    onMarqueeMetadataModeChange: (Boolean) -> Unit,
    marqueeMetadataSpeed: Int,
    onMarqueeMetadataSpeedClick: () -> Unit,
    marqueeMetadataDelay: Int,
    onMarqueeMetadataDelayClick: () -> Unit,
    marqueeMetadataInfinite: Boolean,
    onMarqueeMetadataInfiniteChange: (Boolean) -> Unit,
    marqueeMetadataLoopDelay: Int,
    onMarqueeMetadataLoopClick: () -> Unit
) {
    item(key = "lyric_scroll") {
        Column {
            SmallTitle(text = stringResource(id = R.string.title_marquee))
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(id = R.string.title_lyric_marquee),
                    summary = stringResource(id = R.string.summary_lyric_marquee),
                    checked = marqueeMode,
                    onCheckedChange = onMarqueeModeChange
                )
                AnimatedVisibility(visible = marqueeMode) {
                    Column {
                        ArrowPreference(
                            title = stringResource(id = R.string.title_marquee_speed),
                            endActions = {
                                Text(
                                    "$marqueeSpeed",
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            },
                            onClick = onMarqueeSpeedClick
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.title_marquee_delay),
                            endActions = {
                                Text(
                                    stringResource(id = R.string.format_ms, marqueeDelay),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            },
                            onClick = onMarqueeDelayClick
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.title_infinite_loop),
                            checked = marqueeInfinite,
                            onCheckedChange = onMarqueeInfiniteChange
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.title_marquee_loop),
                            endActions = {
                                Text(
                                    stringResource(id = R.string.format_ms, marqueeLoop),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                            },
                            onClick = onMarqueeLoopClick
                        )
                        SwitchPreference(
                            title = stringResource(id = R.string.title_stop_at_end),
                            checked = marqueeStopEnd,
                            onCheckedChange = onMarqueeStopEndChange
                        )
                    }
                }
                AnimatedVisibility(visible = lyricMode == 0) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = stringResource(id = R.string.title_marquee_metadata_mode),
                            summary = stringResource(id = R.string.summary_marquee_metadata_mode),
                            checked = marqueeMetadataMode,
                            onCheckedChange = onMarqueeMetadataModeChange
                        )
                        AnimatedVisibility(visible = marqueeMetadataMode) {
                            Column {
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_marquee_metadata_speed),
                                    onClick = onMarqueeMetadataSpeedClick,
                                    endActions = {
                                        Text(
                                            "$marqueeMetadataSpeed",
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                )
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_marquee_metadata_delay),
                                    onClick = onMarqueeMetadataDelayClick,
                                    endActions = {
                                        Text(
                                            stringResource(id = R.string.format_ms, marqueeMetadataDelay),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                )
                                SwitchPreference(
                                    title = stringResource(id = R.string.title_marquee_metadata_infinite),
                                    checked = marqueeMetadataInfinite,
                                    onCheckedChange = onMarqueeMetadataInfiniteChange
                                )
                                ArrowPreference(
                                    title = stringResource(id = R.string.title_marquee_metadata_loop),
                                    onClick = onMarqueeMetadataLoopClick,
                                    endActions = {
                                        Text(
                                            stringResource(id = R.string.format_ms, marqueeMetadataLoopDelay),
                                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
