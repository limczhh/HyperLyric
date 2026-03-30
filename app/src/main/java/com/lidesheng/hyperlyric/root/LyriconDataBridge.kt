package com.lidesheng.hyperlyric.root

import io.github.proify.lyricon.lyric.model.RichLyricLine

object LyriconDataBridge {
    @Volatile
    var currentSong: io.github.proify.lyricon.lyric.model.Song? = null

    @Volatile
    var currentSongName: String? = null

    @Volatile
    var currentLyric: String? = null
    
    @Volatile
    var currentLyricLine: io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine? = null
    
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

    fun updateSong(song: io.github.proify.lyricon.lyric.model.Song?) {
        isTextMode = false
        currentSong = song
        currentSongName = song?.name
        currentLyricLine = null
        currentLyric = null
    }

    fun updatePosition(position: Long): Boolean {
        if (isTextMode) return false
        val song = currentSong ?: return false
        val lyrics = song.lyrics
        if (lyrics.isNullOrEmpty()) return false

        val currentLine = lyrics.find { position in it.begin..it.end }
        currentLyricLine = currentLine
        // Bug fix #2: 间奏时保持最后一行歌词，不回退到歌名
        val newText = currentLine?.text ?: currentLyric ?: ""
        
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
    }
}
