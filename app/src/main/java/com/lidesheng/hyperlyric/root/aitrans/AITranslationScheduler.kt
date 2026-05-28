package com.lidesheng.hyperlyric.root.aitrans

import android.util.Log
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.AiTranslationConfigs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Bounded background scheduler for AI requests during aggressive song switching. */
internal class AITranslationScheduler(
    private val cache: AITranslationCache,
    private val generation: AtomicInteger,
    private val maxRunning: Int,
    private val maxPending: Int
) {
    private companion object {
        const val TAG = "HyperLyricAITranslator"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val jobs = ConcurrentHashMap<String, TranslationJob>()
    private val pending = ArrayDeque<TranslationJob>()
    private var running = 0

    fun getOrEnqueue(
        key: String,
        configs: AiTranslationConfigs,
        song: Song,
        originalLines: List<String>
    ): Deferred<List<TranslationItem>?> {
        synchronized(lock) {
            jobs[key]?.let {
                Log.d(TAG, "Reusing scheduled AI translation for: ${song.name} [$key]")
                return it.deferred
            }

            val job = TranslationJob(
                key = key,
                songName = song.name.orEmpty(),
                configs = configs,
                song = song,
                originalLines = originalLines,
                generation = generation.get()
            )
            jobs[key] = job
            pending.addLast(job)
            HookLogger.d("AITranslationScheduler", "AITranslation : 已添加 ${job.songName} 到翻译队列（等待中=${pending.size}，运行中=$running）")
            trimPendingLocked()
            dispatchNextLocked()
            return job.deferred
        }
    }

    fun cancelPending() {
        synchronized(lock) {
            while (pending.isNotEmpty()) {
                val job = pending.removeFirst()
                job.state = TranslationJobState.CANCELLED
                jobs.remove(job.key, job)
                job.deferred.complete(null)
                Log.d(TAG, "Cancelled pending AI translation: ${job.songName}")
            }
        }
    }

    private fun trimPendingLocked() {
        while (pending.size > maxPending) {
            val dropped = pending.removeFirst()
            if (dropped.state != TranslationJobState.PENDING) continue

            dropped.state = TranslationJobState.CANCELLED
            jobs.remove(dropped.key, dropped)
            dropped.deferred.complete(null)
            HookLogger.w("AITranslationScheduler", "AITranslation : 由于队列已满，取消了 ${dropped.songName} 的翻译请求")
        }
    }

    private fun dispatchNextLocked() {
        while (running < maxRunning && pending.isNotEmpty()) {
            val job = pending.removeLast()
            if (job.state != TranslationJobState.PENDING) continue

            job.state = TranslationJobState.RUNNING
            running++
            HookLogger.d("AITranslationScheduler", "AITranslation : 开始翻译 ${job.songName}（等待中=${pending.size}，运行中=$running）")
            scope.launch { runJob(job) }
        }
    }

    private suspend fun runJob(job: TranslationJob) {
        try {
            val apiResults =
                OpenAiTranslationClient.request(job.configs, job.song, job.originalLines)
            if (!apiResults.isNullOrEmpty() && job.generation == generation.get()) {
                HookLogger.d("AITranslationScheduler", "AITranslation :  ${job.songName}翻译成功（翻译已缓存）")
                cache.putMemory(job.key, apiResults)
                cache.saveToDb(job.key, apiResults)
            }
            job.state = TranslationJobState.COMPLETED
            job.deferred.complete(apiResults)
        } catch (e: CancellationException) {
            job.state = TranslationJobState.CANCELLED
            job.deferred.cancel(e)
            throw e
        } catch (e: Exception) {
            job.state = TranslationJobState.COMPLETED
            HookLogger.e("AITranslationScheduler", "AITranslation : 翻译[${job.songName}] 出错", e)
            job.deferred.complete(null)
        } finally {
            synchronized(lock) {
                running = (running - 1).coerceAtLeast(0)
                jobs.remove(job.key, job)
                dispatchNextLocked()
            }
        }
    }

    private data class TranslationJob(
        val key: String,
        val songName: String,
        val configs: AiTranslationConfigs,
        val song: Song,
        val originalLines: List<String>,
        val generation: Int,
        val deferred: CompletableDeferred<List<TranslationItem>?> = CompletableDeferred(),
        var state: TranslationJobState = TranslationJobState.PENDING
    )

    private enum class TranslationJobState {
        PENDING,
        RUNNING,
        COMPLETED,
        CANCELLED
    }
}
