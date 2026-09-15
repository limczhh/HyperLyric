/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.model.lyricMetadataOf

internal class SongPreprocessor(private val placeholder: TitleSlot) {

    companion object {
        internal const val MIN_PLACEHOLDER_DURATION_MS = 3_000L
    }

    fun prepare(song: Song): List<TimedLine> {
        val filled = fillGap(song)
        val lines = mutableListOf<TimedLine>()
        var prev: TimedLine? = null
        filled.lyrics?.forEach { lyric ->
            val tl = TimedLine(lyric).also {
                it.previous = prev
                prev?.next = it
            }
            lines.add(tl)
            prev = tl
        }
        return lines
    }

    /**
     * Builds a persistent placeholder for a metadata-only song. This is separate from
     * [fillGap], whose empty-song behavior deliberately keeps the legacy end-of-song title line.
     */
    internal fun noLyricsPlaceholder(song: Song): RichLyricLine? = when (placeholder) {
        TitleSlot.NONE -> null
        TitleSlot.COUNTDOWN -> countdownLine(Long.MAX_VALUE)
        TitleSlot.NAME_ARTIST,
        TitleSlot.NAME -> songTitle(song)?.let {
            titleLine(Long.MAX_VALUE, Long.MAX_VALUE, it)
        }
    }

    private fun fillGap(song: Song): Song {
        val lyrics = song.lyrics?.toMutableList() ?: mutableListOf()
        if (lyrics.isEmpty()) {
            val title = songTitle(song) ?: return song
            val d = if (song.duration > 0) song.duration else Long.MAX_VALUE
            lyrics.add(titleLine(d, d, title))
        } else {
            val first = lyrics.first()
            if (first.begin < MIN_PLACEHOLDER_DURATION_MS) return song

            var end = first.begin
            if (end > 1) end--
            val line = when (placeholder) {
                TitleSlot.NONE -> null
                TitleSlot.COUNTDOWN -> countdownLine(end)
                TitleSlot.NAME_ARTIST,
                TitleSlot.NAME -> songTitle(song)?.let { titleLine(end, end, it) }
            } ?: return song
            lyrics.add(0, line)
        }
        song.lyrics = lyrics
        return song
    }

    private fun titleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text).apply {
            metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true")
        }

    private fun countdownLine(end: Long) =
        RichLyricLine(end = end, duration = end).apply {
            metadata = lyricMetadataOf(
                METADATA_TITLE_LINE to "true",
                METADATA_COUNTDOWN_LINE to "true"
            )
        }

    private fun songTitle(song: Song): String? {
        val name = song.name
        val artist = song.artist
        return when (placeholder) {
            TitleSlot.NONE -> null
            TitleSlot.NAME_ARTIST -> when {
                !name.isNullOrBlank() && !artist.isNullOrBlank() -> "$name - $artist"
                !name.isNullOrBlank() -> name
                else -> null
            }

            TitleSlot.NAME -> name?.takeIf { it.isNotBlank() }
            TitleSlot.COUNTDOWN -> null
        }
    }
}

internal class TimedLine(val line: IRichLyricLine) : IRichLyricLine by line {
    var previous: TimedLine? = null
    var next: TimedLine? = null
}


