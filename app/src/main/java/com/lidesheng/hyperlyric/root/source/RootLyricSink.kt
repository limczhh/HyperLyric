package com.lidesheng.hyperlyric.root.source

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.IslandSlotContentAssembler
import com.lidesheng.hyperlyric.root.island.renderer.IslandRenderer
import com.lidesheng.hyperlyric.root.aitrans.AITranslator
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.style.AiTranslationConfigs
import com.lidesheng.hyperlyric.lyric.style.AiTranslationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RootLyricSink(
    private val renderer: IslandRenderer,
    private val prefs: SharedPreferences? = null
) : LyricSink {

    private val aiTransScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeAiTranslationJob: Job? = null
    private var aiSetDisplayTranslation: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPositionDispatchTimeMs = 0L
    private var pendingPosition: Long? = null
    private var positionDispatchScheduled = false

    private companion object {
        const val MIN_POSITION_DISPATCH_INTERVAL_MS = 33L
    }

    override fun onSongChanged(song: Any?) {

        activeAiTranslationJob?.cancel()
        activeAiTranslationJob = null

        if (song is Song && prefs != null) {
            val aiEnabled = prefs.getBoolean(
                RootConstants.KEY_HOOK_AI_TRANS_ENABLE,
                RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE
            )
            if (aiEnabled) {
                startAiTranslation(song, prefs)
            } else if (aiSetDisplayTranslation) {
                LyriconDataBridge.isDisplayTranslation = false
                aiSetDisplayTranslation = false
            }
        }
    }

    override fun onLyricLine(line: Any?) {
        if (line is IRichLyricLine) {
    
            LyriconDataBridge.updateLyricLine(line)
            renderer.updateLyricLine()
        }
    }

    override fun onPlainText(text: String?) {

        LyriconDataBridge.updateLyric(text)
        renderer.updateLyricLine()
    }

    override fun onStop() {
        activeAiTranslationJob?.cancel()
        activeAiTranslationJob = null
        aiSetDisplayTranslation = false
        pendingPosition = null
        positionDispatchScheduled = false
        renderer.clearAllViews()
        LyriconDataBridge.clearState()
    }

    override fun onMetadata(title: String?, artist: String?, album: String?, publisher: String?) {
        if (title != null) LyriconDataBridge.currentSongName = title
        if (!publisher.isNullOrEmpty()) {
            LyriconDataBridge.updateLyricPackage(publisher)
        }
        IslandSlotContentAssembler.invalidate()
        renderer.refreshActiveIsland()
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        renderer.onPlaybackStateChanged(isPlaying)
    }

    override fun onPositionChanged(position: Long) {
        val lyricChanged = LyriconDataBridge.updatePosition(position)
        if (lyricChanged) {
            renderer.updateLyricLine()
        }
        dispatchPositionThrottled(position)
    }

    private fun dispatchPositionThrottled(position: Long) {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastPositionDispatchTimeMs
        if (elapsed >= MIN_POSITION_DISPATCH_INTERVAL_MS) {
            lastPositionDispatchTimeMs = now
            pendingPosition = null
            renderer.updatePosition(position)
            return
        }

        pendingPosition = position
        if (positionDispatchScheduled) return

        positionDispatchScheduled = true
        mainHandler.postDelayed({
            positionDispatchScheduled = false
            val latest = pendingPosition ?: return@postDelayed
            pendingPosition = null
            lastPositionDispatchTimeMs = SystemClock.uptimeMillis()
            renderer.updatePosition(latest)
        }, MIN_POSITION_DISPATCH_INTERVAL_MS - elapsed)
    }

    private fun startAiTranslation(song: Song, prefs: SharedPreferences) {
        val configs = buildAiTranslationConfigs(prefs)
        val autoIgnoreChinese = prefs.getBoolean(
            RootConstants.KEY_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE,
            RootConstants.DEFAULT_HOOK_AI_TRANS_AUTO_IGNORE_CHINESE
        )
        val version = LyriconDataBridge.versionCounter.get()

        activeAiTranslationJob = aiTransScope.launch {
            try {
                val ratio = song.calculateChineseRatio()
                val percentage = String.format(java.util.Locale.US, "%.1f%%", ratio * 100)
                if (autoIgnoreChinese) {
                    HookLogger.d("RootLyricSink", "歌曲 ${song.name}（中文占比 $percentage)")
                }
                if (autoIgnoreChinese && ratio > 0.5f) {
                    HookLogger.d("RootLyricSink", "歌曲 ${song.name}（中文占比 $percentage），已自动跳过AI翻译")
                    return@launch
                }
                val skipExisting = prefs.getBoolean(
                    RootConstants.KEY_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION,
                    RootConstants.DEFAULT_HOOK_AI_TRANS_SKIP_EXISTING_TRANSLATION
                )
                if (skipExisting) {
                    val hasTranslation = song.lyrics?.any { !it.translation.isNullOrBlank() } == true
                    if (hasTranslation) {
                        HookLogger.d("RootLyricSink", "歌曲 ${song.name} 已有翻译，跳过AI翻译")
                        return@launch
                    }
                }
                if (song.lyrics.isNullOrEmpty()) {
                    HookLogger.d("RootLyricSink", "歌曲 ${song.name} 无歌词，跳过AI翻译")
                    return@launch
                }
                val translatedSong = AITranslator.translateSongSync(song, configs)

                if (version != LyriconDataBridge.versionCounter.get()) {
                    return@launch
                }

                if (translatedSong !== song && translatedSong.lyrics != null) {
                    LyriconDataBridge.applyTranslation(translatedSong)
                    aiSetDisplayTranslation = true
                    LyriconDataBridge.onAiTranslationComplete?.invoke()
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


