package com.lidesheng.hyperlyric.root.aitrans

import android.content.Context
import android.util.Log
import com.lidesheng.hyperlyric.root.utils.HookLogger
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.style.AiTranslationConfigs
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
            HookLogger.w("AITranslator", "跳过翻译：配置不完整，API Key 或其他配置为空")
            return song
        }
        if (song.lyrics.isNullOrEmpty()) return song

        HookLogger.d("AITranslator", "正在翻译：${song.name}（共 ${song.lyrics?.size ?: 0} 行）")
        return try {
            translateSong(song, configs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HookLogger.e("AITranslator", "翻译过程发生错误", e)
            song
        }
    }

    suspend fun doOpenAiRequest(
        configs: AiTranslationConfigs,
        song: Song? = null,
        texts: List<String>
    ): List<TranslationItem>? = OpenAiTranslationClient.request(configs, song, texts)

    fun clearCache(callback: () -> Unit) {
        HookLogger.d("AITranslator", "清理记录：正在清空所有本地翻译记录（内存+数据库）")
        scheduler.cancelPending()
        cache.clear(callback)
    }

    private suspend fun translateSong(song: Song, configs: AiTranslationConfigs): Song {
        val currentLyrics = song.lyrics ?: return song
        val originalLines = currentLyrics.map { it.text?.trim() ?: "" }
        val songContentId = AITranslationKey.calculate(configs, song, originalLines)

        cache.getFromMemory(songContentId)?.let {
            HookLogger.d("AITranslator", "缓存命中：从内存加载了 ${song.name} 的翻译")
            return AITranslationApplicator.apply(song, it)
        }

        cache.getFromDb(songContentId)?.let {
            HookLogger.d("AITranslator", "记录命中：从本地存储加载了 ${song.name} 的翻译")
            cache.putMemory(songContentId, it)
            return AITranslationApplicator.apply(song, it)
        }

        HookLogger.d("AITranslator", "正在请求 AI：本地无记录，准备发起在线翻译")
        val apiResults = scheduler.getOrEnqueue(songContentId, configs, song, originalLines).await()
        if (apiResults.isNullOrEmpty()) {
            HookLogger.w("AITranslator", "翻译失败：未能获取到 ${song.name} 的 AI 翻译")
            return song
        }
        return AITranslationApplicator.apply(song, apiResults)
    }
}


