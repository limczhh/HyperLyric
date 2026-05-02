package com.lidesheng.hyperlyric.root.aitrans

import android.content.Context
import android.util.Log
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.AiTranslationConfigs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicInteger

/**
 * SystemUI 进程内的 AI 歌词翻译门面。
 *
 * 职责边界：
 * - [AITranslationKey] 负责生成整首歌级缓存 key。
 * - [AITranslationCache] 负责内存 + SQLite 双级缓存。
 * - [AITranslationScheduler] 负责暴力切歌场景下的 pending/running 调度、同 key 复用和队列淘汰。
 * - [OpenAiTranslationClient] 负责 OpenAI-compatible 网络请求。
 * - [AITranslationResponseParser] 负责 LLM 响应清洗、兼容解析和 index 校验。
 * - [AITranslationApplicator] 负责把有效译文写回 Song。
 */
object AITranslator {
    private const val TAG = "HyperLyricAITranslator"
    private const val MAX_CACHE_SIZE = 1000
    private const val MAX_RUNNING_TRANSLATIONS = 3
    private const val MAX_PENDING_TRANSLATIONS = 5

    private val cacheGeneration = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cache = AITranslationCache(
        maxCacheSize = MAX_CACHE_SIZE,
        generation = cacheGeneration,
        scope = scope
    )
    private val scheduler = AITranslationScheduler(
        cache = cache,
        generation = cacheGeneration,
        maxRunning = MAX_RUNNING_TRANSLATIONS,
        maxPending = MAX_PENDING_TRANSLATIONS
    )

    fun init(context: Context) {
        cache.init(context)
    }

    suspend fun translateSongSync(
        song: Song,
        configs: AiTranslationConfigs,
    ): Song {
        if (!configs.isUsable) {
            Log.w(TAG, "Translation skipped: Configs not usable (missing API Key or disabled).")
            return song
        }
        if (song.lyrics.isNullOrEmpty()) return song

        return try {
            translateSong(song, configs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during translateSongSync: ${e.message}", e)
            song
        }
    }

    suspend fun doOpenAiRequest(
        configs: AiTranslationConfigs,
        song: Song? = null,
        texts: List<String>
    ): List<TranslationItem>? = OpenAiTranslationClient.request(configs, song, texts)

    fun clearCache(callback: () -> Unit) {
        Log.i(TAG, "Clearing all translation caches (Memory & DB)...")
        scheduler.cancelPending()
        cache.clear(callback)
    }

    private suspend fun translateSong(song: Song, configs: AiTranslationConfigs): Song {
        val currentLyrics = song.lyrics ?: return song
        val originalLines = currentLyrics.map { it.text?.trim() ?: "" }
        val songContentId = AITranslationKey.calculate(configs, song, originalLines)

        cache.getFromMemory(songContentId)?.let {
            Log.d(TAG, "Memory cache hit for: ${song.name} [$songContentId]")
            return AITranslationApplicator.apply(song, it)
        }

        cache.getFromDb(songContentId)?.let {
            Log.d(TAG, "Database cache hit for: ${song.name} [$songContentId]")
            cache.putMemory(songContentId, it)
            return AITranslationApplicator.apply(song, it)
        }

        Log.i(
            TAG,
            "Cache miss. Waiting for AI translation: ${song.name} (${originalLines.size} lines)"
        )
        val apiResults = scheduler.getOrEnqueue(songContentId, configs, song, originalLines).await()
        if (apiResults.isNullOrEmpty()) {
            Log.w(TAG, "Failed to get translation from API.")
            return song
        }
        return AITranslationApplicator.apply(song, apiResults)
    }
}
