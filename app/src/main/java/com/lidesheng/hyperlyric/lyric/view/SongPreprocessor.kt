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

        /** 触发间奏倒计时的最小行间隔 */
        internal const val MIN_INTERLUDE_GAP_MS = 4_000L

        /** 上一行结束到间奏倒计时出现之间的延迟 */
        internal const val INTERLUDE_COUNTDOWN_DELAY_MS = 1_000L

        private val LEADING_MUSIC_INFO_PATTERN = Regex(
            """^[\s\[\]【】()（）「」『』♪♫♬·•\-]*(?:(?:作词(?:人)?|作詞(?:者)?|作曲(?:者)?|词曲|詞曲|编曲|編曲|制作人|製作人|监制|監製|出品人|录音|錄音|混音|母带|母帶|和声|和聲|演唱|演奏|企划|企劃|统筹|統籌|词|詞|曲|lyricist|composer|arranger|producer)\s*[:：=／/]\s*|(?:lyrics|music|written|composed|arranged|produced)\s+by\b\s*)""",
            RegexOption.IGNORE_CASE
        )
    }

    fun prepare(song: Song): PreparedSongLyrics {
        val lyrics = timelineLyrics(song)
        val lines = mutableListOf<TimedLine>()
        var previous: TimedLine? = null
        lyrics.forEach { lyric ->
            val timedLine = TimedLine(lyric).also {
                it.previous = previous
                previous?.next = it
            }
            lines.add(timedLine)
            previous = timedLine
        }

        val firstSungLyric = song.lyrics.orEmpty().firstOrNull {
            hasRenderableContent(it) && !isLeadingMusicInfo(it)
        }
        val leadingPlaceholder = firstSungLyric
            ?.takeIf { it.begin >= MIN_PLACEHOLDER_DURATION_MS }
            ?.let { leadingPlaceholderLine(song, it) }
            ?.let(::TimedLine)

        return PreparedSongLyrics(
            lines = lines,
            leadingPlaceholder = leadingPlaceholder,
            interludeCountdowns = interludeCountdowns(lines),
            hasRenderableLyrics = lines.isNotEmpty()
        )
    }

    /**
     * 为 AMLL TTML 歌词中的长间奏合成倒计时占位行。
     *
     * 相邻两行都来自 AMLL TTML Database、且后一行开始时间与前一行结束时间之差不小于
     * [MIN_INTERLUDE_GAP_MS] 时，在前一行结束后 [INTERLUDE_COUNTDOWN_DELAY_MS] 处生成一行，
     * 覆盖到后一行开始为止。占位符格式不是倒计时圆点时返回空列表，间奏保持既有表现。
     *
     * 合成行不进入主时间轴，宿主单独维护其检索，因此不影响真实歌词行的检索与可用性判定。
     */
    private fun interludeCountdowns(lines: List<TimedLine>): List<TimedLine> {
        if (placeholder != TitleSlot.COUNTDOWN) return emptyList()
        val countdowns = mutableListOf<TimedLine>()
        for (index in 0 until lines.size - 1) {
            val current = lines[index]
            val next = lines[index + 1]
            if (!current.isAmllTtmlLine() || !next.isAmllTtmlLine()) continue
            if (next.begin - current.end < MIN_INTERLUDE_GAP_MS) continue
            val begin = current.end + INTERLUDE_COUNTDOWN_DELAY_MS
            if (begin >= next.begin) continue
            countdowns.add(TimedLine(countdownPlaceholderLine(begin, next.begin)))
        }
        return countdowns
    }

    /**
     * Builds a persistent placeholder for a metadata-only song. This is independent of the
     * timed prelude placeholder and does not make a lyric-less source pass the lyric gate.
     */
    internal fun noLyricsPlaceholder(song: Song): RichLyricLine? = when (placeholder) {
        TitleSlot.NONE -> null
        TitleSlot.COUNTDOWN -> countdownPlaceholderLine(0L, Long.MAX_VALUE)
        TitleSlot.NAME_ARTIST,
        TitleSlot.NAME -> songTitle(song)?.let {
            titleLine(Long.MAX_VALUE, Long.MAX_VALUE, it)
        }
    }

    private fun timelineLyrics(song: Song): List<RichLyricLine> {
        return song.lyrics.orEmpty().filter(::hasRenderableContent)
    }

    private fun leadingPlaceholderLine(song: Song, firstLyric: RichLyricLine): RichLyricLine? {
        val end = firstLyric.begin
        return when (placeholder) {
            TitleSlot.NONE -> null
            TitleSlot.COUNTDOWN -> countdownPlaceholderLine(0L, end)
            TitleSlot.NAME_ARTIST,
            TitleSlot.NAME -> songTitle(song)?.let {
                titleLine(end, end, it)
            }
        }
    }

    private fun isLeadingMusicInfo(line: RichLyricLine): Boolean {
        if (line.isTitleLine()) return true
        val text = line.text?.takeIf { it.isNotBlank() }
            ?: line.words?.joinToString("") { it.text.orEmpty() }
                ?.takeIf { it.isNotBlank() }
            ?: return false
        return LEADING_MUSIC_INFO_PATTERN.containsMatchIn(text.trim())
    }

    private fun hasRenderableContent(line: RichLyricLine): Boolean =
        !line.text.isNullOrBlank() || !line.words.isNullOrEmpty()

    private fun titleLine(end: Long, duration: Long, text: String) =
        RichLyricLine(end = end, duration = duration, text = text).apply {
            metadata = lyricMetadataOf(METADATA_TITLE_LINE to "true")
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

internal data class PreparedSongLyrics(
    val lines: List<TimedLine>,
    val leadingPlaceholder: TimedLine?,
    /** AMLL TTML 长间奏的倒计时占位行，独立于 [lines]，不参与真实歌词行检索 */
    val interludeCountdowns: List<TimedLine>,
    val hasRenderableLyrics: Boolean
)

internal class TimedLine(val line: IRichLyricLine) : IRichLyricLine by line {
    var previous: TimedLine? = null
    var next: TimedLine? = null
}
