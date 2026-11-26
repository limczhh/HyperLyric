// Copyright 2026, HyperLyric contributors
// SPDX-License-Identifier: Apache-2.0

package com.lidesheng.hyperlyric.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ReorderableCheckboxItem(
    val key: String,
    val title: String,
    val checked: Boolean
)

@Composable
fun ReorderableCheckboxList(
    items: List<ReorderableCheckboxItem>,
    onCheckedChange: (key: String, checked: Boolean) -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 420.dp,
    state: LazyListState = rememberLazyListState()
) {
    if (items.isEmpty()) return

    val hapticFeedback = LocalHapticFeedback.current
    val currentOnMove by rememberUpdatedState(onMove)
    val reorderableState = rememberReorderableLazyListState(state) { from, to ->
        currentOnMove(from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
        userScrollEnabled = !reorderableState.isAnyItemDragging,
        overscrollEffect = null
    ) {
        items(
            items = items,
            key = { item -> item.key }
        ) { item ->
            ReorderableItem(
                state = reorderableState,
                key = item.key
            ) { isDragging ->
                val interactionSource = remember { MutableInteractionSource() }

                CheckboxPreference(
                    title = item.title,
                    checked = item.checked,
                    onCheckedChange = { checked ->
                        onCheckedChange(item.key, checked)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .longPressDraggableHandle(
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                            },
                            interactionSource = interactionSource
                        ),
                    endActions = {
                        DragHandleIcon()
                    },
                    checkboxLocation = CheckboxLocation.Start,
                    holdDownState = isDragging
                )
            }
        }
    }
}

@Composable
private fun DragHandleIcon() {
    val handleColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    // Keep the action footprint aligned with Miuix's standard IconButton slot. The surrounding
    // CheckboxPreference supplies its own BasicComponent inside margin and start-action spacing.
    Canvas(
        modifier = Modifier
            .size(IconButtonDefaults.MinWidth)
            // CheckboxPreference adds 8.dp to endActions; cancel it so the drawable
            // follows the same 16.dp card inset as the leading checkbox.
            .offset(x = 8.dp)
    ) {
        val strokeWidth = 2.dp.toPx()
        // Keep the visible drawable edge on the card's inner boundary instead of centering it
        // inside the action slot. Its right inset therefore matches the leading checkbox's 16.dp.
        val lineEndX = size.width - strokeWidth / 2f
        val lineStartX = lineEndX - size.width * 0.44f
        repeat(3) { index ->
            val y = size.height * (0.35f + index * 0.15f)
            drawLine(
                color = handleColor,
                start = Offset(lineStartX, y),
                end = Offset(lineEndX, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
