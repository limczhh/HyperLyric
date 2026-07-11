package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.lyric.source.StateResetter
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.lyric.model.RichLyricLine
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.extensions.TimingNavigator
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.InterludeTracker
import com.lidesheng.hyperlyric.lyric.view.SongPreprocessor
import com.lidesheng.hyperlyric.lyric.view.TimedLine
import com.lidesheng.hyperlyric.lyric.view.TitleSlot

object LyriconDataBridge : StateResetter {

    val versionCounter = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    var currentSong: Song? = null

    @Volatile
    var currentSongName: String? = null

    @Volatile
    var currentLyric: String? = null

    @Volatile
    var currentLyricLine: IRichLyricLine? = null

    @Volatile
    var currentNextLyricLine: IRichLyricLine? = null

    @Volatile
    var currentPosition: Long = 0L

    @Volatile
    var activePackageName: String? = null

    @Volatile
    var currentLyricPackageName: String? = null

    /** 是否处于纯文本模式（椒盐音乐等通过 onSendText 推送） */
    @Volatile
    var isTextMode: Boolean = false

    /** 是否显示翻译（由插件回调控制；AI 翻译成功也会置 true） */
    @Volatile
    var isDisplayTranslation: Boolean = true

    /** 是否显示罗马音（由插件回调控制） */
    @Volatile
    var isDisplayRoma: Boolean = true

    /** AI 翻译完成后的回调，由 LyriconSource 设置 */
    var onAiTranslationComplete: (() -> Unit)? = null

    fun updateLyricPackage(packageName: String?) {
        activePackageName = packageName
        currentLyricPackageName = packageName
    }

    private var timingNavigator: TimingNavigator<TimedLine> = TimingNavigator(emptyArray())
    private var interludeTracker = InterludeTracker(8_000L)

    fun updateSong(song: Song?) {
        HookLogger.d("LyriconDataBridge", "歌曲变更: ${song?.name}")
        isTextMode = false
        currentSong = song
        currentSongName = song?.name
        currentLyric = null
        currentLyricLine = null
        currentNextLyricLine = null

        versionCounter.incrementAndGet()

        if (song != null) {
            val processor = SongPreprocessor(TitleSlot.NAME_ARTIST)
            val lines = processor.prepare(song)
            timingNavigator = TimingNavigator(lines.toTypedArray())
            interludeTracker = InterludeTracker(8_000L)
        } else {
            timingNavigator = TimingNavigator(emptyArray())
        }
    }

    fun applyTranslation(translatedSong: Song) {
        currentSong = translatedSong
        val processor = SongPreprocessor(TitleSlot.NAME_ARTIST)
        val lines = processor.prepare(translatedSong)
        timingNavigator = TimingNavigator(lines.toTypedArray())
        isDisplayTranslation = true
    }

    fun updatePosition(position: Long): Boolean {
        currentPosition = position
        if (isTextMode) return false
        val song = currentSong ?: return false
        val lyrics = song.lyrics
        if (lyrics.isNullOrEmpty()) return false

        // 使用 TimingNavigator 高效定位当前歌词行
        var foundLine: TimedLine? = null
        timingNavigator.forEachAtOrPrevious(position) { timedLine ->
            foundLine = timedLine
        }

        currentLyricLine = foundLine
        currentNextLyricLine = foundLine?.next
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
            val lines = text.lines()
            RichLyricLine(
                text = lines.first(),
                translation = lines.getOrNull(1)
            )
        } else {
            null
        }
        currentNextLyricLine = null
    }

    fun updateLyricLine(line: IRichLyricLine) {
        isTextMode = false
        currentLyricLine = line
        currentNextLyricLine = null
        currentLyric = line.text
    }

    override fun clearState() {
        currentSong = null
        currentSongName = null
        currentLyric = null
        currentLyricLine = null
        currentNextLyricLine = null
        currentPosition = 0L
        activePackageName = null
        currentLyricPackageName = null
        isTextMode = false
        isDisplayTranslation = true
        isDisplayRoma = true
        timingNavigator = TimingNavigator(emptyArray())

        versionCounter.incrementAndGet()
    }

}


