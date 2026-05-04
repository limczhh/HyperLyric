@file:OptIn(ExperimentalScrollBarApi::class)

package com.lidesheng.hyperlyric.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.ui.component.SearchBarFake
import com.lidesheng.hyperlyric.ui.component.SearchBox
import com.lidesheng.hyperlyric.ui.component.SearchPager
import com.lidesheng.hyperlyric.ui.component.SearchStatus
import com.lidesheng.hyperlyric.ui.navigation.LocalNavigator
import com.lidesheng.hyperlyric.ui.utils.BlurredBar
import com.lidesheng.hyperlyric.ui.utils.pageScrollModifiers
import com.lidesheng.hyperlyric.ui.utils.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val isSystemInfo: Boolean = false
) {
    val displayLevel: String
        get() = when (level) {
            "C" -> "CRASH"
            "E" -> "ERROR"
            "W" -> "WARN"
            "I" -> "INFO"
            "D" -> "DEBUG"
            else -> level
        }

    val levelColorBg: Color
        get() = when (level) {
            "C" -> Color(0xFFD32F2F)
            "E" -> Color(0x40F44336)
            "W" -> Color(0x40FFC107)
            "I" -> Color(0x404CAF50)
            "D" -> Color(0x402196F3)
            else -> Color(0x40909090)
        }

    val levelColorText: Color
        get() = when (level) {
            "C" -> Color(0xFFFFFFFF)
            "E" -> Color(0xFFF44336)
            "W" -> Color(0xFFFF8F00)
            "I" -> Color(0xFF388E3C)
            "D" -> Color(0xFF1976D2)
            else -> Color(0xFF757575)
        }
}

