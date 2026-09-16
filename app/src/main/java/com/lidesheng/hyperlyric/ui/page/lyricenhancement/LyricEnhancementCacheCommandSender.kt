package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import android.content.Context
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheCommand
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationCodec
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationRequest
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationResponse
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheOperationType
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheResultChannel
import com.lidesheng.hyperlyric.root.RootApplication
import java.util.UUID

internal enum class LyricEnhancementCacheCommandResult {
    SENT,
    UNAVAILABLE,
    FAILED,
}

internal sealed interface LyricEnhancementCacheOperationOutcome {
    data class Completed(
        val response: LyricEnhancementCacheOperationResponse,
    ) : LyricEnhancementCacheOperationOutcome

    data class Unavailable(
        val reason: String,
    ) : LyricEnhancementCacheOperationOutcome

    data class Failed(
        val reason: String,
    ) : LyricEnhancementCacheOperationOutcome
}

/** App-side client for cache operations; cache files remain owned by SystemUI. */
internal class LyricEnhancementCacheCommandSender(
    private val context: Context,
) {
    /** Kept for requests written by older App versions. */
    fun sendClearRequest(featureId: String): LyricEnhancementCacheCommandResult {
        if (!LyricEnhancementCacheCommand.isSupportedFeature(featureId)) {
            return LyricEnhancementCacheCommandResult.FAILED
        }
        val service = RootApplication.xposedService
            ?: return LyricEnhancementCacheCommandResult.UNAVAILABLE
        return runCatching {
            val remotePreferences = service.getRemotePreferences(
                LyricEnhancementCacheCommand.REMOTE_PREFERENCES
            )
            val request = LyricEnhancementCacheCommand.encodeClearRequest(featureId)
            if (
                remotePreferences.edit()
                    .putString(LyricEnhancementCacheCommand.requestKey(featureId), request)
                    .commit()
            ) {
                LyricEnhancementCacheCommandResult.SENT
            } else {
                LyricEnhancementCacheCommandResult.FAILED
            }
        }.getOrDefault(LyricEnhancementCacheCommandResult.UNAVAILABLE)
    }

    fun listEntries(featureId: String): LyricEnhancementCacheOperationOutcome = execute(
        featureId = featureId,
        type = LyricEnhancementCacheOperationType.LIST
    )

    fun clearAll(featureId: String): LyricEnhancementCacheOperationOutcome = execute(
        featureId = featureId,
        type = LyricEnhancementCacheOperationType.CLEAR_ALL
    )

    fun clearEntry(
        featureId: String,
        entryId: String,
    ): LyricEnhancementCacheOperationOutcome = execute(
        featureId = featureId,
        type = LyricEnhancementCacheOperationType.CLEAR_ENTRY,
        entryId = entryId
    )

    private fun execute(
        featureId: String,
        type: LyricEnhancementCacheOperationType,
        entryId: String? = null,
    ): LyricEnhancementCacheOperationOutcome {
        if (!LyricEnhancementCacheCommand.isSupportedFeature(featureId)) {
            return LyricEnhancementCacheOperationOutcome.Failed("unsupported_feature")
        }
        val service = RootApplication.xposedService
            ?: return LyricEnhancementCacheOperationOutcome.Unavailable("xposed_service_unavailable")

        val request = LyricEnhancementCacheOperationRequest(
            requestId = UUID.randomUUID().toString(),
            responseToken = UUID.randomUUID().toString(),
            featureId = featureId,
            type = type,
            entryId = entryId
        )
        var remotePreferences: android.content.SharedPreferences? = null
        LyricEnhancementCacheResultChannel.registerPending(context, request)
        return try {
            remotePreferences = service.getRemotePreferences(
                LyricEnhancementCacheCommand.REMOTE_PREFERENCES
            )
            val encoded = LyricEnhancementCacheOperationCodec.encodeRequest(request)
            val written = remotePreferences.edit()
                .putString(LyricEnhancementCacheCommand.OPERATION_REQUEST_KEY, encoded)
                .commit()
            if (!written) {
                return LyricEnhancementCacheOperationOutcome.Failed("request_write_failed")
            }

            val deadline = System.currentTimeMillis() +
                    LyricEnhancementCacheCommand.OPERATION_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                LyricEnhancementCacheResultChannel.consumeResponse(
                    context,
                    request.requestId
                )?.let { response ->
                    return LyricEnhancementCacheOperationOutcome.Completed(response)
                }
                try {
                    Thread.sleep(100L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return LyricEnhancementCacheOperationOutcome.Unavailable("interrupted")
                }
            }
            LyricEnhancementCacheOperationOutcome.Unavailable("timeout")
        } catch (error: Exception) {
            LyricEnhancementCacheOperationOutcome.Unavailable(
                error.message?.take(120) ?: "operation_failed"
            )
        } finally {
            remotePreferences?.let { preferences ->
                runCatching {
                    if (
                        preferences.getString(
                            LyricEnhancementCacheCommand.OPERATION_REQUEST_KEY,
                            null
                        ) == LyricEnhancementCacheOperationCodec.encodeRequest(request)
                    ) {
                        preferences.edit()
                            .remove(LyricEnhancementCacheCommand.OPERATION_REQUEST_KEY)
                            .commit()
                    }
                }
            }
            LyricEnhancementCacheResultChannel.clear(context, request.requestId)
        }
    }
}
