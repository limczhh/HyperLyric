package com.lidesheng.hyperlyric.common

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bounded protocol for cache management requests sent from the App to SystemUI.
 *
 * The cache body never crosses this boundary.  Only the small amount of metadata needed by the
 * management pages is returned, and the cache entry id is treated as an opaque capability.
 */
internal enum class LyricEnhancementCacheOperationType(val wireName: String) {
    LIST("list"),
    CLEAR_ALL("clearAll"),
    CLEAR_ENTRY("clearEntry");

    companion object {
        fun fromWireName(value: String): LyricEnhancementCacheOperationType? =
            entries.firstOrNull { it.wireName == value }
    }
}

internal data class LyricEnhancementCacheOperationRequest(
    val requestId: String,
    val responseToken: String,
    val featureId: String,
    val type: LyricEnhancementCacheOperationType,
    val entryId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

/** Small, bounded metadata returned to the cache-management UI. */
internal data class LyricEnhancementCacheDetail(
    val label: String,
    val value: String,
)

internal data class LyricEnhancementCacheEntry(
    val id: String,
    val title: String,
    val artist: String?,
    val sizeBytes: Long? = null,
    val updatedAtEpochMs: Long? = null,
    val details: List<LyricEnhancementCacheDetail> = emptyList(),
)

internal data class LyricEnhancementCacheOperationResponse(
    val requestId: String,
    val success: Boolean,
    val entries: List<LyricEnhancementCacheEntry> = emptyList(),
    val entryCleared: Boolean? = null,
    val errorCode: String? = null,
    val completedAtEpochMs: Long = System.currentTimeMillis(),
)

internal object LyricEnhancementCacheOperationCodec {
    const val MAX_REQUEST_BYTES = 4 * 1024
    const val MAX_RESPONSE_BYTES = 32 * 1024
    const val MAX_ENTRY_COUNT = 100
    const val MAX_ID_LENGTH = 128
    const val MAX_TITLE_LENGTH = 160
    const val MAX_ARTIST_LENGTH = 320
    const val MAX_DETAILS_PER_ENTRY = 10
    const val MAX_DETAIL_LABEL_LENGTH = 32
    const val MAX_DETAIL_VALUE_LENGTH = 120
    const val RESPONSE_TTL_MS = 5 * 60 * 1000L

    private val requestIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{7,79}")
    private val featureIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun encodeRequest(request: LyricEnhancementCacheOperationRequest): String {
        validateRequest(request)
        return JSONObject()
            .put("requestId", request.requestId)
            .put("responseToken", request.responseToken)
            .put("featureId", request.featureId)
            .put("type", request.type.wireName)
            .put("createdAtEpochMs", request.createdAtEpochMs)
            .also { json -> request.entryId?.let { json.put("entryId", it) } }
            .toString()
            .also(::requireRequestSize)
    }

    fun decodeRequest(encoded: String): LyricEnhancementCacheOperationRequest? = runCatching {
        requireRequestSize(encoded)
        val json = JSONObject(encoded)
        val type = LyricEnhancementCacheOperationType.fromWireName(
            json.requiredString("type")
        ) ?: throw IllegalArgumentException("Unsupported cache operation")
        LyricEnhancementCacheOperationRequest(
            requestId = json.requiredString("requestId"),
            responseToken = json.requiredString("responseToken"),
            featureId = json.requiredString("featureId"),
            type = type,
            entryId = json.optionalString("entryId"),
            createdAtEpochMs = json.optLong("createdAtEpochMs", 0L)
        ).also(::validateRequest)
    }.getOrNull()

    fun encodeResponse(response: LyricEnhancementCacheOperationResponse): String {
        val safe = response.copy(entries = sanitizeEntries(response.entries))
        var entries = safe.entries
        while (true) {
            val encoded = encodeResponseJson(safe.copy(entries = entries))
            if (encoded.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES) return encoded
            if (entries.isEmpty()) {
                requireResponseSize(encoded)
                return encoded
            }
            entries = entries.dropLast(1)
        }
    }

    fun decodeResponse(encoded: String): LyricEnhancementCacheOperationResponse? = runCatching {
        requireResponseSize(encoded)
        val json = JSONObject(encoded)
        val requestId = json.requiredString("requestId")
        require(requestIdPattern.matches(requestId)) { "Invalid cache response request id" }
        LyricEnhancementCacheOperationResponse(
            requestId = requestId,
            success = json.optBoolean("success", false),
            entries = decodeEntries(json.optJSONArray("entries")),
            entryCleared = if (json.has("entryCleared")) {
                json.optBoolean("entryCleared")
            } else {
                null
            },
            errorCode = json.optionalString("errorCode")?.take(80),
            completedAtEpochMs = json.optLong("completedAtEpochMs", 0L)
        )
    }.getOrNull()

    fun sanitizeEntries(
        entries: List<LyricEnhancementCacheEntry>
    ): List<LyricEnhancementCacheEntry> = buildList {
        entries.asSequence().take(MAX_ENTRY_COUNT).forEach { entry ->
            val id = entry.id.takeIf { it.isNotBlank() }?.take(MAX_ID_LENGTH)
                ?: return@forEach
            val title = entry.title.takeIf { it.isNotBlank() }?.take(MAX_TITLE_LENGTH)
                ?: return@forEach
            add(
                LyricEnhancementCacheEntry(
                    id = id,
                    title = title,
                    artist = entry.artist?.takeIf { it.isNotBlank() }?.take(MAX_ARTIST_LENGTH),
                    sizeBytes = entry.sizeBytes?.takeIf { it >= 0L },
                    updatedAtEpochMs = entry.updatedAtEpochMs?.takeIf { it > 0L },
                    details = sanitizeDetails(entry.details)
                )
            )
        }
    }

    fun isRequestExpired(
        request: LyricEnhancementCacheOperationRequest,
        nowEpochMs: Long,
        requestTtlMs: Long,
    ): Boolean = request.createdAtEpochMs <= 0L ||
            nowEpochMs - request.createdAtEpochMs > requestTtlMs

    fun isResponseExpired(
        response: LyricEnhancementCacheOperationResponse,
        nowEpochMs: Long,
    ): Boolean = response.completedAtEpochMs <= 0L ||
            nowEpochMs - response.completedAtEpochMs > RESPONSE_TTL_MS

    private fun encodeResponseJson(
        response: LyricEnhancementCacheOperationResponse,
    ): String = JSONObject()
        .put("requestId", response.requestId)
        .put("success", response.success)
        .put("completedAtEpochMs", response.completedAtEpochMs)
        .also { json ->
            response.entryCleared?.let { json.put("entryCleared", it) }
            response.errorCode?.takeIf { it.isNotBlank() }?.let {
                json.put("errorCode", it.take(80))
            }
            if (response.entries.isNotEmpty()) {
                json.put("entries", JSONArray().apply {
                    response.entries.forEach { entry ->
                        put(
                            JSONObject()
                                .put("id", entry.id)
                                .put("title", entry.title)
                                .also { item ->
                                    entry.artist?.let { item.put("artist", it) }
                                    entry.sizeBytes?.let { item.put("sizeBytes", it) }
                                    entry.updatedAtEpochMs?.let {
                                        item.put("updatedAtEpochMs", it)
                                    }
                                    if (entry.details.isNotEmpty()) {
                                        item.put("details", JSONArray().apply {
                                            entry.details.forEach { detail ->
                                                put(
                                                    JSONObject()
                                                        .put("label", detail.label)
                                                        .put("value", detail.value)
                                                )
                                            }
                                        })
                                    }
                                }
                        )
                    }
                })
            }
        }
        .toString()

    private fun decodeEntries(array: JSONArray?): List<LyricEnhancementCacheEntry> {
        if (array == null || array.length() > MAX_ENTRY_COUNT) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optionalString("id")?.takeIf { it.isNotBlank() } ?: continue
                val title = item.optionalString("title")?.takeIf { it.isNotBlank() } ?: continue
                add(
                    LyricEnhancementCacheEntry(
                        id = id,
                        title = title,
                        artist = item.optionalString("artist"),
                        sizeBytes = item.optLong("sizeBytes", -1L)
                            .takeIf { it >= 0L },
                        updatedAtEpochMs = item.optLong("updatedAtEpochMs", -1L)
                            .takeIf { it > 0L },
                        details = decodeDetails(item.optJSONArray("details"))
                    )
                )
            }
        }.let(::sanitizeEntries)
    }

    private fun validateRequest(request: LyricEnhancementCacheOperationRequest) {
        require(requestIdPattern.matches(request.requestId)) { "Invalid cache request id" }
        require(requestIdPattern.matches(request.responseToken)) {
            "Invalid cache response token"
        }
        require(featureIdPattern.matches(request.featureId)) { "Invalid cache feature id" }
        require(LyricEnhancementConstants.isSupportedFeature(request.featureId)) {
            "Unsupported lyric enhancement feature"
        }
        require(request.createdAtEpochMs > 0L) { "Invalid cache request time" }
        if (request.type == LyricEnhancementCacheOperationType.CLEAR_ENTRY) {
            require(!request.entryId.isNullOrBlank()) { "Cache entry id is missing" }
        }
        require(request.entryId == null || request.entryId.length <= MAX_ID_LENGTH) {
            "Cache entry id is too long"
        }
    }

    private fun requireRequestSize(value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_REQUEST_BYTES) {
            "Cache request is too large"
        }
    }

    private fun requireResponseSize(value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_RESPONSE_BYTES) {
            "Cache response is too large"
        }
    }

    private fun JSONObject.requiredString(key: String): String =
        optionalString(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing cache protocol field: $key")

    private fun JSONObject.optionalString(key: String): String? =
        opt(key)?.takeUnless { it === JSONObject.NULL }?.toString()

    private fun sanitizeDetails(
        details: List<LyricEnhancementCacheDetail>,
    ): List<LyricEnhancementCacheDetail> = details.asSequence()
        .mapNotNull { detail ->
            val label = detail.label.takeIf { it.isNotBlank() }?.take(MAX_DETAIL_LABEL_LENGTH)
                ?: return@mapNotNull null
            val value = detail.value.takeIf { it.isNotBlank() }?.take(MAX_DETAIL_VALUE_LENGTH)
                ?: return@mapNotNull null
            LyricEnhancementCacheDetail(label, value)
        }
        .take(MAX_DETAILS_PER_ENTRY)
        .toList()

    private fun decodeDetails(array: JSONArray?): List<LyricEnhancementCacheDetail> {
        if (array == null || array.length() > MAX_DETAILS_PER_ENTRY) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optionalString("label") ?: continue
                val value = item.optionalString("value") ?: continue
                add(LyricEnhancementCacheDetail(label, value))
            }
        }.let(::sanitizeDetails)
    }
}
