package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.translation

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
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

fun LazyListScope.translationSections(
    lyricContentDisplay: LyricContentDisplaySettings,
    onEditLyricContent: () -> Unit,
    lyricContentSheetVisible: Boolean,
    onlySecondary: Boolean,
    onOnlySecondaryChange: (Boolean) -> Unit,
    swapSecondary: Boolean,
    onSwapSecondaryChange: (Boolean) -> Unit
) {
    item(key = "lyric_content_display") {
        val displayLabels = lyricContentDisplay.order
            .filter(lyricContentDisplay::isEnabled)
            .map { content ->
                stringResource(id = content.labelRes())
            }
        val displaySummary = displayLabels
            .joinToString("、")
            .takeIf { it.isNotBlank() }
            ?: stringResource(id = R.string.summary_lyric_content_none)

        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            ArrowPreference(
                title = stringResource(id = R.string.title_double_line_content),
                summary = displaySummary,
                onClick = onEditLyricContent,
                holdDownState = lyricContentSheetVisible
            )
        }
    }

    item(key = "translation_title") {
        SmallTitle(text = stringResource(id = R.string.title_secondary_lyric))
    }
    item(key = "translation_content") {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
                .fillMaxWidth()
        ) {
            Column {
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
                        order = draftOrder
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
