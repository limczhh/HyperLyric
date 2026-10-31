/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.interfaces.ILyricTiming
import java.text.BreakIterator
import java.util.Locale

internal class RelativeWordBuilder {
    fun build(
        timing: ILyricTiming,
        text: String?,
        words: List<LyricWord>?
    ): List<LyricWord>? {
        if (words.isNullOrEmpty() && !text.isNullOrBlank()
            && timing.begin < timing.end && timing.begin >= 0
        ) {
            return buildRelativeWords(timing, splitIntoUnits(text))
        }
        return words
    }

    private fun buildRelativeWords(timing: ILyricTiming, units: List<String>): List<LyricWord> {
        if (units.isEmpty()) return emptyList()

        val totalDuration = timing.end - timing.begin
        val unitCount = minOf(units.size.toLong(), totalDuration).toInt().coerceAtLeast(1)
        val resolvedUnits = if (unitCount == units.size) {
            units
        } else {
            units.chunkedBy(unitCount)
        }
        val baseDuration = totalDuration / resolvedUnits.size
        val remainder = totalDuration % resolvedUnits.size
        var cursor = timing.begin

        return resolvedUnits.mapIndexed { index, unit ->
            val duration = baseDuration + if (index < remainder) 1L else 0L
            val end = cursor + duration
            LyricWord(
                text = unit,
                begin = cursor,
                end = end,
                duration = duration
            ).also {
                cursor = end
            }
        }
    }

    /**
     * Generates display units without cutting a grapheme cluster. ASCII words stay together so
     * an English line does not reveal one letter at a time, while CJK characters remain separate.
     */
    private fun splitIntoUnits(text: String): List<String> {
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val clusters = buildList {
            var start = boundaries.first()
            var end = boundaries.next()
            while (end != BreakIterator.DONE) {
                add(text.substring(start, end))
                start = end
                end = boundaries.next()
            }
        }
        if (clusters.isEmpty()) return emptyList()

        val units = ArrayList<UnitBuilder>()
        val pendingWhitespace = StringBuilder()
        for (cluster in clusters) {
            if (cluster.all { it.isWhitespace() }) {
                pendingWhitespace.append(cluster)
                continue
            }

            val previous = units.lastOrNull()
            when {
                cluster.isAsciiWordCluster() && pendingWhitespace.isEmpty() &&
                        previous != null && previous.kind == UnitKind.ASCII_WORD -> {
                    previous.text.append(cluster)
                }

                cluster.isPunctuation() && pendingWhitespace.isEmpty() && previous != null -> {
                    previous.text.append(cluster)
                    previous.kind = UnitKind.OTHER
                }

                else -> {
                    units += UnitBuilder(
                        text = StringBuilder(pendingWhitespace.toString() + cluster),
                        kind = if (cluster.isAsciiWordCluster()) {
                            UnitKind.ASCII_WORD
                        } else {
                            UnitKind.OTHER
                        }
                    )
                    pendingWhitespace.clear()
                }
            }
        }
        if (pendingWhitespace.isNotEmpty() && units.isNotEmpty()) {
            units.last().text.append(pendingWhitespace)
        }

        return units.map { it.text.toString() }.ifEmpty { listOf(text) }
    }

    private fun List<String>.chunkedBy(count: Int): List<String> = List(count) { index ->
        val start = (index.toLong() * size / count).toInt()
        val end = ((index + 1L) * size / count).toInt()
        subList(start, end).joinToString("")
    }

    private fun String.isAsciiWordCluster(): Boolean = length == 1 &&
            first().isLetterOrDigit() && first().code < 128

    private fun String.isPunctuation(): Boolean {
        val type = Character.getType(codePointAt(0))
        return type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                type == Character.DASH_PUNCTUATION.toInt() ||
                type == Character.START_PUNCTUATION.toInt() ||
                type == Character.END_PUNCTUATION.toInt() ||
                type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                type == Character.OTHER_PUNCTUATION.toInt()
    }

    private enum class UnitKind { ASCII_WORD, OTHER }

    private data class UnitBuilder(
        var text: StringBuilder,
        var kind: UnitKind
    )
}


