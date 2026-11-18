package com.lidesheng.hyperlyric.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.lidesheng.hyperlyric.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.glass.glassDialogSurface
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import com.lidesheng.hyperlyric.ui.utils.LocalGlassStyle
import com.lidesheng.hyperlyric.ui.utils.LocalAppBackdrop


private val LocalGlassDialog = staticCompositionLocalOf { false }

@Composable
fun AdaptiveDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val glassStyle = LocalGlassStyle.current
    val backdrop = LocalAppBackdrop.current
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        modifier = modifier,
        dimColor = if (glassStyle) GlassDialogDefaults.ScrimColor else MiuixTheme.colorScheme.windowDimming,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        surface = if (glassStyle) glassDialogSurface(backdrop = backdrop) else null,
    ) {
        CompositionLocalProvider(LocalGlassDialog provides glassStyle) {
            content()
        }
    }
}

@Composable
fun AdaptiveDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextButtonColors? = null,
) {
    if (LocalGlassDialog.current) {
        GlassDialogButton(onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors?.let { ButtonColors(it.color, it.disabledColor, it.textColor, it.disabledTextColor) }
                ?: GlassDialogButtonDefaults.buttonColors()) {
            Text(text = text, style = MiuixTheme.textStyles.button)
        }
    } else {
        TextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors ?: ButtonDefaults.textButtonColors())
    }
}

/** Attach only to the editable field in a single-input dialog. */
@Composable
internal fun rememberDialogInputFocusModifier(): Modifier {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequester, keyboardController) {
        withFrameNanos { }
        if (focusRequester.requestFocus()) keyboardController?.show()
    }

    return Modifier.focusRequester(focusRequester)
}

data class MultiSelectDialogOption(
    val key: String,
    val title: String
)

@Composable
fun MultiSelectDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    options: List<MultiSelectDialogOption>,
    selectedKeys: Set<String>,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: (Set<String>) -> Unit
) {
    var draftSelection by remember { mutableStateOf(selectedKeys) }
    LaunchedEffect(show) { if (show) draftSelection = selectedKeys }

    AdaptiveDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surface,
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column {
                    options.forEach { option ->
                        CheckboxPreference(
                            title = option.title,
                            checked = option.key in draftSelection,
                            onCheckedChange = { checked ->
                                draftSelection = if (checked) {
                                    draftSelection + option.key
                                } else {
                                    draftSelection - option.key
                                }
                            },
                            checkboxLocation = CheckboxLocation.End
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.confirm),
                    onClick = {
                        onConfirm(draftSelection)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun NumberInputDialog(
    show: Boolean,
    title: String,
    label: String,
    summary: String? = null,
    initialValue: Int,
    min: Int,
    max: Int,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: (Int) -> Unit
) {
    var inputValue by remember {
        val text = initialValue.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
    }
    LaunchedEffect(show) {
        if (show) {
            val text = initialValue.toString()
            inputValue = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    AdaptiveDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        val inputFocusModifier = rememberDialogInputFocusModifier()
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { newValue ->
                    if (newValue.text.all { it.isDigit() }) inputValue = newValue
                },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = inputFocusModifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.confirm),
                    onClick = {
                        inputValue.text.toIntOrNull()?.let {
                            onConfirm(it.coerceIn(min, max))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun TextInputDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    label: String = title,
    summary: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    confirmText: String = stringResource(id = R.string.confirm),
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: (String) -> Unit
) {
    var inputValue by remember {
        mutableStateOf(
            TextFieldValue(initialValue, selection = TextRange(initialValue.length))
        )
    }
    LaunchedEffect(show) {
        if (show) inputValue = TextFieldValue(initialValue, selection = TextRange(initialValue.length))
    }

    AdaptiveDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        val inputFocusModifier = rememberDialogInputFocusModifier()
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = label,
                keyboardOptions = keyboardOptions,
                modifier = inputFocusModifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                maxLines = 15
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                AdaptiveDialogButton(
                    text = confirmText,
                    onClick = { onConfirm(inputValue.text); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun SimpleDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    confirmText: String = stringResource(id = R.string.confirm),
    cancelText: String = stringResource(id = R.string.cancel),
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: () -> Unit
) {

    AdaptiveDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AdaptiveDialogButton(text = cancelText, onClick = onDismiss, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(20.dp))
            AdaptiveDialogButton(
                text = confirmText,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
                onClick = {
                    onConfirm()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun FloatInputDialog(
    show: Boolean,
    title: String,
    label: String,
    summary: String? = null,
    initialValue: Float,
    min: Float,
    max: Float,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: (Float) -> Unit
) {
    var inputValue by remember {
        val text = initialValue.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
    }
    LaunchedEffect(show) {
        if (show) {
            val text = initialValue.toString()
            inputValue = TextFieldValue(text, selection = TextRange(text.length))
        }
    }

    AdaptiveDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished
    ) {
        val inputFocusModifier = rememberDialogInputFocusModifier()
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = inputValue,
                onValueChange = { newValue ->
                    if (newValue.text.isValidDecimalInput()) inputValue = newValue
                },
                label = label,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = inputFocusModifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                maxLines = 1
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.confirm),
                    onClick = {
                        inputValue.text.toFloatOrNull()?.let {
                            onConfirm(it.coerceIn(min, max))
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

private fun String.isValidDecimalInput(): Boolean {
    if (isEmpty() || this == "-" || this == "." || this == "-.") return true
    var decimalPointCount = 0
    var digitCount = 0
    for ((index, character) in withIndex()) {
        when {
            character == '-' && index == 0 -> Unit
            character == '.' && decimalPointCount++ == 0 -> Unit
            character.isDigit() -> digitCount++
            else -> return false
        }
    }
    return digitCount > 0
}

@Composable
fun PaddingInputDialog(
    show: Boolean,
    title: String,
    initialLeft: Int,
    initialRight: Int,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit = {},
    onConfirm: (Int, Int) -> Unit
) {
    var leftValue by remember { mutableStateOf(initialLeft.toString()) }
    var rightValue by remember { mutableStateOf(initialRight.toString()) }
    LaunchedEffect(show) {
        if (show) {
            leftValue = initialLeft.toString()
            rightValue = initialRight.toString()
        }
    }

    AdaptiveDialog(title = title, show = show, onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val filter = { text: String ->
                if (text == "-" || text.isEmpty()) true
                else text.toIntOrNull() != null
            }
            TextField(
                value = leftValue,
                onValueChange = { if (filter(it)) leftValue = it },
                label = stringResource(id = R.string.label_left_padding),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = rightValue,
                onValueChange = { if (filter(it)) rightValue = it },
                label = stringResource(id = R.string.label_right_padding),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(20.dp))
                AdaptiveDialogButton(
                    text = stringResource(id = R.string.confirm),
                    onClick = {
                        val l = leftValue.toIntOrNull() ?: 0
                        val r = rightValue.toIntOrNull() ?: 0
                        onConfirm(l.coerceIn(-50, 100), r.coerceIn(-50, 100))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}
