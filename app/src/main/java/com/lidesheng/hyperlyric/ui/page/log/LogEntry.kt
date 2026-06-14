package com.lidesheng.hyperlyric.ui.page.log

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val tag: String,
    val message: String,
    val isSystemInfo: Boolean = false,
    val source: String = "com.lidesheng.hyperlyric",
    val rawLog: String = "",
    val id: String = ""
) {
    val displaySource: String
        get() = when {
            source == "com.lidesheng.hyperlyric" || source == "HyperLyric" -> "HyperLyric"
            else -> source
        }
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
