package com.lidesheng.hyperlyric.common.lyric

import com.lidesheng.hyperlyric.lyric.LrcLine

object LrcParser {
    fun parse(lrcText: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val timeRegex = Regex("\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})]")
        lrcText.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach

            val matches = timeRegex.findAll(trimmedLine).toList()
            if (matches.isNotEmpty()) {
                val content = trimmedLine.replace(timeRegex, "").trim()
                if (content.isNotEmpty()) {
                    matches.forEach { match ->
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toLong()
                        val msRaw = match.groupValues[3]
                        val ms = if (msRaw.length == 2) msRaw.toLong() * 10 else msRaw.toLong()
                        val timeMs = min * 60000 + sec * 1000 + ms
                        lines.add(LrcLine(timeMs, content))
                    }
                }
            }
        }
        return lines.sortedBy { it.startTimeMs }
    }
}
