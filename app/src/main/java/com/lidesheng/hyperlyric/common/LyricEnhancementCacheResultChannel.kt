package com.lidesheng.hyperlyric.common

import android.content.Context
import android.net.Uri
import android.os.Bundle

/**
 * App-owned mailbox for bounded results produced by SystemUI.
 *
 * RemotePreferences is used as the App -> SystemUI request path.  The hooked process does not
 * write back to that preference view, so SystemUI submits the result to this exported provider;
 * the App accepts it only when a matching request id and one-time response token are pending.
 */
internal object LyricEnhancementCacheResultChannel {
    const val METHOD_SUBMIT = "submit_lyric_enhancement_cache_result"
    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_RESPONSE_TOKEN = "responseToken"
    const val EXTRA_RESPONSE = "response"
    const val EXTRA_ACCEPTED = "accepted"

    const val RESULT_PROVIDER_AUTHORITY =
        "com.lidesheng.hyperlyric.lyric-enhancement-cache-result"

    private const val PREFERENCES = "hyperlyric_lyric_enhancement_cache_mailbox"
    private const val PENDING_PREFIX = "pending."
    private const val RESPONSE_PREFIX = "response."
    private const val TOKEN_SEPARATOR = '\u001F'
    private val uri = Uri.parse("content://$RESULT_PROVIDER_AUTHORITY")
    private val lock = Any()

    fun registerPending(
        context: Context,
        request: LyricEnhancementCacheOperationRequest,
    ) {
        synchronized(lock) {
            val preferences = preferences(context)
            cleanupExpired(preferences, System.currentTimeMillis())
            preferences.edit()
                .putString(
                    PENDING_PREFIX + request.requestId,
                    request.responseToken + TOKEN_SEPARATOR + request.createdAtEpochMs
                )
                .remove(RESPONSE_PREFIX + request.requestId)
                .apply()
        }
    }

    fun consumeResponse(
        context: Context,
        requestId: String,
    ): LyricEnhancementCacheOperationResponse? = synchronized(lock) {
        val preferences = preferences(context)
        val response = preferences.getString(RESPONSE_PREFIX + requestId, null)
            ?.let(LyricEnhancementCacheOperationCodec::decodeResponse)
            ?.takeIf { it.requestId == requestId }
        if (response != null) {
            preferences.edit()
                .remove(PENDING_PREFIX + requestId)
                .remove(RESPONSE_PREFIX + requestId)
                .apply()
        }
        response
    }

    fun clear(context: Context, requestId: String) {
        synchronized(lock) {
            preferences(context).edit()
                .remove(PENDING_PREFIX + requestId)
                .remove(RESPONSE_PREFIX + requestId)
                .apply()
        }
    }

    fun publishFromSystemUi(
        context: Context,
        request: LyricEnhancementCacheOperationRequest,
        response: LyricEnhancementCacheOperationResponse,
    ): Boolean = runCatching {
        val result = context.contentResolver.call(
            uri,
            METHOD_SUBMIT,
            null,
            Bundle().apply {
                putString(EXTRA_REQUEST_ID, request.requestId)
                putString(EXTRA_RESPONSE_TOKEN, request.responseToken)
                putString(
                    EXTRA_RESPONSE,
                    LyricEnhancementCacheOperationCodec.encodeResponse(response)
                )
            }
        )
        result?.getBoolean(EXTRA_ACCEPTED, false) == true
    }.getOrDefault(false)

    fun acceptFromSystemUi(
        context: Context,
        requestId: String?,
        responseToken: String?,
        encodedResponse: String?,
    ): Boolean {
        if (requestId.isNullOrBlank() ||
            responseToken.isNullOrBlank() ||
            encodedResponse.isNullOrBlank()
        ) {
            return false
        }
        val response = LyricEnhancementCacheOperationCodec.decodeResponse(encodedResponse)
            ?.takeIf { it.requestId == requestId }
            ?: return false

        synchronized(lock) {
            val preferences = preferences(context)
            cleanupExpired(preferences, System.currentTimeMillis())
            val pending = preferences.getString(PENDING_PREFIX + requestId, null)
                ?: return false
            val expectedToken = pending.substringBefore(TOKEN_SEPARATOR)
            if (expectedToken != responseToken) return false
            preferences.edit()
                .putString(
                    RESPONSE_PREFIX + requestId,
                    LyricEnhancementCacheOperationCodec.encodeResponse(response)
                )
                .apply()
            return true
        }
    }

    private fun preferences(context: Context) = context.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE
    )

    private fun cleanupExpired(
        preferences: android.content.SharedPreferences,
        nowEpochMs: Long,
    ) {
        val editor = preferences.edit()
        preferences.all.forEach { (key, value) ->
            when {
                key.startsWith(PENDING_PREFIX) -> {
                    val createdAt = (value as? String)
                        ?.substringAfter(TOKEN_SEPARATOR, "")
                        ?.toLongOrNull()
                    if (createdAt == null ||
                        nowEpochMs - createdAt > LyricEnhancementCacheCommand.REQUEST_TTL_MS
                    ) {
                        editor.remove(key)
                        editor.remove(RESPONSE_PREFIX + key.removePrefix(PENDING_PREFIX))
                    }
                }

                key.startsWith(RESPONSE_PREFIX) -> {
                    val response = (value as? String)
                        ?.let(LyricEnhancementCacheOperationCodec::decodeResponse)
                    if (response == null ||
                        LyricEnhancementCacheOperationCodec.isResponseExpired(
                            response,
                            nowEpochMs
                        )
                    ) {
                        editor.remove(key)
                    }
                }
            }
        }
        editor.apply()
    }
}
