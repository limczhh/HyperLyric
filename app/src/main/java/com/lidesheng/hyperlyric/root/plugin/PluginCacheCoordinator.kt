package com.lidesheng.hyperlyric.root.plugin

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.plugin.core.PluginCacheFileLayout
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationCodec
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationReplayTracker
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationRequest
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationResponse
import com.lidesheng.hyperlyric.plugin.core.PluginCacheOperationType
import com.lidesheng.hyperlyric.plugin.core.PluginCacheResultChannel
import com.lidesheng.hyperlyric.plugin.core.PluginConstants
import com.lidesheng.hyperlyric.root.utils.HookLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Owns the SystemUI-side cache-management protocol and its bounded worker. */
internal class PluginCacheCoordinator(
    private val application: Application,
    private val scope: CoroutineScope,
    private val isClosed: () -> Boolean,
    private val cancelActiveProcessing: () -> Unit,
    private val clearLoadedPluginCache: (String) -> Unit,
    private val executeOperation: (PluginCacheOperationRequest) -> PluginCacheOperationResponse,
) {
    companion object {
        private const val TAG = "PluginRuntime"
        private const val LAST_CACHE_CLEAR_TOKEN_KEY = "__hyperlyric_core_last_clear_token"
        private const val CACHE_OPERATION_RESULT_PREFIX = "result."
        private const val MAX_STORED_CACHE_OPERATION_RESULTS = 16
    }

    private val operationExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "HyperLyric-PluginCacheOperation").apply { isDaemon = true }
    }
    private val consuming = AtomicBoolean(false)
    private val replayTracker = PluginCacheOperationReplayTracker()

    fun consumePendingCacheClears(registry: SharedPreferences?) {
        val tokens = runCatching {
            registry?.getStringSet(
                PluginConstants.REMOTE_CACHE_CLEAR_TOKENS_KEY,
                emptySet()
            ).orEmpty()
        }.onFailure { error ->
            HookLogger.w(TAG, "读取插件缓存清理请求失败", error)
        }.getOrDefault(emptySet())
        tokens.forEach { encoded ->
            val separator = encoded.indexOf('\u001F')
            if (separator <= 0 || separator == encoded.lastIndex) return@forEach
            val pluginId = encoded.substring(0, separator)
            val token = encoded.substring(separator + 1)
            if (!PluginCacheFileLayout.isValidPluginId(pluginId)) {
                HookLogger.w(TAG, "忽略非法插件缓存清理请求: id=$pluginId")
                return@forEach
            }
            runCatching {
                val marker = application.getSharedPreferences(
                    PluginConstants.cacheMetadataPreferences(pluginId),
                    Context.MODE_PRIVATE
                )
                if (marker.getString(LAST_CACHE_CLEAR_TOKEN_KEY, null) == token) {
                    return@runCatching
                }
                val legacyPreferences = application.getSharedPreferences(
                    PluginConstants.cachePreferences(pluginId),
                    Context.MODE_PRIVATE
                )
                if (legacyPreferences.all.isNotEmpty()) {
                    val cleared = legacyPreferences.edit().clear().commit()
                    check(cleared) { "cache clear commit returned false" }
                }
                cancelActiveProcessing()
                clearLoadedPluginCache(pluginId)
                clearPluginCacheFiles(pluginId)
                val marked = marker.edit().putString(LAST_CACHE_CLEAR_TOKEN_KEY, token).commit()
                check(marked) { "cache clear marker commit returned false" }
                HookLogger.i(TAG, "插件缓存已清理: id=$pluginId")
            }.onFailure { error ->
                HookLogger.w(TAG, "插件缓存清理失败: id=$pluginId", error)
            }
        }
    }

    /**
     * Reads bounded cache-management requests from the App. The target-process
     * RemotePreferences view is read-only, so each result is submitted to the App-owned provider
     * instead of attempting to write the registry from SystemUI.
     */
    fun consumePendingCacheOperations(registry: SharedPreferences?) {
        if (isClosed() || !consuming.compareAndSet(false, true)) return
        scope.launch {
            var snapshotRequestIds: Set<String> = emptySet()
            var requestSnapshotRead = false
            try {
                val now = System.currentTimeMillis()
                val pendingRequests = runCatching {
                    registry?.getStringSet(
                        PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
                        emptySet()
                    ).orEmpty()
                }.onSuccess {
                    requestSnapshotRead = true
                }.getOrElse { error ->
                    HookLogger.w(TAG, "读取插件缓存操作请求失败", error)
                    emptySet()
                }.mapNotNull(PluginCacheOperationCodec::decodeRequest)
                snapshotRequestIds = pendingRequests.mapTo(linkedSetOf()) { it.requestId }
                val requests = pendingRequests
                    .sortedBy { it.createdAtEpochMs }
                    .take(PluginCacheOperationCodec.MAX_ACTIVE_REQUESTS)

                requests.forEach { request ->
                    if (isClosed()) return@forEach
                    val response = when {
                        PluginCacheOperationCodec.isRequestExpired(request, now) -> {
                            PluginCacheOperationResponse(
                                requestId = request.requestId,
                                success = false,
                                errorCode = "request_expired"
                            )
                        }

                        else -> storedCacheOperationResponse(request)
                            ?.also(replayTracker::markCompleted)
                            ?: replayTracker.completedResponse(request.requestId)
                            ?: executeCacheOperation(request).also {
                                storeCacheOperationResponse(request.pluginId, it)
                                replayTracker.markCompleted(it)
                            }
                    }
                    if (!PluginCacheResultChannel.publishFromSystemUi(application, request, response)) {
                        HookLogger.w(
                            TAG,
                            "回传插件缓存操作结果失败: plugin=${request.pluginId}, " +
                                    "scope=${request.scopeId}"
                        )
                    }
                }
            } finally {
                consuming.set(false)
                if (
                    requestSnapshotRead &&
                    hasNewPendingCacheOperation(registry, snapshotRequestIds)
                ) {
                    consumePendingCacheOperations(registry)
                }
            }
        }
    }

    fun close() {
        operationExecutor.shutdownNow()
    }

    private fun hasNewPendingCacheOperation(
        registry: SharedPreferences?,
        snapshotRequestIds: Set<String>
    ): Boolean {
        val now = System.currentTimeMillis()
        return runCatching {
            registry?.getStringSet(
                PluginConstants.REMOTE_CACHE_OPERATION_REQUESTS_KEY,
                emptySet()
            ).orEmpty().mapNotNull(PluginCacheOperationCodec::decodeRequest)
                .any { request ->
                    !PluginCacheOperationCodec.isRequestExpired(request, now) &&
                            request.requestId !in snapshotRequestIds
                }
        }.onFailure { error ->
            HookLogger.w(TAG, "复查插件缓存操作请求失败", error)
        }.getOrDefault(false)
    }

    private fun executeCacheOperation(
        request: PluginCacheOperationRequest
    ): PluginCacheOperationResponse {
        if (PluginCacheOperationCodec.isOperationTimedOut(request, System.currentTimeMillis())) {
            return cacheOperationFailure(request, "operation_timeout")
        }
        if (request.type != PluginCacheOperationType.LIST) {
            // A clear must not race with an in-flight processor and repopulate the cache later.
            cancelActiveProcessing()
        }
        val task = try {
            operationExecutor.submit<PluginCacheOperationResponse> {
                if (
                    Thread.currentThread().isInterrupted ||
                    PluginCacheOperationCodec.isOperationTimedOut(request, System.currentTimeMillis())
                ) {
                    return@submit cacheOperationFailure(request, "operation_timeout")
                }
                executeOperation(request)
            }
        } catch (error: Exception) {
            HookLogger.w(TAG, "无法提交插件缓存操作", error)
            return cacheOperationFailure(request, "runtime_unavailable")
        }

        val timeoutMs = PluginCacheOperationCodec.remainingOperationTimeoutMs(
            request,
            System.currentTimeMillis()
        )
        if (timeoutMs == 0L) {
            task.cancel(true)
            return cacheOperationFailure(request, "operation_timeout")
        }
        return try {
            task.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            task.cancel(true)
            HookLogger.w(
                TAG,
                "插件缓存操作超时: plugin=${request.pluginId}, scope=${request.scopeId}"
            )
            cacheOperationFailure(request, "operation_timeout")
        } catch (error: Exception) {
            task.cancel(true)
            HookLogger.w(TAG, "插件缓存操作异常", error)
            cacheOperationFailure(request, "runtime_failed")
        }
    }

    /** The tombstone is marked only after both the legacy and file-backed cache are cleared. */
    private fun clearPluginCacheFiles(pluginId: String) {
        val directory = PluginCacheFileLayout.directory(application, pluginId)
        directory.listFiles()?.forEach { file ->
            if (file.isFile) {
                check(file.delete()) { "cannot delete plugin cache file: ${file.name}" }
            }
        }
    }

    private fun cacheOperationFailure(
        request: PluginCacheOperationRequest,
        errorCode: String
    ): PluginCacheOperationResponse = PluginCacheOperationResponse(
        requestId = request.requestId,
        success = false,
        errorCode = errorCode
    )

    private fun storedCacheOperationResponse(
        request: PluginCacheOperationRequest
    ): PluginCacheOperationResponse? {
        val preferences = application.getSharedPreferences(
            PluginConstants.cacheOperationPreferences(request.pluginId),
            Context.MODE_PRIVATE
        )
        return preferences.getString(CACHE_OPERATION_RESULT_PREFIX + request.requestId, null)
            ?.let(PluginCacheOperationCodec::decodeResponse)
            ?.takeIf { it.requestId == request.requestId }
    }

    private fun storeCacheOperationResponse(
        pluginId: String,
        response: PluginCacheOperationResponse
    ) {
        val encoded = runCatching { PluginCacheOperationCodec.encodeResponse(response) }
            .getOrElse { error ->
                HookLogger.w(TAG, "插件缓存操作结果过大或非法", error)
                return
            }
        val preferences = application.getSharedPreferences(
            PluginConstants.cacheOperationPreferences(pluginId),
            Context.MODE_PRIVATE
        )
        val retainedKeys = preferences.all
            .filterKeys { it.startsWith(CACHE_OPERATION_RESULT_PREFIX) }
            .mapNotNull { (key, value) ->
                (value as? String)?.let(PluginCacheOperationCodec::decodeResponse)
                    ?.let { key to it }
            }
            .sortedByDescending { it.second.completedAtEpochMs }
            .take(MAX_STORED_CACHE_OPERATION_RESULTS - 1)
            .map { it.first }
            .toSet()
        preferences.edit().apply {
            preferences.all.keys
                .filter { it.startsWith(CACHE_OPERATION_RESULT_PREFIX) && it !in retainedKeys }
                .forEach(::remove)
            putString(CACHE_OPERATION_RESULT_PREFIX + response.requestId, encoded)
            apply()
        }
    }
}
