package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementCacheStore
import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger

internal class AiTranslationEngine(
    cacheStore: LyricEnhancementCacheStore,
    logger: LyricEnhancementLogger,
    private val translatorLogger: LyricEnhancementLogger,
    networkRequester: ((AiTranslationConfig, Song, List<String>) -> List<TranslationItem>?)? = null,
    translationCache: TranslationCache? = null,
) {
    private val cache = translationCache ?: TranslationCache(
        cacheStore,
        logger.withTag("Cache")
    )
    private val client = OpenAiTranslationClient(
        logger = logger.withTag("OpenAiClient"),
        parserLogger = logger.withTag("ResponseParser")
    )
    private val networkRequest:
        (AiTranslationConfig, Song, List<String>) -> List<TranslationItem>? =
        networkRequester ?: client::request
    private val scheduler = TranslationScheduler(logger.withTag("Scheduler"))

    fun translate(
        song: Song,
        config: AiTranslationConfig,
        sourcePackageName: String? = null
    ): Song? {
        TranslationEligibility.skipReason(song)?.let { reason ->
            translatorLogger.debug("跳过 AI 翻译: reason=${reason}, song=${song.name}")
            return null
        }
        val lyrics = song.lyrics ?: return null
        val originalLines = lyrics.map { it.text?.trim().orEmpty() }
        val key = TranslationKey.calculate(song, originalLines, config, sourcePackageName)
        val cacheGeneration = cache.currentGeneration()

        cache.get(key, title = song.name, artist = song.artist)?.let { cached ->
            if (cached.fromMemory) {
                translatorLogger.debug("缓存命中：从内存加载了 ${song.name} 的翻译")
            } else {
                translatorLogger.debug("记录命中：从本地存储加载了 ${song.name} 的翻译")
            }
            val validItems = validItems(cached.items, lyrics.size)
            if (validItems.isEmpty()) {
                cache.remove(key)
                translatorLogger.warn("缓存内容无效，删除后回退网络: song=${song.name}")
                return@let null
            }
            return TranslationApplicator.apply(
                song,
                validItems,
                config.forceOverride,
                translatorLogger.withTag("Applicator")
            )
        }

        translatorLogger.debug("正在请求 AI：本地无记录，准备发起在线翻译")
        val scheduled = scheduler.getOrEnqueue(
            key = key,
            songName = song.name.orEmpty()
        ) {
            networkRequest(config, song, originalLines)
        }
        return try {
            val results = scheduled.items
            if (results.isNullOrEmpty()) {
                translatorLogger.warn("翻译失败：未能获取到 ${song.name} 的 AI 翻译")
                null
            } else {
                val validItems = validItems(results, lyrics.size)
                if (validItems.isEmpty()) {
                    translatorLogger.warn("翻译结果无有效行，跳过缓存: song=${song.name}")
                    null
                } else {
                    cache.put(
                        key = key,
                        items = validItems,
                        expectedGeneration = cacheGeneration,
                        title = song.name,
                        artist = song.artist
                    )
                    TranslationApplicator.apply(
                        song,
                        validItems,
                        config.forceOverride,
                        translatorLogger.withTag("Applicator")
                    )
                }
            }
        } finally {
            scheduled.release()
        }
    }

    private fun validItems(items: List<TranslationItem>, lineCount: Int): List<TranslationItem> =
        items.asSequence()
            .filter { it.index in 0 until lineCount && it.trans.isNotBlank() }
            .map { it.copy(trans = it.trans.trim()) }
            .distinctBy { it.index }
            .toList()

    fun clearCache(): Boolean {
        return cache.clear()
    }

    fun listCacheEntries(): List<LyricEnhancementCacheEntry> = cache.listEntries()

    fun clearCacheEntry(entryId: String): Boolean = cache.clearEntry(entryId)

    fun close() {
        scheduler.close()
    }
}
