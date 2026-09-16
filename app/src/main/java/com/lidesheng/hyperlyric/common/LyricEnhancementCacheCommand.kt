package com.lidesheng.hyperlyric.common

/**
 * The small App/SystemUI mailbox used to manage a built-in cache.
 *
 * Cache bodies never cross the process boundary. SystemUI owns the cache, receives short-lived
 * operation requests through RemotePreferences, and returns bounded metadata through the result
 * channel.
 */
internal object LyricEnhancementCacheCommand {
    const val REMOTE_PREFERENCES = "hyperlyric.lyric_enhancement.control"
    /** App-to-SystemUI mailbox for LIST/CLEAR_ENTRY/CLEAR_ALL operations. */
    const val OPERATION_REQUEST_KEY = "operation_request"
    const val OPERATION_TIMEOUT_MS = 8_000L
    /** Kept so SystemUI can consume requests written by the previous app version. */
    const val CLEAR_REQUEST_KEY = "clear_request"
    const val REQUEST_TTL_MS = 2 * 60 * 1000L

    private const val AMLL_CLEAR_REQUEST_KEY = "clear_request.amll_ttml"
    private const val AI_TRANSLATION_CLEAR_REQUEST_KEY = "clear_request.ai_translation"

    private const val SEPARATOR = '\u001F'

    fun encodeClearRequest(
        featureId: String,
        createdAtEpochMs: Long = System.currentTimeMillis(),
    ): String {
        require(isSupportedFeature(featureId)) { "Unsupported lyric enhancement feature" }
        require(createdAtEpochMs > 0L) { "Invalid cache request time" }
        return featureId + SEPARATOR + createdAtEpochMs
    }

    fun decodeClearRequest(
        encoded: String,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ClearRequest? = runCatching {
        val separator = encoded.lastIndexOf(SEPARATOR)
        require(separator > 0 && separator < encoded.lastIndex) { "Invalid cache request" }
        val featureId = encoded.substring(0, separator)
        val createdAtEpochMs = encoded.substring(separator + 1).toLong()
        require(isSupportedFeature(featureId)) { "Unsupported lyric enhancement feature" }
        require(createdAtEpochMs > 0L) { "Invalid cache request time" }
        require(nowEpochMs - createdAtEpochMs <= REQUEST_TTL_MS) { "Expired cache request" }
        ClearRequest(featureId, createdAtEpochMs)
    }.getOrNull()

    fun isSupportedFeature(featureId: String): Boolean = featureId ==
            LyricEnhancementConstants.AMLL_TTML_FEATURE_ID || featureId ==
            LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID

    fun requestKey(featureId: String): String = when (featureId) {
        LyricEnhancementConstants.AMLL_TTML_FEATURE_ID -> AMLL_CLEAR_REQUEST_KEY
        LyricEnhancementConstants.AI_TRANSLATION_FEATURE_ID -> AI_TRANSLATION_CLEAR_REQUEST_KEY
        else -> error("Unsupported lyric enhancement feature")
    }

    fun requestKeys(): List<String> = listOf(
        AMLL_CLEAR_REQUEST_KEY,
        AI_TRANSLATION_CLEAR_REQUEST_KEY,
        CLEAR_REQUEST_KEY,
    )

    data class ClearRequest(
        val featureId: String,
        val createdAtEpochMs: Long,
    )
}