private suspend fun readXposedLogs(context: Context): List<LogEntry> = withContext(Dispatchers.IO) {
    val entries = mutableListOf<LogEntry>()
    try {
        val findProcess = Runtime.getRuntime().exec(
            arrayOf("su", "-c",
                "ls -d /data/adb/lspd/log /data/adb/lspd/log.old 2>/dev/null || echo '__NONE__'"
            )
        )
        val foundDirs = BufferedReader(InputStreamReader(findProcess.inputStream))
            .readLines().filter { it.isNotBlank() && it != "__NONE__" }
        findProcess.waitFor()

        if (foundDirs.isEmpty()) {
            entries.add(LogEntry("NOW", "W", context.getString(R.string.tag_logger), context.getString(R.string.lsposed_not_found)))
            return@withContext entries
        }

        val dirsArg = foundDirs.joinToString(" ")
        val listProcess = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "find $dirsArg -name '*.log' -type f 2>/dev/null")
        )
        val logFiles = BufferedReader(InputStreamReader(listProcess.inputStream))
            .readLines().filter { it.isNotBlank() }
        listProcess.waitFor()

        if (logFiles.isEmpty()) {
            entries.add(LogEntry("NOW", "W", context.getString(R.string.tag_logger), context.getString(R.string.format_log_files_not_found, dirsArg)))
            return@withContext entries
        }

        val catCmd = logFiles.joinToString(" ") { "'$it'" }
        val process = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "cat $catCmd 2>/dev/null")
        )

        val timeRegex = Pattern.compile("^(?:\\[\\s*)?(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}|\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}|\\d+\\.\\d{6})")
        val levelRegex = Pattern.compile("\\s+([VDIWEC])/")

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            val currentBlock = java.lang.StringBuilder()

            fun processCurrentBlock() {
                val blockStr = currentBlock.toString()
                if (blockStr.contains("hyperlyric", ignoreCase = true) || blockStr.contains("HyperLyric")) {
                    val firstLine = blockStr.lineSequence().firstOrNull() ?: ""

                    val timeMatcher = timeRegex.matcher(firstLine)
                    val rawTime = if (timeMatcher.find()) timeMatcher.group(1) ?: context.getString(R.string.unknown_time) else context.getString(R.string.unknown_time)
                    val time = if (rawTime.length >= 19 && rawTime != context.getString(R.string.unknown_time)) rawTime.substring(5).replace('T', ' ') else rawTime

                    val levelMatcher = levelRegex.matcher(firstLine)
                    val parsedLevel = if (levelMatcher.find()) levelMatcher.group(1) ?: "I" else "I"
                    val level = when {
                        blockStr.contains(" E/", ignoreCase = true) || blockStr.contains("[E]", ignoreCase = true) || blockStr.contains("error", ignoreCase = true) || blockStr.contains("fail", ignoreCase = true) || blockStr.contains("exception", ignoreCase = true) -> "E"
                        blockStr.contains(" W/", ignoreCase = true) || blockStr.contains("[W]", ignoreCase = true) || blockStr.contains("warn", ignoreCase = true) -> "W"
                        blockStr.contains(" D/", ignoreCase = true) || blockStr.contains("[D]", ignoreCase = true) || blockStr.contains("debug", ignoreCase = true) -> "D"
                        else -> parsedLevel
                    }

                    if (level == "V") return

                    val tagStart = firstLine.indexOf("[HyperLyric]")
                    val headerMsg = if (tagStart != -1) {
                        firstLine.substring(tagStart + "[HyperLyric]".length).trim().ifEmpty { firstLine }
                    } else {
                        val lastBracket = firstLine.lastIndexOf(']')
                        if (lastBracket != -1 && lastBracket < firstLine.length - 1) {
                            firstLine.substring(lastBracket + 1).trim()
                        } else firstLine
                    }

                    val remainingLines = if (blockStr.contains('\n')) blockStr.substringAfter('\n') else ""
                    val finalMsg = if (remainingLines.isNotBlank()) {
                        if (headerMsg.isNotEmpty() && headerMsg != firstLine) "$headerMsg\n$remainingLines" else "$headerMsg\n$remainingLines"
                    } else {
                        headerMsg
                    }

                    entries.add(LogEntry(time, level, context.getString(R.string.tag_lsposed), finalMsg.trim()))
                }
            }

            reader.lineSequence().forEach { line ->
                if (timeRegex.matcher(line).find()) {
                    if (currentBlock.isNotEmpty()) {
                        processCurrentBlock()
                        currentBlock.clear()
                    }
                }
                if (currentBlock.isNotEmpty()) currentBlock.append("\n")
                currentBlock.append(line)
            }
            if (currentBlock.isNotEmpty()) {
                processCurrentBlock()
            }
        }
        process.waitFor()

        if (entries.isEmpty()) {
            entries.add(LogEntry("NOW", "I", context.getString(R.string.tag_logger),
                context.getString(R.string.format_logs_scanned_no_match, logFiles.size, dirsArg)))
        }
    } catch (e: Exception) {
        val msg = if (e.message?.contains("Permission denied") == true ||
                      e.message?.contains("su:") == true ||
                      e.message?.contains("not found") == true) {
            context.getString(R.string.no_root_permission)
        } else {
            context.getString(R.string.format_log_read_failed, e.message)
        }
        entries.add(LogEntry("NOW", "E", context.getString(R.string.tag_logger), msg))
    }
    entries.sortedByDescending { it.timestamp }
}

private suspend fun collectAllLogs(context: Context): List<LogEntry> {
    return readXposedLogs(context)
}

private fun formatTimestamp(raw: String): String {
    val dotIndex = raw.lastIndexOf('.')
    return if (dotIndex != -1 && raw.length - dotIndex == 4) raw.substring(0, dotIndex) else raw
}

