package com.lidesheng.hyperlyric.root

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.root.aitrans.AITranslator
import com.lidesheng.hyperlyric.root.utils.Constants as RootConstants
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.extensions.TimingNavigator
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.style.AiTranslationConfigs
import io.github.proify.lyricon.lyric.style.AiTranslationProvider
import io.github.proify.lyricon.lyric.view.InterludeTracker
import io.github.proify.lyricon.lyric.view.SongPreprocessor
import io.github.proify.lyricon.lyric.view.TimedLine
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.proify.lyricon.lyric.view.TitleSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object LyriconDataBridge {
    private val aiTransScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 状态版本号，用于在异步恢复后校验数据是否已过时
    private val versionCounter = java.util.concurrent.atomic.AtomicInteger(0)

    // 当前执行中的 AI 翻译协程任务
    private var activeAiTranslationJob: Job? = null

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

    /** 是否显示翻译（由插件回调控制；AI 翻译成功也会置 true） */
    @Volatile
    var isDisplayTranslation: Boolean = true

    /** 是否显示罗马音（由插件回调控制） */
    @Volatile
    var isDisplayRoma: Boolean = true

    /** 标记 isDisplayTranslation 是否由 AI 翻译置 true，用于 AI 关闭后正确复位 */
    private var aiSetDisplayTranslation: Boolean = false

    private var timingNavigator: TimingNavigator<TimedLine> = TimingNavigator(emptyArray())
    private var interludeTracker = InterludeTracker(8_000L)

    fun updateSong(song: Song?, prefs: SharedPreferences? = null) {
        HookLogger.d("LyriconDataBridge", "歌曲变更: ${song?.name}")
        isTextMode = false
        currentSong = song
        currentSongName = song?.name
        currentLyric = null

        // 每次切歌，自增版本号并取消上一次的翻译任务
        val currentVersion = versionCounter.incrementAndGet()
        activeAiTranslationJob?.cancel()
        activeAiTranslationJob = null

        if (song != null) {
            val processor = SongPreprocessor(TitleSlot.NAME_ARTIST)
            val lines = processor.prepare(song)
            timingNavigator = TimingNavigator(lines.toTypedArray())
            interludeTracker = InterludeTracker(8_000L)

            // Start AI translation if enabled
            if (prefs != null) {
                val aiEnabled = prefs.getBoolean(RootConstants.KEY_HOOK_AI_TRANS_ENABLE, RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE)
                if (aiEnabled) {
                    startAiTranslation(song, prefs, currentVersion)
                } else if (aiSetDisplayTranslation) {
                    // 仅当上一首歌的翻译行是 AI 加的，才复位
                    isDisplayTranslation = false
                    aiSetDisplayTranslation = false
                }
            }
        } else {
            timingNavigator = TimingNavigator(emptyArray())
        }
    }

    private fun startAiTranslation(song: Song, prefs: SharedPreferences, version: Int) {
        val configs = buildAiTranslationConfigs(prefs)
        val autoIgnoreChinese = prefs.getBoolean(
            RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE,
            RootConstants.DEFAULT_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE
        )
        activeAiTranslationJob = aiTransScope.launch {
            try {
                val ratio = song.calculateChineseRatio()
                val percentage = String.format(java.util.Locale.US, "%.1f%%", ratio * 100)
                if (autoIgnoreChinese) {
                    HookLogger.d("LyriconDataBridge", "AITranslation : 歌曲 ${song.name}（中文占比 $percentage)")
                }
                if (autoIgnoreChinese && ratio > 0.5f) {
                    HookLogger.d("LyriconDataBridge", "AITranslation : 歌曲 ${song.name}（中文占比 $percentage），已自动跳过AI翻译")
                    return@launch
                }
                val translatedSong = AITranslator.translateSongSync(song, configs)
                
                // 核心校验：如果当前运行的翻译版本号不是最新的全局版本号，说明已被切歌，直接废弃结果
                if (version != versionCounter.get()) {
                    return@launch
                }

                if (translatedSong !== song && translatedSong.lyrics != null) {
                    currentSong = translatedSong
                    val processor = SongPreprocessor(TitleSlot.NAME_ARTIST)
                    val lines = processor.prepare(translatedSong)
                    timingNavigator = TimingNavigator(lines.toTypedArray())

                    isDisplayTranslation = true
                    aiSetDisplayTranslation = true
                    HookIslandLyric.refreshActiveIsland()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private fun buildAiTranslationConfigs(prefs: SharedPreferences): AiTranslationConfigs {
        val providerName = prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_PROVIDER, AiTranslationProvider.OPENAI.name)
            ?: AiTranslationProvider.OPENAI.name
        val provider = try { AiTranslationProvider.valueOf(providerName) } catch (_: Exception) { AiTranslationProvider.OPENAI }

        return AiTranslationConfigs(
            provider = providerName,
            targetLanguage = prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_TARGET_LANG, RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_TARGET_LANG,
            apiKey = prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_API_KEY, "") ?: "",
            model = prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_MODEL, RootConstants.DEFAULT_HOOK_AI_TRANS_MODEL).orEmpty().ifBlank { provider.model },
            baseUrl = prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_BASE_URL, RootConstants.DEFAULT_HOOK_AI_TRANS_BASE_URL).orEmpty().ifBlank { provider.url },
            prompt = prefs.getString(RootConstants.KEY_HOOK_AI_TRANS_PROMPT, RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT) ?: RootConstants.DEFAULT_HOOK_AI_TRANS_PROMPT,
            temperature = prefs.getFloat(RootConstants.KEY_HOOK_AI_TRANS_TEMPERATURE, AiTranslationConfigs.DEFAULT_TEMPERATURE),
            topP = prefs.getFloat(RootConstants.KEY_HOOK_AI_TRANS_TOP_P, AiTranslationConfigs.DEFAULT_TOP_P),
            maxTokens = prefs.getInt(RootConstants.KEY_HOOK_AI_TRANS_MAX_TOKENS, AiTranslationConfigs.DEFAULT_MAX_TOKENS)
        )
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
            HookLogger.d("LyriconDataBridge", "歌词行切换: ${newText.take(30)}")
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
    }

    fun clearAll() {
        currentSong = null
        currentSongName = null
        currentLyric = null
        currentLyricLine = null
        isTextMode = false
        isDisplayTranslation = true
        isDisplayRoma = true
        aiSetDisplayTranslation = false
        timingNavigator = TimingNavigator(emptyArray())

        // 清空状态时，递增版本号并取消仍在后台运行的协程
        versionCounter.incrementAndGet()
        activeAiTranslationJob?.cancel()
        activeAiTranslationJob = null
    }

    private fun Song.calculateChineseRatio(): Float {
        val totalChars = lyrics?.flatMap { it.text.orEmpty().toList() }
            ?.filterNot { it.isWhitespace() || it.isPunctuation() } ?: return 1.0f
        if (totalChars.isEmpty()) return 1.0f
        
        val chineseHanCount = totalChars.count { it.isChineseHan() }
        return chineseHanCount.toFloat() / totalChars.size
    }

    private fun Char.isChineseHan(): Boolean {
        return try {
            Character.UnicodeScript.of(this.code) == Character.UnicodeScript.HAN
        } catch (_: Exception) {
            val ub = Character.UnicodeBlock.of(this)
            ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                    ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                    ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
        }
    }

    private fun Char.isPunctuation(): Boolean {
        return !isLetterOrDigit() && !isWhitespace()
    }
}
