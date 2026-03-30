package com.lidesheng.hyperlyric.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lidesheng.hyperlyric.utils.ThemeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

class LogActivity : ComponentActivity() {


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
                "V" -> "VERBOSE"
                else -> level
            }

        val levelColorBg: Color
            get() = when (level) {
                "C" -> Color(0xFFD32F2F)
                "E" -> Color(0x40F44336)
                "W" -> Color(0x40FFC107)
                "I" -> Color(0x404CAF50)
                "D" -> Color(0x402196F3)
                "V" -> Color(0x409E9E9E)
                else -> Color(0x40909090)
            }

        val levelColorText: Color
            get() = when (level) {
                "C" -> Color(0xFFFFFFFF)
                "E" -> Color(0xFFF44336)
                "W" -> Color(0xFFFF8F00)
                "I" -> Color(0xFF388E3C)
                "D" -> Color(0xFF1976D2)
                "V" -> Color(0xFF616161)
                else -> Color(0xFF757575)
            }
    }


    private suspend fun readXposedLogs(): List<LogEntry> = withContext(Dispatchers.IO) {
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
                entries.add(LogEntry("NOW", "W", "Logger", "未找到 LSPosed 日志目录，请确认 LSPosed 已正确安装"))
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
                entries.add(LogEntry("NOW", "W", "Logger", "日志目录存在 ($dirsArg) 但未找到 .log 文件"))
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
                        val rawTime = if (timeMatcher.find()) timeMatcher.group(1) ?: "未知时间" else "未知时间"
                        val time = if (rawTime.length >= 19) rawTime.substring(5).replace('T', ' ') else rawTime

                        val levelMatcher = levelRegex.matcher(firstLine)
                        val parsedLevel = if (levelMatcher.find()) levelMatcher.group(1) ?: "I" else "I"
                        val level = when {
                            blockStr.contains(" E/", ignoreCase = true) || blockStr.contains("[E]", ignoreCase = true) || blockStr.contains("error", ignoreCase = true) || blockStr.contains("fail", ignoreCase = true) || blockStr.contains("exception", ignoreCase = true) -> "E"
                            blockStr.contains(" W/", ignoreCase = true) || blockStr.contains("[W]", ignoreCase = true) || blockStr.contains("warn", ignoreCase = true) -> "W"
                            blockStr.contains(" D/", ignoreCase = true) || blockStr.contains("[D]", ignoreCase = true) || blockStr.contains("debug", ignoreCase = true) -> "D"
                            else -> parsedLevel
                        }

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

                        entries.add(LogEntry(time, level, "LSPosed", finalMsg.trim()))
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
                entries.add(LogEntry("NOW", "I", "Logger",
                    "已扫描 ${logFiles.size} 个日志文件，未发现包含 HyperLyric 的记录。" +
                    "日志目录: $dirsArg"))
            }
        } catch (e: Exception) {
            entries.add(LogEntry("NOW", "E", "Logger", "读取 LSPosed 日志失败: ${e.message}"))
        }
        entries.sortedByDescending { it.timestamp }
    }

    private suspend fun collectAllLogs(): List<LogEntry> {
        return readXposedLogs()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        setContent {
            ThemeUtils.MiuixThemeWrapper {
                LogScreen()
            }
        }
    }

    @Composable
    fun LogScreen() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
        val hazeState = remember { HazeState() }
        val hazeStyle = HazeStyle(
            backgroundColor = MiuixTheme.colorScheme.surface,
            tint = HazeTint(MiuixTheme.colorScheme.surface.copy(0.8f))
        )
        val allLogs = remember { mutableStateListOf<LogEntry>() }
        val filteredLogs = remember { mutableStateListOf<LogEntry>() }
        val checkedStates = remember { mutableStateMapOf<Int, Boolean>() }
        var isLoading by remember { mutableStateOf(true) }
        var searchQuery by remember { mutableStateOf("") }
        var searchExpanded by remember { mutableStateOf(false) }
        var selectedLevel by remember { mutableStateOf("ALL") }
        var showMorePopup by remember { mutableStateOf(false) }
        var showFilterPopup by remember { mutableStateOf(false) }
        var showDetailDialog by remember { mutableStateOf(false) }
        var currentDetailLog by remember { mutableStateOf<LogEntry?>(null) }
        val reloadLogs = {
            coroutineScope.launch {
                isLoading = true
                val logs = collectAllLogs()
                allLogs.clear()
                allLogs.addAll(logs)
                checkedStates.clear()
                updateFilteredLogs(allLogs, searchQuery, selectedLevel, filteredLogs)
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            reloadLogs()
        }

        LaunchedEffect(searchQuery, selectedLevel) {
            updateFilteredLogs(allLogs, searchQuery, selectedLevel, filteredLogs)
        }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/plain"),
            onResult = { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                try {
                    val sb = StringBuilder()
                    sb.appendLine("========== HyperLyric 调试日志 ==========")
                    sb.appendLine("导出时间: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                    sb.appendLine()
                    val toExport = if (checkedStates.isEmpty()) filteredLogs else allLogs.filterIndexed { index, _ -> checkedStates[index] == true }
                    toExport.forEach {
                        sb.appendLine("[${it.timestamp}][${it.level}][${it.tag}]")
                        sb.appendLine(it.message)
                        sb.appendLine()
                    }
                    val output = contentResolver.openOutputStream(uri)
                    if (output != null) {
                        output.use {
                            it.write(sb.toString().toByteArray(Charsets.UTF_8))
                            it.flush()
                        }
                        Toast.makeText(this@LogActivity, "导出成功", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LogActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val hasSelection by remember { derivedStateOf { checkedStates.values.any { it } } }
        val allSelected by remember { derivedStateOf { 
            if (filteredLogs.isEmpty()) false 
            else filteredLogs.all { entry -> 
                val realIndex = allLogs.indexOf(entry)
                checkedStates[realIndex] == true 
            }
        }}
        val selectedCount by remember { derivedStateOf { checkedStates.values.count { it } } }

        Scaffold(
            topBar = {
                TopAppBar(
                    color = Color.Transparent,
                    title = "模块日志",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(
                            onClick = { finish() }
                        ) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showFilterPopup = true }, holdDownState = showFilterPopup) {
                                Icon(imageVector = MiuixIcons.Filter, contentDescription = "筛选")
                            }
                            WindowListPopup(
                                show = showFilterPopup,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showFilterPopup = false }
                            ) {
                                val dismissPopup = LocalDismissState.current
                                ListPopupColumn {
                                    val levels = listOf("ALL", "D", "I", "W", "E", "C")
                                    val levelNames = listOf("全部", "Debug", "Info", "Warn", "Error", "Crash")
                                    levels.forEachIndexed { index, level ->
                                        DropdownImpl(
                                            text = levelNames[index],
                                            optionSize = levels.size,
                                            isSelected = selectedLevel == level,
                                            onSelectedIndexChange = {
                                                dismissPopup?.invoke()
                                                selectedLevel = level
                                            },
                                            index = index
                                        )
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.padding(end = 12.dp)) {
                            IconButton(onClick = { showMorePopup = true }, holdDownState = showMorePopup) {
                                Icon(imageVector = MiuixIcons.More, contentDescription = "更多")
                            }
                            WindowListPopup(
                                show = showMorePopup,
                                alignment = PopupPositionProvider.Align.TopEnd,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = { showMorePopup = false }
                            ) {
                                val dismissPopup = LocalDismissState.current
                                ListPopupColumn {
                                    DropdownImpl(
                                        text = "刷新",
                                        optionSize = 3,
                                        isSelected = false,
                                        onSelectedIndexChange = {
                                            dismissPopup?.invoke()
                                            reloadLogs()
                                        },
                                        index = 0
                                    )
                                    DropdownImpl(
                                        text = "导出全部",
                                        optionSize = 3,
                                        isSelected = false,
                                        onSelectedIndexChange = {
                                            dismissPopup?.invoke()
                                            val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                                            checkedStates.clear()
                                            exportLauncher.launch("hyperlyric_debug_$dateTime.txt")
                                        },
                                        index = 1
                                    )
                                    DropdownImpl(
                                        text = "清空全部",
                                        optionSize = 3,
                                        isSelected = false,
                                        onSelectedIndexChange = {
                                            dismissPopup?.invoke()
                                            coroutineScope.launch {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        Runtime.getRuntime().exec(arrayOf("su", "-c", "rm -f /data/adb/lspd/log.old/modules_*.log /data/adb/lspd/log/modules_*.log")).waitFor()
                                                    } catch (_: Exception) {}
                                                }
                                                allLogs.clear()
                                                filteredLogs.clear()
                                                checkedStates.clear()
                                                Toast.makeText(context, "已彻底清空底层日志", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        index = 2
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.hazeEffect(hazeState) {
                        style = hazeStyle
                        blurRadius = 25.dp
                        noiseFactor = 0f
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollEndHaptic()
                        .hazeSource(state = hazeState)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + if (hasSelection) 80.dp else 16.dp
                    )
                ) {
                    item {
                        SearchBar(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            inputField = {
                                InputField(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = { },
                                    expanded = searchExpanded,
                                    onExpandedChange = { searchExpanded = it },
                                    label = "搜索"
                                )
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                        ) {}
                    }

                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("正在加载日志...", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                            }
                        }
                    } else if (filteredLogs.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Text("未找到相关日志", color = MiuixTheme.colorScheme.onSurfaceSecondary)
                            }
                        }
                    } else {
                        itemsIndexed(filteredLogs) { _, entry ->
                            val realIndex = allLogs.indexOf(entry)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                LogItem(
                                    entry = entry,
                                    isChecked = checkedStates[realIndex] == true,
                                    onCheckedChange = { checked ->
                                        if (checked) checkedStates[realIndex] = true else checkedStates.remove(realIndex)
                                    },
                                    onClick = {
                                        currentDetailLog = entry
                                        showDetailDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hasSelection,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                text = if (allSelected) "全不选" else "全选",
                                onClick = {
                                    val target = !allSelected
                                    filteredLogs.forEach { entry ->
                                        val realIdx = allLogs.indexOf(entry)
                                        if (target) checkedStates[realIdx] = true
                                        else checkedStates.remove(realIdx)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                text = "反选",
                                onClick = {
                                    filteredLogs.forEach { entry ->
                                        val realIdx = allLogs.indexOf(entry)
                                        if (checkedStates[realIdx] == true) checkedStates.remove(realIdx)
                                        else checkedStates[realIdx] = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(4.dp))
                            TextButton(
                                text = "导出($selectedCount)",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                                    exportLauncher.launch("hyperlyric_selected_debug_$dateTime.txt")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (showDetailDialog && currentDetailLog != null) {
                WindowDialog(
                    show = true,
                    title = "日志详情",
                    onDismissRequest = { showDetailDialog = false },
                ) {
                    val log = currentDetailLog!!
                    val dismiss = LocalDismissState.current
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0x409E9E9E), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = log.tag, fontSize = 12.sp, color = Color(0xFF616161))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(log.levelColorBg, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = log.displayLevel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = log.levelColorText
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(text = log.timestamp, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .background(MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = log.message,
                                fontSize = 14.sp,
                                color = if (log.level == "C") Color(0xFFD32F2F) else MiuixTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            TextButton(
                                text = "关闭",
                                onClick = { dismiss?.invoke() },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(16.dp))
                            TextButton(
                                text = "复制",
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    val logText = "[${log.timestamp}][${log.level}][${log.tag}]\n${log.message}"
                                    val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("HyperLyric Log", logText)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
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
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            val firstLine = entry.message.lines().firstOrNull() ?: ""
            val displaySummary = firstLine.take(20) + if(entry.message.lines().size > 1 || firstLine.length > 20) "..." else ""
            CheckboxPreference(
                title = entry.timestamp,
                summary = displaySummary,
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                endActions = {
                    Box(
                        modifier = Modifier
                            .background(entry.levelColorBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = entry.displayLevel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = entry.levelColorText
                        )
                    }
                }
            )
        }
    }
}