@Composable
fun LogPage() {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val allLogs = remember { mutableStateListOf<LogEntry>() }
    val filteredLogs = remember { mutableStateListOf<LogEntry>() }
    var isLoading by remember { mutableStateOf(true) }
    val searchLabel = stringResource(id = R.string.search)
    var searchStatus by remember { mutableStateOf(SearchStatus(label = searchLabel)) }
    var selectedLevel by remember { mutableStateOf("ALL") }
    val density = LocalDensity.current
    val pullToRefreshState = rememberPullToRefreshState()
    var showMorePopup by remember { mutableStateOf(false) }

    val exportHeader = stringResource(id = R.string.export_header)
    val exportTimeFormat = stringResource(id = R.string.format_export_time)
    val exportSuccessMsg = stringResource(id = R.string.export_success)
    val exportFailedMsg = stringResource(id = R.string.format_export_failed)
    val copiedMsg = stringResource(id = R.string.copied)

    val reloadLogs = {
        coroutineScope.launch {
            isLoading = true
            val logs = collectAllLogs(context)
            allLogs.clear()
            allLogs.addAll(logs)
            updateFilteredLogs(allLogs, searchStatus.searchText, selectedLevel, filteredLogs)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reloadLogs()
    }

    LaunchedEffect(searchStatus.searchText, selectedLevel) {
        updateFilteredLogs(allLogs, searchStatus.searchText, selectedLevel, filteredLogs)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val sb = StringBuilder()
                sb.appendLine(exportHeader)
                sb.appendLine(String.format(exportTimeFormat, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                sb.appendLine()
                filteredLogs.forEach {
                    sb.appendLine("[${it.timestamp}][${it.level}][${it.tag}]")
                    sb.appendLine(it.message)
                    sb.appendLine()
                }
                val output = context.contentResolver.openOutputStream(uri)
                if (output != null) {
                    output.use {
                        it.write(sb.toString().toByteArray(Charsets.UTF_8))
                        it.flush()
                    }
                    Toast.makeText(context, exportSuccessMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, String.format(exportFailedMsg, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            BlurredBar(backdrop, blurActive) {
                searchStatus.TopAppBarAnim(backgroundColor = barColor) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(id = R.string.title_module_logs),
                        scrollBehavior = topAppBarScrollBehavior,
                        navigationIcon = {
                            IconButton(
                                onClick = { navigator.pop() }
                            ) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = stringResource(id = R.string.back))
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showMorePopup = true }, holdDownState = showMorePopup) {
                                    Icon(imageVector = MiuixIcons.More, contentDescription = stringResource(id = R.string.more))
                                }
                                WindowListPopup(
                                    show = showMorePopup,
                                    alignment = PopupPositionProvider.Align.TopEnd,
                                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                    onDismissRequest = { showMorePopup = false }
                                ) {
                                    val dismissPopup = LocalDismissState.current
                                    ListPopupColumn {
                                        val levels = listOf("ALL", "D", "I", "W", "E", "C")
                                        val levelNames = listOf(
                                            stringResource(id = R.string.all),
                                            stringResource(id = R.string.level_debug),
                                            stringResource(id = R.string.level_info),
                                            stringResource(id = R.string.level_warn),
                                            stringResource(id = R.string.level_error),
                                            stringResource(id = R.string.level_crash)
                                        )
                                        val totalOptions = levels.size + 1
                                        levels.forEachIndexed { index, level ->
                                            DropdownImpl(
                                                text = levelNames[index],
                                                optionSize = totalOptions,
                                                isSelected = selectedLevel == level,
                                                onSelectedIndexChange = {
                                                    dismissPopup?.invoke()
                                                    selectedLevel = level
                                                },
                                                index = index
                                            )
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        DropdownImpl(
                                            text = stringResource(id = R.string.export_all),
                                            optionSize = totalOptions,
                                            isSelected = false,
                                            onSelectedIndexChange = {
                                                dismissPopup?.invoke()
                                                val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                                                exportLauncher.launch("hyperlyric_debug_$dateTime.txt")
                                            },
                                            index = levels.size
                                        )
                                    }
                                }
                            }
                        },
                        bottomContent = {
                            Box(
                                modifier = Modifier
                                    .alpha(if (searchStatus.isCollapsed()) 1f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        with(density) {
                                            val newOffsetY = coordinates.positionInWindow().y.toDp()
                                            if (searchStatus.offsetY != newOffsetY) {
                                                searchStatus = searchStatus.copy(offsetY = newOffsetY)
                                            }
                                        }
                                    }
                                    .then(
                                        if (searchStatus.isCollapsed()) {
                                            Modifier.pointerInput(Unit) {
                                                detectTapGestures {
                                                    searchStatus = searchStatus.copy(current = SearchStatus.Status.EXPANDING)
                                                }
                                            }
                                        } else Modifier
                                    )
                            ) {
                                SearchBarFake(stringResource(id = R.string.search))
                            }
                        }
                    )
                }
            }
        },
        popupHost = {
            searchStatus.SearchPager(
                onSearchStatusChange = { searchStatus = it },
            ) {
                if (searchStatus.searchText.isNotBlank()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (isLoading) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (filteredLogs.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(id = R.string.no_logs_found), color = MiuixTheme.colorScheme.onSurfaceSecondary)
                                }
                            }
                        } else {
                            items(filteredLogs) { entry ->
                                LogItem(entry = entry, copiedMsg = copiedMsg)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            searchStatus.SearchBox {
                PullToRefresh(
                    isRefreshing = isLoading,
                    onRefresh = { reloadLogs() },
                    pullToRefreshState = pullToRefreshState,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    refreshTexts = listOf(
                        stringResource(id = R.string.refresh_pull_down),
                        stringResource(id = R.string.refresh_release),
                        stringResource(id = R.string.refreshing),
                        stringResource(id = R.string.refresh_success)
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val lazyListState = rememberLazyListState()
                    val top = padding.calculateTopPadding()
                    val bottom = padding.calculateBottomPadding()
                    val contentPadding = remember(top, bottom) {
                        PaddingValues(top = top, bottom = bottom + 16.dp)
                    }
                    Box {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.pageScrollModifiers(true, true, topAppBarScrollBehavior),
                            contentPadding = contentPadding
                        ) {
                            if (isLoading) {
                                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                            } else if (filteredLogs.isEmpty()) {
                                item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(id = R.string.no_logs_found), color = MiuixTheme.colorScheme.onSurfaceSecondary) } }
                            } else {
                                items(filteredLogs) { entry -> LogItem(entry = entry, copiedMsg = copiedMsg) }
                            }
                        }
                        VerticalScrollBar(
                            adapter = rememberScrollBarAdapter(lazyListState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            trackPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
}

private fun updateFilteredLogs(
    all: List<LogEntry>,
    query: String,
    level: String,
    filtered: MutableList<LogEntry>
) {
    val q = query.lowercase(Locale.getDefault())
    filtered.clear()
    filtered.addAll(
        all.filter {
            val matchLevel = level == "ALL" || it.level == level
            val matchQuery = q.isEmpty() || it.message.lowercase(Locale.getDefault()).contains(q) || it.tag.lowercase(Locale.getDefault()).contains(q)
            matchLevel && matchQuery
        }
    )
}

@Composable
fun LogItem(
    entry: LogEntry,
    copiedMsg: String
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        insideMargin = PaddingValues(12.dp),
        showIndication = true,
        onClick = { expanded = !expanded },
        onLongPress = {
            val logText = "[${entry.timestamp}][${entry.level}][${entry.tag}]\n${entry.message}"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("HyperLyric Log", logText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(entry.levelColorBg, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = entry.displayLevel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = entry.levelColorText
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTimestamp(entry.timestamp),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modifier = Modifier.animateContentSize(),
            text = entry.message,
            fontSize = 13.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            color = if (entry.level == "E" || entry.level == "C") Color(0xFFF44336) else MiuixTheme.colorScheme.onSurface
        )
    }
}
