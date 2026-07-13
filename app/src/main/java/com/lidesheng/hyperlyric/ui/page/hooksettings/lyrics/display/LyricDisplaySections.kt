package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.display

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

fun LazyListScope.lyricDisplaySections(
    textSize: Int,
    onTextSizeClick: () -> Unit,
    textSizeRatio: Float,
    onTextSizeRatioClick: () -> Unit,
    fadingEdge: Int,
    onFadingEdgeClick: () -> Unit,
    extractCoverColor: Boolean,
    onExtractCoverColorChange: (Boolean) -> Unit,
    extractCoverGradient: Boolean,
    onExtractCoverGradientChange: (Boolean) -> Unit,
    customFontPath: String,
    onFontPathClick: () -> Unit,
    fontWeight: Int,
    onFontWeightClick: () -> Unit,
    fontItalic: Boolean,
    onFontItalicChange: (Boolean) -> Unit,
    centerLyric: Boolean,
    onCenterLyricChange: (Boolean) -> Unit
) {
    item(key = "lyric_display") {
        Column {
            SmallTitle(text = stringResource(id = R.string.title_text))
            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp).fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(id = R.string.title_size),
                        endActions = {
                            Text(
                                "$textSize",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = onTextSizeClick
                    )
                    ArrowPreference(
                        title = stringResource(id = R.string.title_text_size_ratio),
                        endActions = {
                            Text(
                                stringResource(id = R.string.format_percent, (textSizeRatio * 100).toInt()),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = onTextSizeRatioClick
                    )
                    ArrowPreference(
                        title = stringResource(id = R.string.title_fading_edge),
                        endActions = {
                            Text(
                                "$fadingEdge",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = onFadingEdgeClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = stringResource(id = R.string.title_extract_cover_color),
                        checked = extractCoverColor,
                        onCheckedChange = onExtractCoverColorChange
                    )
                    AnimatedVisibility(visible = extractCoverColor) {
                        SwitchPreference(
                            title = stringResource(id = R.string.title_extract_cover_gradient),
                            checked = extractCoverGradient,
                            onCheckedChange = onExtractCoverGradientChange
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = stringResource(id = R.string.title_custom_font),
                        endActions = {
                            Text(
                                customFontPath.ifEmpty { stringResource(id = R.string.summary_default_font) },
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = onFontPathClick
                    )
                    ArrowPreference(
                        title = stringResource(id = R.string.title_font_weight),
                        endActions = {
                            Text(
                                fontWeight.toString(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        },
                        onClick = onFontWeightClick
                    )
                    SwitchPreference(
                        title = stringResource(id = R.string.title_italic),
                        checked = fontItalic,
                        onCheckedChange = onFontItalicChange
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = stringResource(id = R.string.title_center_lyric),
                        checked = centerLyric,
                        onCheckedChange = onCenterLyricChange
                    )
                }
            }
        }
    }
}
