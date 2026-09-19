package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.contentlayout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.MusicInfoLayoutPolicy
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplayPolicy
import com.lidesheng.hyperlyric.common.lyric.LyricContentDisplaySettings
import com.lidesheng.hyperlyric.common.lyric.LyricSecondaryContent
import com.lidesheng.hyperlyric.ui.component.ReorderableCheckboxItem
import com.lidesheng.hyperlyric.ui.component.ReorderableCheckboxList
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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

internal enum class ContentLayoutAlignment(
    val value: Int,
    @param:StringRes val labelRes: Int
) {
    Left(
        value = RootConstants.CONTENT_ALIGNMENT_LEFT,
        labelRes = R.string.content_layout_alignment_left
    ),
    Center(
        value = RootConstants.CONTENT_ALIGNMENT_CENTER,
        labelRes = R.string.content_layout_alignment_center
    ),
    Right(
        value = RootConstants.CONTENT_ALIGNMENT_RIGHT,
        labelRes = R.string.content_layout_alignment_right
    );

    companion object {
        fun fromValue(value: Int): ContentLayoutAlignment {
            return values().firstOrNull { it.value == value } ?: Left
        }
    }
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
    musicInfoAlignment: ContentLayoutAlignment,
    onMusicInfoAlignmentChange: (ContentLayoutAlignment) -> Unit,
    lyricAlignment: ContentLayoutAlignment,
    onLyricAlignmentChange: (ContentLayoutAlignment) -> Unit,
    placeholderFormat: Int,
    onPlaceholderFormatChange: (Int) -> Unit,
    hideTitleAlias: Boolean,
    onHideTitleAliasChange: (Boolean) -> Unit,
    lyricContentDisplay: LyricContentDisplaySettings,
    onEditLyricContent: () -> Unit,
    lyricContentSheetVisible: Boolean,
    onlySecondary: Boolean,
    onOnlySecondaryChange: (Boolean) -> Unit,
    swapSecondary: Boolean,
    onSwapSecondaryChange: (Boolean) -> Unit,
    autoDuet: Boolean,
    onAutoDuetChange: (Boolean) -> Unit
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
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.title_content_layout_alignment),
                    items = ContentLayoutAlignment.values().map { alignment ->
                        stringResource(id = alignment.labelRes)
                    },
                    selectedIndex = musicInfoAlignment.ordinal,
                    onSelectedIndexChange = { index ->
                        ContentLayoutAlignment.values().getOrNull(index)?.let(
                            onMusicInfoAlignmentChange
                        )
                    }
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
                OverlayDropdownPreference(
                    title = stringResource(id = R.string.title_content_layout_alignment),
                    items = ContentLayoutAlignment.values().map { alignment ->
                        stringResource(id = alignment.labelRes)
                    },
                    selectedIndex = lyricAlignment.ordinal,
                    onSelectedIndexChange = { index ->
                        ContentLayoutAlignment.values().getOrNull(index)?.let(
                            onLyricAlignmentChange
                        )
                    }
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
    item(key = "lyrics_double_line_content") {
        val displayLabels = lyricContentDisplay.order
            .filter(lyricContentDisplay::isEnabled)
            .map { content ->
                stringResource(id = content.labelRes())
            }
        val displaySummary = displayLabels
            .joinToString("→")
            .takeIf { it.isNotBlank() }
            ?: stringResource(id = R.string.summary_lyric_content_none)

        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
                ArrowPreference(
                    title = stringResource(id = R.string.title_double_line_content),
                    summary = displaySummary,
                    onClick = onEditLyricContent,
                    holdDownState = lyricContentSheetVisible
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_auto_duet),
                    summary = stringResource(id = R.string.summary_auto_duet),
                    checked = autoDuet,
                    onCheckedChange = onAutoDuetChange
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_only_secondary_lyric),
                    checked = onlySecondary,
                    onCheckedChange = onOnlySecondaryChange
                )
                SwitchPreference(
                    title = stringResource(id = R.string.title_swap_secondary_lyric),
                    checked = swapSecondary,
                    onCheckedChange = onSwapSecondaryChange
                )
            }
        }
    }
}

@Composable
internal fun LyricContentDisplayBottomSheet(
    show: Boolean,
    currentSettings: LyricContentDisplaySettings,
    onDismiss: () -> Unit,
    onConfirm: (LyricContentDisplaySettings) -> Unit
) {
    if (!show) return

    val initialOrder = remember(show, currentSettings) {
        LyricContentDisplayPolicy.normalizeOrder(currentSettings.order)
    }
    var draftOrder by remember(show, initialOrder) { mutableStateOf(initialOrder) }
    var draftSelection by remember(show, currentSettings) {
        mutableStateOf(
            LyricSecondaryContent.entries
                .filter(currentSettings::isEnabled)
                .toSet()
        )
    }
    val checkboxItems = draftOrder.map { content ->
        ReorderableCheckboxItem(
            key = content.preferenceValue,
            title = stringResource(id = content.labelRes()),
            checked = content in draftSelection
        )
    }
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val safeTopInset = maxOf(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        WindowInsets.captionBar.asPaddingValues().calculateTopPadding(),
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding(),
    )
    val maxSheetHeight = (windowHeight - safeTopInset).coerceAtLeast(0.dp)
    val maxListHeight = (maxSheetHeight - 122.dp).coerceAtLeast(0.dp)

    WindowBottomSheet(
        show = true,
        modifier = Modifier.heightIn(max = maxSheetHeight),
        title = stringResource(id = R.string.title_double_line_content),
        backgroundColor = MiuixTheme.colorScheme.surface,
        enableNestedScroll = false,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(id = R.string.cancel),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        endAction = {
            IconButton(
                onClick = {
                    val settings = LyricContentDisplaySettings(
                        showTranslation = LyricSecondaryContent.TRANSLATION in draftSelection,
                        showRoma = LyricSecondaryContent.ROMA in draftSelection,
                        showNextLyric = LyricSecondaryContent.NEXT_LINE in draftSelection,
                        order = draftOrder,
                        showBackgroundVocal =
                            LyricSecondaryContent.BACKGROUND_VOCAL in draftSelection,
                        showOverlappingLine =
                            LyricSecondaryContent.OVERLAPPING_LINE in draftSelection
                    )
                    onConfirm(settings)
                    onDismiss()
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(id = R.string.confirm),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val paddingPx = 24.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(maxWidth = constraints.maxWidth + paddingPx * 2)
                    )
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.place(-paddingPx, 0)
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp)
            ) {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                ) {
                    ReorderableCheckboxList(
                        items = checkboxItems,
                        onCheckedChange = { key, checked ->
                            LyricSecondaryContent.fromPreferenceValue(key)?.let { content ->
                                draftSelection = if (checked) {
                                    draftSelection + content
                                } else {
                                    draftSelection - content
                                }
                            }
                        },
                        onMove = { fromIndex, toIndex ->
                            draftOrder = draftOrder.move(fromIndex, toIndex)
                        },
                        maxHeight = maxListHeight
                    )
                }
            }
        }
    }
}

private fun LyricSecondaryContent.labelRes(): Int = when (this) {
    LyricSecondaryContent.BACKGROUND_VOCAL -> R.string.title_background_vocal
    LyricSecondaryContent.OVERLAPPING_LINE -> R.string.title_overlapping_lyric
    LyricSecondaryContent.TRANSLATION -> R.string.title_translation
    LyricSecondaryContent.ROMA -> R.string.title_roma
    LyricSecondaryContent.NEXT_LINE -> R.string.title_next_lyric
}

private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) {
        return this
    }
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
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
