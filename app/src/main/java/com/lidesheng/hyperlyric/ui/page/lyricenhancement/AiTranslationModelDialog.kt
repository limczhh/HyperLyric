package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lidesheng.hyperlyric.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

@Composable
internal fun AiTranslationModelDialog(
    show: Boolean,
    currentModel: String,
    baseUrl: String,
    apiKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val safeTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var dialogTopPx by remember { mutableIntStateOf(0) }
    val snackbarPositionProvider = remember(
        density,
        safeTopInset,
        dialogTopPx
    ) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val safeTopPx = with(density) { safeTopInset.roundToPx() }
                val dialogTop = dialogTopPx.takeIf { it > 0 } ?: safeTopPx
                val popupTop = (dialogTop - popupContentSize.height).coerceAtLeast(safeTopPx)
                val popupLeft = (windowSize.width - popupContentSize.width) / 2
                return IntOffset(x = popupLeft, y = popupTop)
            }
        }
    }
    var inputValue by remember(currentModel) { mutableStateOf(currentModel) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var pendingSnackbarCount by remember { mutableIntStateOf(0) }

    val apiKeyRequiredMessage = stringResource(
        R.string.toast_ai_translation_models_api_key_required
    )
    val baseUrlRequiredMessage = stringResource(
        R.string.toast_ai_translation_models_base_url_required
    )
    val modelListEmptyMessage = stringResource(R.string.toast_ai_translation_models_empty)
    val modelFetchFailedMessage = stringResource(
        R.string.toast_ai_translation_models_fetch_failed
    )

    fun showSearchMessage(message: String) {
        coroutineScope.launch {
            if (pendingSnackbarCount > 0) return@launch

            pendingSnackbarCount++
            try {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            } finally {
                pendingSnackbarCount--
            }
        }
    }

    fun searchModels() {
        when {
            apiKey.isBlank() -> showSearchMessage(apiKeyRequiredMessage)
            baseUrl.isBlank() -> showSearchMessage(baseUrlRequiredMessage)
            else -> {
                val selectedModel = inputValue
                availableModels = emptyList()
                isLoading = true
                coroutineScope.launch {
                    try {
                        val fetchedModels = OpenAiModelDiscovery.fetch(
                            baseUrl = baseUrl,
                            apiKey = apiKey
                        )
                        if (fetchedModels.isEmpty()) {
                            showSearchMessage(modelListEmptyMessage)
                        } else {
                            availableModels = (fetchedModels + selectedModel)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .sorted()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        val message = if (error is ModelListHttpException) {
                            context.getString(
                                R.string.toast_ai_translation_models_fetch_http_failed,
                                error.statusCode
                            )
                        } else {
                            modelFetchFailedMessage
                        }
                        showSearchMessage(message)
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    }

    WindowDialog(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            dialogTopPx = position.y.roundToInt()
        },
        show = true,
        title = stringResource(R.string.title_ai_translation_model),
        onDismissRequest = onDismiss
    ) {
        val popupWindowWidth = LocalWindowInfo.current.containerDpSize.width
        if (pendingSnackbarCount > 0) {
            Popup(
                popupPositionProvider = snackbarPositionProvider,
                properties = PopupProperties(
                    focusable = false,
                    clippingEnabled = false
                )
            ) {
                SnackbarHost(
                    state = snackbarHostState,
                    modifier = Modifier
                        .width(popupWindowWidth)
                        .height(96.dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        label = stringResource(R.string.title_ai_translation_model),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    InfiniteProgressIndicator()
                }
            }

            if (availableModels.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surface,
                        contentColor = MiuixTheme.colorScheme.onSurface
                    )
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(availableModels, key = { it }) { availableModel ->
                            CheckboxPreference(
                                title = availableModel,
                                checked = inputValue == availableModel,
                                onCheckedChange = { checked ->
                                    if (checked) inputValue = availableModel
                                },
                                checkboxLocation = CheckboxLocation.End
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.search),
                    onClick = ::searchModels,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = { onConfirm(inputValue) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}
