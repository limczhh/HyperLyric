package com.lidesheng.hyperlyric.root.lyricenhancement

import android.content.Context
import android.content.SharedPreferences
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheCommand
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheEntry
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationCodec
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationRequest
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationResponse
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationType
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheResultChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * SystemUI-side consumer for cache-management requests sent by the App.
 *
 * The feature callbacks remain the only owners of cache state.  This listener only validates the
 * request, invokes the corresponding feature operation, and returns bounded metadata.
 */
internal class LyricEnhancementCacheCommandListener(
    private val application: Context,
    private val remotePreferences: SharedPreferences?,
    private val scope: CoroutineScope,
    private val isClosed: () -> Boolean,
    private val listFeatureCache: (String) -> List<LyricEnhancementCacheEntry>?,
    private val clearFeatureCache: (String) -> Boolean,
    private val clearCacheEntry: (String, String) -> Boolean,
    private val logger: LyricEnhancementLogger,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val consuming = AtomicBoolean(false)
    private val lastConsumedCommands = mutableMapOf<String, String?>()
    private var lastConsumedOperation: String? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null ||
                key == LyricEnhancementCacheCommand.OPERATION_REQUEST_KEY ||
                key in LyricEnhancementCacheCommand.requestKeys()
            ) {
                consumePending()
            }
        }

    init {
        remotePreferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
        consumePending()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        remotePreferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun consumePending() {
        if (closed.get() || isClosed() || !consuming.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val operation = findPendingOperation()
                    if (operation != null) {
                        lastConsumedOperation = operation.encoded
                        handleOperation(operation.request)
                        continue
                    }

                    val clear = findPendingClearRequest() ?: break
                    lastConsumedCommands[clear.key] = clear.encoded
                    val cleared = runCatching {
                        clearFeatureCache(clear.request.featureId)
                    }.getOrElse { error ->
                        logger.warn(
                            "清除歌词增强缓存失败: feature=${clear.request.featureId}",
                            error
                        )
                        false
                    }
                    if (cleared) {
                        logger.info("歌词增强缓存已清除: feature=${clear.request.featureId}")
                    }
                }
            } finally {
                consuming.set(false)
                if (hasNewRequest()) consumePending()
            }
        }
    }

    private fun handleOperation(request: LyricEnhancementCacheOperationRequest) {
        val response = runCatching {
            when (request.type) {
                LyricEnhancementCacheOperationType.LIST -> {
                    val entries = listFeatureCache(request.featureId)
                        ?: return@runCatching failureResponse(request, "feature_unavailable")
                    LyricEnhancementCacheOperationResponse(
                        requestId = request.requestId,
                        success = true,
                        entries = entries
                    )
                }

                LyricEnhancementCacheOperationType.CLEAR_ALL -> {
                    val cleared = clearFeatureCache(request.featureId)
                    if (cleared) {
                        LyricEnhancementCacheOperationResponse(
                            requestId = request.requestId,
                            success = true
                        )
                    } else {
                        failureResponse(request, "cache_not_cleared")
                    }
                }

                LyricEnhancementCacheOperationType.CLEAR_ENTRY -> {
                    val entryId = request.entryId
                        ?: return@runCatching failureResponse(request, "entry_id_missing")
                    val cleared = clearCacheEntry(request.featureId, entryId)
                    if (cleared) {
                        LyricEnhancementCacheOperationResponse(
                            requestId = request.requestId,
                            success = true,
                            entryCleared = true
                        )
                    } else {
                        LyricEnhancementCacheOperationResponse(
                            requestId = request.requestId,
                            success = false,
                            entryCleared = false,
                            errorCode = "entry_not_found"
                        )
                    }
                }
            }
        }.getOrElse { error ->
            logger.warn(
                "歌词增强缓存操作失败: feature=${request.featureId}, type=${request.type}",
                error
            )
            failureResponse(request, "operation_failed")
        }

        val accepted = LyricEnhancementCacheResultChannel.publishFromSystemUi(
            context = application,
            request = request,
            response = response
        )
        if (!accepted) {
            logger.warn(
                "歌词增强缓存操作结果未被 App 接收: request=${request.requestId}",
                null
            )
        }
    }

    private fun failureResponse(
        request: LyricEnhancementCacheOperationRequest,
        errorCode: String,
    ) = LyricEnhancementCacheOperationResponse(
        requestId = request.requestId,
        success = false,
        errorCode = errorCode
    )

    private fun hasNewRequest(): Boolean =
        findPendingOperation() != null || findPendingClearRequest() != null

    private fun findPendingOperation(): PendingOperation? {
        val preferences = remotePreferences ?: return null
        val encoded = preferences.getString(
            LyricEnhancementCacheCommand.OPERATION_REQUEST_KEY,
            null
        ) ?: return null
        val request = LyricEnhancementCacheOperationCodec.decodeRequest(encoded) ?: return null
        if (
            LyricEnhancementCacheOperationCodec.isRequestExpired(
                request,
                System.currentTimeMillis(),
                LyricEnhancementCacheCommand.REQUEST_TTL_MS
            )
        ) {
            return null
        }
        return encoded.takeIf { it != lastConsumedOperation }?.let {
            PendingOperation(encoded = it, request = request)
        }
    }

    private fun findPendingClearRequest(): PendingClearRequest? {
        val preferences = remotePreferences ?: return null
        for (key in LyricEnhancementCacheCommand.requestKeys()) {
            val encoded = preferences.getString(key, null) ?: continue
            val request = LyricEnhancementCacheCommand.decodeClearRequest(encoded) ?: continue
            if (encoded != lastConsumedCommands[key]) {
                return PendingClearRequest(key, encoded, request)
            }
        }
        return null
    }

    private data class PendingOperation(
        val encoded: String,
        val request: LyricEnhancementCacheOperationRequest,
    )

    private data class PendingClearRequest(
        val key: String,
        val encoded: String,
        val request: LyricEnhancementCacheCommand.ClearRequest,
    )
}
