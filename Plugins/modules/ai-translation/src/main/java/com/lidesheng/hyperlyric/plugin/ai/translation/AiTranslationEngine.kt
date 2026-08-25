package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginCache
import com.lidesheng.hyperlyric.plugin.api.PluginSong

internal class AiTranslationEngine(
    cacheStore: PluginCache,
    logger: PluginLogger,
    private val translatorLogger: PluginLogger,
    networkRequester: ((AiTranslationConfig, PluginSong, List<String>) -> List<TranslationItem>?)? = null,
    translationCache: TranslationCache? = null,
) {
    private val cache = translationCache ?: TranslationCache(
        cacheStore,
        logger.withTag("AITranslationCache")
    )
    private val client = OpenAiTranslationClient(
        logger = logger.withTag("OpenAiTranslationClient"),
        parserLogger = logger.withTag("AITranslationResponseParser")
    )
    private val networkRequest:
        (AiTranslationConfig, PluginSong, List<String>) -> List<TranslationItem>? =
        networkRequester ?: client::request
    private val scheduler = TranslationScheduler(logger.withTag("AITranslationScheduler"))

    fun translate(
        song: PluginSong,
        config: AiTranslationConfig,
        sourcePackageName: String? = null
    ): PluginSong? {
        TranslationEligibility.skipReason(song)?.let { reason ->
            translatorLogger.debug("跳过 AI 翻译: reason=$reason, song=${song.name}")
            return null
        }
        val lyrics = song.lyrics ?: return null
        val originalLines = lyrics.map { it.text?.trim().orEmpty() }
        val key = TranslationKey.calculate(song, originalLines, config, sourcePackageName)
        val cacheGeneration = cache.currentGeneration()

        cache.get(key)?.let { cached ->
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
                translatorLogger.withTag("AITranslationApplicator")
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
                    // Cache the verified network response even when the current Song already
                    // contains some/all translations and the applicator has nothing to write.
                    cache.put(key, validItems, song, cacheGeneration)
                    TranslationApplicator.apply(
                        song,
                        validItems,
                        config.forceOverride,
                        translatorLogger.withTag("AITranslationApplicator")
                    )
                }
            }
        } finally {
            // The scheduler keeps the completed result registered until this point, so another
            // same-key caller either shares it or observes the persistent cache after this call.
            scheduled.release()
        }
    }

    private fun validItems(items: List<TranslationItem>, lineCount: Int): List<TranslationItem> =
        items.asSequence()
            .filter { it.index in 0 until lineCount && it.trans.isNotBlank() }
            .map { it.copy(trans = it.trans.trim()) }
            .distinctBy { it.index }
            .toList()

    fun close() {
        scheduler.close()
    }

    fun cancelPending() {
        scheduler.cancelAll()
    }
}
