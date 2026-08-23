package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.LyricProcessorExtension
import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import com.lidesheng.hyperlyric.plugin.api.PluginCacheExtension
import com.lidesheng.hyperlyric.plugin.api.PluginContext
import com.lidesheng.hyperlyric.plugin.api.PluginLyricField
import com.lidesheng.hyperlyric.plugin.api.PluginMediaInfo
import com.lidesheng.hyperlyric.plugin.api.PluginProcessingContext
import com.lidesheng.hyperlyric.plugin.api.PluginProcessorStage
import com.lidesheng.hyperlyric.plugin.api.PluginLyricsUpdateMode
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import com.lidesheng.hyperlyric.plugin.api.PluginSongField
import com.lidesheng.hyperlyric.plugin.api.PluginSongResult

internal class AiTranslationProcessor(
    private val context: PluginContext,
) : LyricProcessorExtension {
    override val id: String = AI_TRANSLATION_EXTENSION_ID
    override val stage = PluginProcessorStage.TRANSLATION_ENHANCEMENT

    private val gatewayLogger = context.logger.withTag("AiTranslationGateway")
    private val translatorLogger = context.logger.withTag("AITranslator")
    private val cache = TranslationCache(context.cache, context.logger.withTag("AITranslationCache"))
    private val engine = AiTranslationEngine(
        cacheStore = context.cache,
        logger = context.logger,
        translatorLogger = translatorLogger,
        translationCache = cache
    )
    private val cacheExtension = AiTranslationCacheExtension(cache, engine::cancelPending)

    override fun processResult(
        song: PluginSong,
        processingContext: PluginProcessingContext
    ): PluginSongResult? {
        return try {
            val config = AiTranslationConfig.from(context.config)
            if (!config.enabled) return null
            // Media fields are a read-only Core snapshot used for lookup/cache/prompt only.
            // PluginSongMapper never writes these local query values back into Core Song.
            val querySong = song.withMediaInfo(processingContext.mediaInfo)
            val lyrics = querySong.lyrics
            if (lyrics.isNullOrEmpty()) {
                gatewayLogger.debug("跳过 AI 翻译: reason=no_lyrics, song=${querySong.name}")
                return null
            }

            if (
                config.skipExisting &&
                !config.forceOverride &&
                lyrics.any { !it.translation.isNullOrBlank() }
            ) {
                gatewayLogger.debug(
                    "跳过 AI 翻译: reason=existing_translation, song=${querySong.name}"
                )
                return null
            }

            if (config.skipLanguages.isNotEmpty()) {
                if (!TranslationLanguageDetector.hasEnoughText(querySong)) {
                    gatewayLogger.debug(
                        "跳过语言识别: reason=text_too_short, song=${querySong.name}"
                    )
                } else {
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
                        gatewayLogger.debug(
                            "歌词语言识别: song=${querySong.name}, detected=${detected.languageTag}, " +
                                    "confidence=$confidence, margin=$marginText, " +
                                    "hypotheses=${detected.hypothesisCount}, selected=$selected, " +
                                    "confident=$confidentEnough"
                        )
                        if (selected && confidentEnough) {
                            gatewayLogger.debug(
                                "跳过 AI 翻译: reason=selected_language, song=${querySong.name}, " +
                                        "detected=${detected.languageTag}"
                            )
                            return null
                        }
                    }
                }
            }

            if (!config.isUsable) {
                translatorLogger.warn("跳过翻译：配置不完整，API Key 或其他配置为空")
                return null
            }
            translatorLogger.debug("正在翻译：${querySong.name}（共 ${lyrics.size} 行）")
            engine.translate(
                song = querySong,
                config = config,
                sourcePackageName = processingContext.mediaInfo?.sourcePackageName
            )?.let { translated ->
                PluginSongResult(
                    song = translated,
                    changedFields = setOf(PluginSongField.LYRICS),
                    lyricsUpdateMode = PluginLyricsUpdateMode.PATCH,
                    changedLyricFields = setOf(
                        PluginLyricField.TRANSLATION,
                        PluginLyricField.TRANSLATION_WORDS
                    )
                )
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (error: Exception) {
            translatorLogger.error("翻译过程发生错误", error)
            null
        }
    }

    private fun PluginSong.withMediaInfo(mediaInfo: PluginMediaInfo?): PluginSong {
        mediaInfo ?: return this
        return copy(
            name = mediaInfo.title ?: name,
            artist = mediaInfo.artist ?: artist,
            album = mediaInfo.album ?: album,
            duration = mediaInfo.duration ?: duration
        )
    }

    fun close() {
        engine.close()
    }

    fun onConfigChanged(_config: PluginConfig) {
        // Configuration is read at the next processing request. Do not cancel an in-flight
        // translation when the plugin switch or another setting changes.
    }

    fun cacheExtension(): PluginCacheExtension = cacheExtension

    private companion object {
        const val AI_TRANSLATION_EXTENSION_ID = "ai.translation"
    }
}
