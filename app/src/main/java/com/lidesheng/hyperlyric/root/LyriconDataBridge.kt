package com.lidesheng.hyperlyric.root

import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.view.InterludeTracker
import io.github.proify.lyricon.lyric.view.SongPreprocessor
import io.github.proify.lyricon.lyric.view.TimedLine
import io.github.proify.lyricon.lyric.view.TitleSlot

object LyriconDataBridge {
    @Volatile
    var currentSong: Song? = null

    @Volatile
    var currentSongName: String? = null

    @Volatile
    var currentLyric: String? = null

    @Volatile
    var currentLyricLine: IRichLyricLine? = null

    @Volatile
    var activePackageName: String? = null

    @Volatile
    var isPlaying: Boolean = false

    /** 是否处于纯文本模式（椒盐音乐等通过 onSendText 推送） */
    @Volatile
    var isTextMode: Boolean = false

    /** 是否显示翻译（由插件回调控制） */
    @Volatile
    var isDisplayTranslation: Boolean = true

    private var timingNavigator: TimingNavigator<TimedLine> = TimingNavigator(emptyArray())
    private var interludeTracker = InterludeTracker(8_000L)

    fun updateSong(song: Song?) {
        isTextMode = false
        currentSong = song
        currentSongName = song?.name
        currentLyricLine = null
        currentLyric = null

        if (song != null) {
            val processor = SongPreprocessor(TitleSlot.NAME_ARTIST)
            val lines = processor.prepare(song)
            timingNavigator = TimingNavigator(lines.toTypedArray())
            interludeTracker = InterludeTracker(8_000L)
        } else {
            timingNavigator = TimingNavigator(emptyArray())
        }
    }

    fun updatePosition(position: Long): Boolean {
        if (isTextMode) return false
        val song = currentSong ?: return false
        val lyrics = song.lyrics
        if (lyrics.isNullOrEmpty()) return false

        // 使用 TimingNavigator 高效定位当前歌词行
        var foundLine: IRichLyricLine? = null
        timingNavigator.forEachAtOrPrevious(position) { timedLine ->
            foundLine = timedLine
        }

        currentLyricLine = foundLine
        // 间奏时保持最后一行歌词，不回退到歌名
        val newText = foundLine?.text ?: currentLyric ?: ""

        if (newText != currentLyric) {
            currentLyric = newText
            return true
        }
        return false
    }

    fun updateLyric(text: String?) {
        isTextMode = true
        currentLyric = text
        currentLyricLine = if (!text.isNullOrBlank()) {
            RichLyricLine(text = text)
        } else {
            null
        }
    }

    fun clearAll() {
        currentSong = null
        currentSongName = null
        currentLyric = null
        currentLyricLine = null
        isTextMode = false
        timingNavigator = TimingNavigator(emptyArray())
    }
}
