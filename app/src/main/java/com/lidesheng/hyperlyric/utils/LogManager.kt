package com.lidesheng.hyperlyric.utils

import android.content.Context
import android.util.Log
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.ui.page.log.LogEntry
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.regex.Pattern
import kotlin.concurrent.read
import kotlin.concurrent.write

object LogManager : HyperLogger {
    private const val LOG_FILE_NAME = "app_logs.log"
    private const val MAX_LOG_SIZE = 2 * 1024 * 1024L // 2MB

    // 日志等级：0=一般(I+W+E), 1=调试(D+I+W+E)
    private const val LEVEL_NORMAL = 0
    private const val LEVEL_DEBUG = 1

    private val lock = ReentrantReadWriteLock()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private var logFile: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, LOG_FILE_NAME)
    }

    // ========================= 写入 =========================

    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        if (shouldWrite("D")) writeLog("D", tag, msg)
    }

    override fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeLog("I", tag, msg)
    }

    override fun w(tag: String, msg: String, e: Throwable?) {
        val fullMsg = if (e != null) "$msg: ${e.message}" else msg
        Log.w(tag, fullMsg, e)
        writeLog("W", tag, fullMsg)
    }

    override fun e(tag: String, msg: String, e: Throwable?) {
        val fullMsg = if (e != null) "$msg: ${e.message}" else msg
        Log.e(tag, fullMsg, e)
        writeLog("E", tag, fullMsg)
    }

    fun clearLogs() {
        val file = logFile ?: return
        lock.write {
            try {
                if (file.exists()) file.writeText("")
            } catch (_: Exception) {
            }
        }
    }

    private fun shouldWrite(level: String): Boolean {
        val prefs = appContext?.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE) ?: return true
        val logLevel = prefs.getInt(UIConstants.KEY_LOG_LEVEL, UIConstants.DEFAULT_LOG_LEVEL)
        if (logLevel == LEVEL_DEBUG) return true
        return level == "I" || level == "W" || level == "E" || level == "C"
    }

    private fun writeLog(level: String, tag: String, message: String) {
        val file = logFile ?: return
        lock.write {
            try {
                if (file.exists() && file.length() > MAX_LOG_SIZE) {
                    trimLogFile(file)
                }
                val timestamp = dateFormat.format(Date())
                val line = "$timestamp $level/$tag: $message\n"
                file.appendText(line)
            } catch (_: Exception) {
            }
        }
    }

    private fun trimLogFile(file: File) {
        try {
            val lines = file.readLines()
            val keepCount = lines.size / 2
            if (keepCount > 0) {
                file.writeText(lines.subList(lines.size - keepCount, lines.size).joinToString("\n") + "\n")
            }
        } catch (_: Exception) {
        }
    }

    // ========================= 读取 =========================

    suspend fun readAppLogs(): List<LogEntry> = withContext(Dispatchers.IO) {
        val file = logFile ?: return@withContext emptyList()
        if (!file.exists()) return@withContext emptyList()

        val entries = mutableListOf<LogEntry>()
        val regex = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) ([DIWEC])/(\S+): (.+)$""")

        lock.read {
            try {
                file.forEachLine { line ->
                    val match = regex.find(line)
                    if (match != null) {
                        val (time, level, tag, msg) = match.destructured
                        entries.add(
                            LogEntry(
                                timestamp = time,
                                level = level,
                                tag = tag,
                                message = msg,
                                source = "HyperLyric",
                                rawLog = line
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
        entries.reversed()
    }

    suspend fun readModuleLogs(context: Context): List<LogEntry> {
        return readXposedLogs(context)
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
                val msg = context.getString(R.string.lsposed_not_found)
                entries.add(LogEntry("NOW", "W", context.getString(R.string.tag_logger), msg, rawLog = msg))
                return@withContext entries
            }

            val dirsArg = foundDirs.joinToString(" ")
            val listProcess = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "find $dirsArg -name '*.log' ! -name 'kmsg*' -type f 2>/dev/null")
            )
            val logFiles = BufferedReader(InputStreamReader(listProcess.inputStream))
                .readLines().filter { it.isNotBlank() }
            listProcess.waitFor()

            if (logFiles.isEmpty()) {
                val msg = context.getString(R.string.format_log_files_not_found, dirsArg)
                entries.add(LogEntry("NOW", "W", context.getString(R.string.tag_logger), msg, rawLog = msg))
                return@withContext entries
            }

            val catCmd = logFiles.joinToString(" ") { "'$it'" }
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "cat $catCmd 2>/dev/null")
            )

            val timeRegex = Pattern.compile("^(?:\\[\\s*)?(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}|\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}|\\d+\\.\\d{6})")
            val levelRegex = Pattern.compile("\\s+([VDIWEC])/")
            val lsposedRegex = Pattern.compile("\\(([^)]+)\\)\\[([^,\\]]+)")
            val processRegex = Pattern.compile("\\(([^)]+)\\)")

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val currentBlock = java.lang.StringBuilder()

                fun processCurrentBlock() {
                    val blockStr = currentBlock.toString()
                    val isHyperLyric = blockStr.contains("hyperlyric", ignoreCase = true) || blockStr.contains("HyperLyric")
                    val isSystemUi = blockStr.contains("systemui", ignoreCase = true) || blockStr.contains("SystemUI")
                    val isLyricon = blockStr.contains("io.github.proify.lyricon", ignoreCase = true)
                    val isSystemUiCrash = isSystemUi &&
                                          (blockStr.contains("crash", ignoreCase = true) || blockStr.contains("fatal exception", ignoreCase = true))

                    if (isHyperLyric || isSystemUi || isLyricon) {
                        val firstLine = blockStr.lineSequence().firstOrNull() ?: ""

                        val timeMatcher = timeRegex.matcher(firstLine)
                        val rawTime = if (timeMatcher.find()) timeMatcher.group(1) ?: context.getString(R.string.unknown_time) else context.getString(R.string.unknown_time)
                        val time = if (rawTime.length >= 19 && rawTime != context.getString(R.string.unknown_time)) rawTime.substring(5).replace('T', ' ') else rawTime

                        val levelMatcher = levelRegex.matcher(firstLine)
                        val parsedLevel = if (levelMatcher.find()) levelMatcher.group(1) ?: "I" else "I"
                        val level = when {
                            isSystemUiCrash -> "C"
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

                        val lsposedMatcher = lsposedRegex.matcher(firstLine)
                        val source = if (lsposedMatcher.find()) {
                            lsposedMatcher.group(2) ?: "com.lidesheng.hyperlyric"
                        } else {
                            val processMatcher = processRegex.matcher(firstLine)
                            if (processMatcher.find()) {
                                processMatcher.group(1) ?: "com.lidesheng.hyperlyric"
                            } else if (isSystemUi) {
                                "com.android.systemui"
                            } else {
                                "com.lidesheng.hyperlyric"
                            }
                        }
                        val tag = if (isSystemUi && !isHyperLyric && !isLyricon) context.getString(R.string.tag_logger) else context.getString(R.string.tag_lsposed)
                        entries.add(LogEntry(time, level, tag, finalMsg.trim(), source = source, rawLog = blockStr))
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
                val msg = context.getString(R.string.format_logs_scanned_no_match, logFiles.size, dirsArg)
                entries.add(LogEntry("NOW", "I", context.getString(R.string.tag_logger), msg, rawLog = msg))
            }
        } catch (e: Exception) {
            val msg = if (e.message?.contains("Permission denied") == true ||
                          e.message?.contains("su:") == true ||
                          e.message?.contains("not found") == true) {
                context.getString(R.string.no_root_permission)
            } else {
                context.getString(R.string.format_log_read_failed, e.message)
            }
            entries.add(LogEntry("NOW", "E", context.getString(R.string.tag_logger), msg, rawLog = msg))
        }
        val sortedList = entries.sortedByDescending { it.timestamp }
        sortedList.mapIndexed { index, entry ->
            entry.copy(id = "log_${index}_${entry.timestamp}")
        }
    }
}
