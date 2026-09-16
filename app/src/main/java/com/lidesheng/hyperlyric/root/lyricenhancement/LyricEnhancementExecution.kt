package com.lidesheng.hyperlyric.root.lyricenhancement

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object LyricEnhancementExecution {
    fun <T> invoke(
        operation: () -> T?,
        onFailure: (Throwable) -> Unit,
    ): T? = try {
        operation()
    } catch (error: Throwable) {
        onFailure(error)
        null
    }

    suspend fun <T> run(
        executor: ExecutorService,
        operation: () -> T?,
        timeoutMs: Long,
        onFailure: (Throwable) -> Unit,
        onTimeout: () -> Unit,
    ): T? {
        val future: Future<T?> = try {
            executor.submit<T?> {
                invoke(
                    operation = operation,
                    onFailure = onFailure
                )
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            runInterruptible {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
            }
        } catch (error: CancellationException) {
            future.cancel(true)
            throw error
        } catch (_: TimeoutException) {
            future.cancel(true)
            onTimeout()
            null
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            null
        } catch (error: ExecutionException) {
            onFailure(error.cause ?: error)
            null
        } catch (error: Exception) {
            onFailure(error)
            null
        }
    }
}
