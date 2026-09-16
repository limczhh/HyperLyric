package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.root.lyricenhancement.LyricEnhancementLogger
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded request scheduler mirroring the legacy 3-running/5-pending policy. */
internal class TranslationScheduler(
    private val logger: LyricEnhancementLogger,
) {
    private companion object {
        const val MAX_RUNNING = 3
        const val MAX_PENDING = 5
        // Leave a small margin below the outer enhancement processor deadline.
        const val MAX_PROCESS_WAIT_MS = 35_000L
    }

    private val executor: ExecutorService = Executors.newFixedThreadPool(MAX_RUNNING) { runnable ->
        Thread(runnable, "HyperLyric-AiTranslation").apply { isDaemon = true }
    }
    private val lock = Any()
    private val jobs = HashMap<String, TranslationJob>()
    private val pending = ArrayDeque<TranslationJob>()
    private var running = 0

    /**
     * A completed request stays in [jobs] until the caller has finished consuming it. This
     * closes the small window between completing the future and writing the result to the
     * persistent enhancement cache.
     */
    internal class ScheduledTranslation(
        val items: List<TranslationItem>?,
        private val releaseCallback: () -> Unit,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) releaseCallback()
        }
    }

    fun getOrEnqueue(
        key: String,
        songName: String,
        request: () -> List<TranslationItem>?,
    ): ScheduledTranslation {
        val job = synchronized(lock) {
            jobs[key]?.also {
                logger.debug("复用翻译任务: song=$songName, key=$key")
            } ?: TranslationJob(
                key = key,
                songName = songName,
                future = CompletableFuture(),
                request = request
            ).also { created ->
                jobs[key] = created
                pending.addLast(created)
                logger.debug(
                    "加入翻译队列: song=${created.songName}, pending=${pending.size}, running=$running"
                )
                trimPendingLocked()
                dispatchNextLocked()
            }
        }
        return ScheduledTranslation(
            items = await(job),
            releaseCallback = { release(job) }
        )
    }

    fun cancelAll() {
        val tasks = synchronized(lock) {
            val current = jobs.values.toList()
            jobs.clear()
            pending.clear()
            current.forEach { it.running = false }
            running = 0
            current.mapNotNull { job ->
                job.state = State.CANCELLED
                job.future.complete(null)
                job.task
            }
        }
        tasks.forEach { it.cancel(true) }
    }

    fun close() {
        cancelAll()
        executor.shutdownNow()
    }

    private fun await(job: TranslationJob): List<TranslationItem>? {
        return try {
            job.future.get(MAX_PROCESS_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            logger.warn(
                "翻译任务超时: song=${job.songName}, timeoutMs=$MAX_PROCESS_WAIT_MS"
            )
            cancel(job)
            null
        } catch (error: InterruptedException) {
            cancel(job)
            throw error
        } catch (error: ExecutionException) {
            logger.error("翻译任务失败: song=${job.songName}", error.cause)
            null
        }
    }

    private fun trimPendingLocked() {
        while (pending.size > MAX_PENDING) {
            val dropped = pending.removeFirst()
            if (dropped.state != State.PENDING) continue
            dropped.state = State.CANCELLED
            jobs.remove(dropped.key, dropped)
            dropped.future.complete(null)
            logger.warn("翻译队列已满: action=drop, song=${dropped.songName}")
        }
    }

    private fun dispatchNextLocked() {
        while (running < MAX_RUNNING && pending.isNotEmpty()) {
            val job = pending.removeLast()
            if (job.state != State.PENDING) continue
            job.state = State.RUNNING
            job.running = true
            running++
            logger.debug(
                "启动翻译任务: song=${job.songName}, pending=${pending.size}, running=$running"
            )
            try {
                job.task = executor.submit { runJob(job) }
            } catch (error: Exception) {
                job.state = State.CANCELLED
                job.running = false
                running--
                jobs.remove(job.key, job)
                job.future.complete(null)
                logger.error("翻译任务失败: song=${job.songName}", error)
            }
        }
    }

    private fun runJob(job: TranslationJob) {
        try {
            if (job.state == State.CANCELLED || Thread.currentThread().isInterrupted) return
            val result = job.request()
            synchronized(lock) {
                if (job.state == State.CANCELLED || Thread.currentThread().isInterrupted) {
                    job.state = State.CANCELLED
                    job.future.complete(null)
                } else {
                    if (!result.isNullOrEmpty()) {
                        logger.debug("翻译任务完成: song=${job.songName}, items=${result.size}")
                    }
                    // Publish the terminal state before waking callers. A caller can release
                    // the completed job immediately after future.get() returns.
                    job.state = State.COMPLETED
                    job.future.complete(result)
                }
            }
        } catch (error: Exception) {
            synchronized(lock) {
                if (job.state != State.CANCELLED) {
                    job.state = State.COMPLETED
                    logger.error("翻译任务失败: song=${job.songName}", error)
                    job.future.complete(null)
                }
            }
        } finally {
            synchronized(lock) {
                if (job.running) {
                    job.running = false
                    running = (running - 1).coerceAtLeast(0)
                }
                // Successful and failed terminal results are retained until the consumer calls
                // release(). Cancelled jobs are removed immediately and can be retried later.
                if (job.state != State.COMPLETED) jobs.remove(job.key, job)
                dispatchNextLocked()
            }
        }
    }

    private fun release(job: TranslationJob) {
        synchronized(lock) {
            if (jobs[job.key] === job && job.state == State.COMPLETED) {
                jobs.remove(job.key, job)
                logger.debug("释放已完成翻译任务: song=${job.songName}, key=${job.key}")
            }
        }
    }

    private fun cancel(job: TranslationJob) {
        val task = synchronized(lock) {
            if (job.state == State.COMPLETED || job.state == State.CANCELLED) return@synchronized null
            if (job.state == State.PENDING) {
                logger.debug("取消等待中的翻译任务: song=${job.songName}")
            }
            job.state = State.CANCELLED
            pending.remove(job)
            jobs.remove(job.key, job)
            if (job.running) {
                job.running = false
                running = (running - 1).coerceAtLeast(0)
            }
            job.future.complete(null)
            dispatchNextLocked()
            job.task
        }
        task?.cancel(true)
    }

    private data class TranslationJob(
        val key: String,
        val songName: String,
        val future: CompletableFuture<List<TranslationItem>?>,
        val request: () -> List<TranslationItem>?,
        var task: Future<*>? = null,
        var state: State = State.PENDING,
        var running: Boolean = false,
    )

    private enum class State {
        PENDING,
        RUNNING,
        COMPLETED,
        CANCELLED
    }
}
