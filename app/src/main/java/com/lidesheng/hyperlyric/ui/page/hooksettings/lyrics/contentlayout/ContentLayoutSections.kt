package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.contentlayout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.MusicInfoLayoutPolicy
import com.lidesheng.hyperlyric.common.RootConstants
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

internal enum class ContentLayoutField(
    val key: String,
    @param:StringRes val labelRes: Int
) {
    Title(
        key = MusicInfoLayoutPolicy.FIELD_TITLE,
        labelRes = R.string.content_layout_field_title
    ),
    Artist(
        key = MusicInfoLayoutPolicy.FIELD_ARTIST,
        labelRes = R.string.content_layout_field_artist
    ),
    Album(
        key = MusicInfoLayoutPolicy.FIELD_ALBUM,
        labelRes = R.string.content_layout_field_album
    ),
    Duration(
        key = MusicInfoLayoutPolicy.FIELD_DURATION,
        labelRes = R.string.content_layout_field_duration
    ),
    Elapsed(
        key = MusicInfoLayoutPolicy.FIELD_ELAPSED,
        labelRes = R.string.content_layout_field_elapsed
    ),
    Remaining(
        key = MusicInfoLayoutPolicy.FIELD_REMAINING,
        labelRes = R.string.content_layout_field_remaining
    ),
    ProgressPercent(
        key = MusicInfoLayoutPolicy.FIELD_PROGRESS_PERCENT,
        labelRes = R.string.content_layout_field_progress_percent
    )
}

internal enum class ContentLayoutSeparator(
    val key: String,
    @param:StringRes val labelRes: Int
) {
    Plus(
        key = MusicInfoLayoutPolicy.SEPARATOR_PLUS,
        labelRes = R.string.content_layout_separator_plus
    ),
    Space(
        key = MusicInfoLayoutPolicy.SEPARATOR_SPACE,
        labelRes = R.string.content_layout_separator_space
    ),
    Comma(
        key = MusicInfoLayoutPolicy.SEPARATOR_COMMA,
        labelRes = R.string.content_layout_separator_comma
    ),
    IdeographicComma(
        key = MusicInfoLayoutPolicy.SEPARATOR_IDEOGRAPHIC_COMMA,
        labelRes = R.string.content_layout_separator_ideographic_comma
    ),
    Slash(
        key = MusicInfoLayoutPolicy.SEPARATOR_SLASH,
        labelRes = R.string.content_layout_separator_slash
    ),
    Hyphen(
        key = MusicInfoLayoutPolicy.SEPARATOR_HYPHEN,
        labelRes = R.string.content_layout_separator_hyphen
    ),
    None(
        key = MusicInfoLayoutPolicy.SEPARATOR_NONE,
        labelRes = R.string.content_layout_separator_none
    );

    val value: String
        get() = MusicInfoLayoutPolicy.separatorValue(key)
}

internal fun LazyListScope.contentLayoutSections(
    firstLine: List<ContentLayoutField>,
    secondLine: List<ContentLayoutField>,
    separator: ContentLayoutSeparator,
    onEditField: (Int) -> Unit,
    onSeparatorChange: (ContentLayoutSeparator) -> Unit,
    centerMusicInfo: Boolean,
    onCenterMusicInfoChange: (Boolean) -> Unit,
    centerLyric: Boolean,
    onCenterLyricChange: (Boolean) -> Unit,
    rightLyric: Boolean,
    onRightLyricChange: (Boolean) -> Unit,
    placeholderFormat: Int,
    onPlaceholderFormatChange: (Int) -> Unit,
    hideTitleAlias: Boolean,
    onHideTitleAliasChange: (Boolean) -> Unit
) {
    item(key = "music_info_title") {
        SmallTitle(text = stringResource(id = R.string.title_content_layout_music_info))
    }
    item(key = "music_info_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ContentLayoutLine(
                    title = stringResource(id = R.string.title_content_layout_first_line),
                    fields = firstLine,
                    separator = separator,
                    onEditField = { onEditField(0) }
                )
                ContentLayoutLine(
                    title = stringResource(id = R.string.title_content_layout_second_line),
                    fields = secondLine,
                    separator = separator,
                    onEditField = { onEditField(1) }
                )
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.title_content_layout_separator),
                    summary = stringResource(id = R.string.summary_content_layout_separator),
                    items = ContentLayoutSeparator.values().map { separatorOption ->
                        stringResource(id = separatorOption.labelRes)
                    },
                    selectedIndex = separator.ordinal,
                    onSelectedIndexChange = { index ->
                        ContentLayoutSeparator.values().getOrNull(index)?.let(onSeparatorChange)
                    }
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_center_music_info),
                    checked = centerMusicInfo,
                    onCheckedChange = onCenterMusicInfoChange
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_hide_title_alias),
                    summary = stringResource(id = R.string.summary_hide_title_alias),
                    checked = hideTitleAlias,
                    onCheckedChange = onHideTitleAliasChange
                )
            }
        }
    }
    item(key = "lyrics_title") {
        SmallTitle(text = stringResource(id = R.string.title_content_layout_lyric))
    }
    item(key = "lyrics_content") {
        val placeholderOptions = listOf(
            stringResource(id = R.string.option_placeholder_none),
            stringResource(id = R.string.option_placeholder_title_artist),
            stringResource(id = R.string.option_placeholder_title),
            stringResource(id = R.string.option_placeholder_countdown)
        )
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                SwitchPreference(
                    title = stringResource(id = R.string.title_center_lyric),
                    checked = centerLyric,
                    onCheckedChange = onCenterLyricChange
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_right_lyric),
                    checked = rightLyric,
                    onCheckedChange = onRightLyricChange
                )
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.title_placeholder_format),
                    items = placeholderOptions,
                    selectedIndex = placeholderFormat.coerceIn(
                        RootConstants.PLACEHOLDER_FORMAT_NONE,
                        RootConstants.PLACEHOLDER_FORMAT_COUNTDOWN
                    ),
                    onSelectedIndexChange = onPlaceholderFormatChange
                )
            }
        }
    }
}

@Composable
private fun ContentLayoutLine(
    title: String,
    fields: List<ContentLayoutField>,
    separator: ContentLayoutSeparator,
    onEditField: () -> Unit
) {
    val fieldLabels = fields.map { field -> stringResource(id = field.labelRes) }
    val displaySummary = fieldLabels.takeIf { it.isNotEmpty() }?.joinToString(separator.value)
        ?: stringResource(id = R.string.summary_content_layout_empty_line)

    ArrowPreference(
        title = title,
        summary = displaySummary,
        onClick = onEditField,
        modifier = Modifier.fillMaxWidth()
    )
}
