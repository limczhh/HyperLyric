package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementInput
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementMediaInfo
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.utils.HookLogger

internal class AiTranslationFeature(
    private val preferences: SharedPreferences,
    cacheStore: LyricEnhancementCacheStore,
    private val onConfigChanged: () -> Unit,
) : AutoCloseable {

    private companion object {
        const val LOG_TAG = "LyricEnhancement/AiTranslation"
        const val GATEWAY_LOG_TAG = "$LOG_TAG/Gateway"
        const val TRANSLATOR_LOG_TAG = "$LOG_TAG/Translator"
    }

    private val cache = TranslationCache(cacheStore)
    private val engine = AiTranslationEngine(
        cacheStore = cacheStore,
        translationCache = cache
    )

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in AiTranslationConfig.PREFERENCE_KEYS) {
                onConfigChanged()
            }
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun isEnabled(): Boolean = preferences.getBoolean(
        RootConstants.KEY_HOOK_AI_TRANS_ENABLE,
        RootConstants.DEFAULT_HOOK_AI_TRANS_ENABLE
    )

    fun clearCache(): Boolean {
        val cleared = engine.clearCache()
        HookLogger.i(GATEWAY_LOG_TAG, if (cleared) "AI 翻译缓存已清除" else "AI 翻译缓存清除不完整")
        return cleared
    }

    fun listCacheEntries(): List<LyricEnhancementCacheEntry> = engine.listCacheEntries()

    fun clearCacheEntry(entryId: String): Boolean = engine.clearCacheEntry(entryId)

    fun enhance(
        song: Song,
        input: LyricEnhancementInput,
        enhancedSong: Song? = null,
    ): Song? {
        return try {
            val config = AiTranslationConfig.from(preferences)
            if (!config.enabled) return null
            val querySong = song.withMediaInfo(input.mediaInfo)
            TranslationEligibility.skipReason(querySong)?.let { reason ->
                HookLogger.d(GATEWAY_LOG_TAG, "跳过 AI 翻译: reason=${reason}, song=${querySong.name}")
                return null
            }
            val lyrics = querySong.lyrics ?: return null

            if (
                config.skipExisting &&
                !config.forceOverride &&
                (
                    lyrics.any { TranslationApplicator.hasTranslation(it) } ||
                            enhancedSong?.lyrics.orEmpty()
                                .any { TranslationApplicator.hasTranslation(it) }
                    )
            ) {
                HookLogger.d(
                    GATEWAY_LOG_TAG,
                    "跳过 AI 翻译: reason=existing_translation, song=${querySong.name}"
                )
                return null
            }

            if (config.skipLanguages.isNotEmpty()) {
                val detected = TranslationLanguageDetector.detect(querySong)
                if (detected != null) {
                    val margin = detected.secondConfidence?.let {
                        detected.confidence - it
                    }
                    val confidentEnough = detected.confidence >= 0.8f &&
                            (margin == null || margin >= 0.15f)
                    val selected = detected.language in config.skipLanguages
                    val confidence = "%.3f".format(java.util.Locale.US, detected.confidence)
                    val marginText = margin?.let {
                        "%.3f".format(java.util.Locale.US, it)
                    } ?: "-"
                    HookLogger.d(
                        GATEWAY_LOG_TAG,
                        "歌词语言识别: song=${querySong.name}, detected=${detected.languageTag}, " +
                                "confidence=$confidence, margin=$marginText, " +
                                "hypotheses=${detected.hypothesisCount}, selected=$selected, " +
                                "confident=$confidentEnough"
                    )
                    if (selected && confidentEnough) {
                        HookLogger.d(
                            GATEWAY_LOG_TAG,
                            "跳过 AI 翻译: reason=selected_language, song=${querySong.name}, " +
                                    "detected=${detected.languageTag}"
                        )
                        return null
                    }
                }
            }

            if (!config.isUsable) {
                HookLogger.w(TRANSLATOR_LOG_TAG, "跳过翻译：配置不完整，API Key 或其他配置为空")
                return null
            }
            HookLogger.d(TRANSLATOR_LOG_TAG, "正在翻译：${querySong.name}（共 ${lyrics.size} 行）")
            engine.translate(
                song = querySong,
                config = config,
                sourcePackageName = input.mediaInfo?.sourcePackageName
            )?.let { translated ->
                if (enhancedSong == null) {
                    song.copy(lyrics = translated.lyrics)
                } else {
                    TranslationApplicator.merge(
                        sourceSong = translated,
                        targetSong = enhancedSong,
                        forceOverride = config.forceOverride,
                    )
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (error: Exception) {
            HookLogger.e(TRANSLATOR_LOG_TAG, "翻译过程发生错误", error)
            null
        }
    }

    override fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        engine.close()
    }

    private fun Song.withMediaInfo(mediaInfo: LyricEnhancementMediaInfo?): Song {
        mediaInfo ?: return this
        return copy(
            name = mediaInfo.title ?: name,
            artist = mediaInfo.artist ?: artist,
            album = mediaInfo.album ?: album,
            duration = mediaInfo.duration ?: duration
        )
    }

}
