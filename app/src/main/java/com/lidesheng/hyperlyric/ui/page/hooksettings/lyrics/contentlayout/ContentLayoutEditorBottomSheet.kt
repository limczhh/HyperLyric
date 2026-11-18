package com.lidesheng.hyperlyric.ui.page.hooksettings.lyrics.contentlayout

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
import androidx.compose.runtime.LaunchedEffect
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
import com.lidesheng.hyperlyric.ui.component.ReorderableCheckboxItem
import com.lidesheng.hyperlyric.ui.component.ReorderableCheckboxList
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.lidesheng.hyperlyric.ui.utils.AdaptiveBottomSheet

@Composable
internal fun ContentLayoutEditorBottomSheet(
    show: Boolean,
    title: String,
    currentFields: List<ContentLayoutField>,
    availableFields: List<ContentLayoutField>,
    requireSelection: Boolean,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onConfirm: (List<ContentLayoutField>) -> Unit
) {

    val initialOrder = remember(currentFields, availableFields) {
        currentFields + availableFields.filterNot { it in currentFields }
    }
    var draftOrder by remember { mutableStateOf(initialOrder) }
    var draftSelection by remember {
        mutableStateOf(currentFields.map { it.key }.toSet())
    }
    LaunchedEffect(show) {
        if (show) {
            draftOrder = initialOrder
            draftSelection = currentFields.map { it.key }.toSet()
        }
    }
    val checkboxItems = draftOrder.map { field ->
        ReorderableCheckboxItem(
            key = field.key,
            title = stringResource(id = field.labelRes),
            checked = field.key in draftSelection
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

    AdaptiveBottomSheet(
        show = show,
        modifier = Modifier.heightIn(max = maxSheetHeight),
        title = title,
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
                    val selectedFields = draftOrder.filter { it.key in draftSelection }
                    if (!requireSelection || selectedFields.isNotEmpty()) {
                        onConfirm(selectedFields)
                        onDismiss()
                    }
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = stringResource(id = R.string.confirm),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        },
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
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
                            if (checked || !requireSelection || draftSelection.size > 1) {
                                draftSelection = if (checked) {
                                    draftSelection + key
                                } else {
                                    draftSelection - key
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

private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) {
        return this
    }
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
