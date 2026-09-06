package com.lidesheng.hyperlyric.plugin.core

import com.lidesheng.hyperlyric.plugin.api.PluginCacheDetail
import com.lidesheng.hyperlyric.plugin.api.PluginCacheEntry
import org.json.JSONArray
import org.json.JSONObject

/** Bounded wire protocol carried in the existing plugin RemotePreferences registry. */
internal enum class PluginCacheOperationType(val wireName: String) {
    LIST("list"),
    CLEAR_ALL("clearAll"),
    CLEAR_ENTRY("clearEntry");

    companion object {
        fun fromWireName(value: String): PluginCacheOperationType? =
            entries.firstOrNull { it.wireName == value }
    }
}

internal data class PluginCacheOperationRequest(
    val requestId: String,
    /** App-generated capability required when SystemUI submits the response to the App provider. */
    val responseToken: String,
    val pluginId: String,
    val scopeId: String,
    val type: PluginCacheOperationType,
    val entryId: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

internal data class PluginCacheOperationResponse(
    val requestId: String,
    val success: Boolean,
    val entries: List<PluginCacheEntry> = emptyList(),
    val entryCleared: Boolean? = null,
    val errorCode: String? = null,
    val completedAtEpochMs: Long = System.currentTimeMillis(),
)

/** In-process fast path; Runtime also persists completed responses for SystemUI restarts. */
internal class PluginCacheOperationReplayTracker {
    private val responses = LinkedHashMap<String, PluginCacheOperationResponse>()

    fun completedResponse(requestId: String): PluginCacheOperationResponse? = responses[requestId]

    fun markCompleted(response: PluginCacheOperationResponse) {
        responses[response.requestId] = response
        while (responses.size > PluginCacheOperationCodec.MAX_RESPONSE_RECORDS) {
            responses.remove(responses.entries.first().key)
        }
    }
}

internal object PluginCacheOperationCodec {
    const val MAX_ACTIVE_REQUESTS = 8
    const val MAX_RESPONSE_RECORDS = 8
    const val MAX_REQUEST_BYTES = 4 * 1024
    const val MAX_RESPONSE_BYTES = 32 * 1024
    const val MAX_ENTRY_COUNT = 100
    const val MAX_ID_LENGTH = 256
    const val MAX_TITLE_LENGTH = 160
    const val MAX_SUMMARY_LENGTH = 320

    /** 单条目详情行数预算；超限在 sanitize 中截断 */
    const val MAX_DETAILS_PER_ENTRY = 10
    const val MAX_DETAIL_LABEL_LENGTH = 32
    const val MAX_DETAIL_VALUE_LENGTH = 120
    const val REQUEST_TTL_MS = 2 * 60 * 1000L
    const val RESPONSE_TTL_MS = 5 * 60 * 1000L

    private val requestIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{7,79}")
    private val scopeIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun encodeRequest(request: PluginCacheOperationRequest): String {
        validateRequest(request)
        return JSONObject()
            .put("requestId", request.requestId)
            .put("responseToken", request.responseToken)
            .put("pluginId", request.pluginId)
            .put("scopeId", request.scopeId)
            .put("type", request.type.wireName)
            .put("createdAtEpochMs", request.createdAtEpochMs)
            .also { json -> request.entryId?.let { json.put("entryId", it) } }
            .toString()
            .also(::requireRequestSize)
    }

    fun decodeRequest(encoded: String): PluginCacheOperationRequest? = runCatching {
        requireRequestSize(encoded)
        val json = JSONObject(encoded)
        val type = PluginCacheOperationType.fromWireName(json.requiredString("type"))
            ?: throw IllegalArgumentException("Unsupported cache operation")
        PluginCacheOperationRequest(
            requestId = json.requiredString("requestId"),
            responseToken = json.requiredString("responseToken"),
            pluginId = json.requiredString("pluginId"),
            scopeId = json.requiredString("scopeId"),
            type = type,
            entryId = json.optionalString("entryId"),
            createdAtEpochMs = json.optLong("createdAtEpochMs", 0L)
        ).also(::validateRequest)
    }.getOrNull()

    fun encodeResponse(response: PluginCacheOperationResponse): String {
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

    private fun encodeResponseJson(response: PluginCacheOperationResponse): String {
        val json = JSONObject()
            .put("requestId", response.requestId)
            .put("success", response.success)
            .put("completedAtEpochMs", response.completedAtEpochMs)
        response.entryCleared?.let { json.put("entryCleared", it) }
        response.errorCode?.takeIf { it.isNotBlank() }?.let { json.put("errorCode", it.take(80)) }
        if (response.entries.isNotEmpty()) {
            json.put("entries", JSONArray().apply {
                response.entries.forEach { entry ->
                    put(
                        JSONObject()
                            .put("id", entry.id)
                            .put("title", entry.title)
                            .also { item ->
                                entry.summary?.let { item.put("summary", it) }
                                entry.sizeBytes?.let { item.put("sizeBytes", it) }
                                entry.updatedAtEpochMs?.let { item.put("updatedAtEpochMs", it) }
                                if (entry.details.isNotEmpty()) {
                                    item.put(
                                        "details",
                                        JSONArray().apply {
                                            entry.details.forEach { detail ->
                                                put(
                                                    JSONObject()
                                                        .put("label", detail.label)
                                                        .put("value", detail.value)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                    )
                }
            })
        }
        return json.toString()
    }

    fun decodeResponse(encoded: String): PluginCacheOperationResponse? = runCatching {
        requireResponseSize(encoded)
        val json = JSONObject(encoded)
        val requestId = json.requiredString("requestId")
        require(requestIdPattern.matches(requestId)) { "Invalid cache response request id" }
        val entries = decodeEntries(json.optJSONArray("entries"))
        PluginCacheOperationResponse(
            requestId = requestId,
            success = json.optBoolean("success", false),
            entries = entries,
            entryCleared = if (json.has("entryCleared")) json.optBoolean("entryCleared") else null,
            errorCode = json.optionalString("errorCode")?.take(MAX_TITLE_LENGTH),
            completedAtEpochMs = json.optLong("completedAtEpochMs", 0L)
        )
    }.getOrNull()

    fun sanitizeEntries(entries: List<PluginCacheEntry>): List<PluginCacheEntry> = buildList {
        entries.asSequence().take(MAX_ENTRY_COUNT).forEach { entry ->
            val id = entry.id.takeIf {
                it.isNotBlank() && it.length <= MAX_ID_LENGTH
            } ?: return@forEach
            val title = entry.title.takeIf { it.isNotBlank() }?.take(MAX_TITLE_LENGTH)
                ?: return@forEach
            add(
                PluginCacheEntry(
                    id = id,
                    title = title,
                    summary = entry.summary?.takeIf { it.isNotBlank() }?.take(MAX_SUMMARY_LENGTH),
                    sizeBytes = entry.sizeBytes?.takeIf { it >= 0L },
                    updatedAtEpochMs = entry.updatedAtEpochMs?.takeIf { it > 0L },
                    details = sanitizeDetails(entry.details)
                )
            )
        }
    }

    /** 详情截断归一：条数/label/value 分别对齐预算；裁剪后 label 为空的整条丢弃 */
    private fun sanitizeDetails(details: List<PluginCacheDetail>): List<PluginCacheDetail> =
        buildList {
            details.asSequence().take(MAX_DETAILS_PER_ENTRY).forEach { detail ->
                val label = detail.label.take(MAX_DETAIL_LABEL_LENGTH)
                    .takeIf { it.isNotBlank() } ?: return@forEach
                add(PluginCacheDetail(label, detail.value.take(MAX_DETAIL_VALUE_LENGTH)))
            }
        }

    fun isRequestExpired(request: PluginCacheOperationRequest, nowEpochMs: Long): Boolean =
        request.createdAtEpochMs <= 0L || nowEpochMs - request.createdAtEpochMs > REQUEST_TTL_MS

    fun isResponseExpired(response: PluginCacheOperationResponse, nowEpochMs: Long): Boolean =
        response.completedAtEpochMs <= 0L || nowEpochMs - response.completedAtEpochMs > RESPONSE_TTL_MS

    /** Shared end-to-end deadline, counted from App-side request creation. */
    fun operationDeadlineEpochMs(request: PluginCacheOperationRequest): Long =
        request.createdAtEpochMs + PluginConstants.MAX_CACHE_OPERATION_TIMEOUT_MS

    fun remainingOperationTimeoutMs(
        request: PluginCacheOperationRequest,
        nowEpochMs: Long
    ): Long = (operationDeadlineEpochMs(request) - nowEpochMs).coerceAtLeast(0L)

    fun isOperationTimedOut(request: PluginCacheOperationRequest, nowEpochMs: Long): Boolean =
        remainingOperationTimeoutMs(request, nowEpochMs) == 0L

    /** A missing or failed delete is never represented as a successful cache operation. */
    fun clearEntryResponse(
        request: PluginCacheOperationRequest,
        entryCleared: Boolean
    ): PluginCacheOperationResponse = if (entryCleared) {
        PluginCacheOperationResponse(
            requestId = request.requestId,
            success = true,
            entryCleared = true
        )
    } else {
        PluginCacheOperationResponse(
            requestId = request.requestId,
            success = false,
            entryCleared = false,
            errorCode = "entry_not_cleared"
        )
    }

    private fun validateRequest(request: PluginCacheOperationRequest) {
        require(requestIdPattern.matches(request.requestId)) { "Invalid cache request id" }
        require(requestIdPattern.matches(request.responseToken)) { "Invalid cache response token" }
        require(scopeIdPattern.matches(request.pluginId)) { "Invalid cache plugin id" }
        require(scopeIdPattern.matches(request.scopeId)) { "Invalid cache scope id" }
        require(request.createdAtEpochMs > 0L) { "Invalid cache request time" }
        if (request.type == PluginCacheOperationType.CLEAR_ENTRY) {
            require(!request.entryId.isNullOrBlank()) { "Cache entry id is missing" }
        }
        require(request.entryId == null || request.entryId.length <= MAX_ID_LENGTH) {
            "Cache entry id is too long"
        }
    }

    private fun decodeEntries(array: JSONArray?): List<PluginCacheEntry> {
        if (array == null || array.length() > MAX_ENTRY_COUNT) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optionalString("id")?.takeIf { it.isNotBlank() } ?: continue
                val title = item.optionalString("title")?.takeIf { it.isNotBlank() } ?: continue
                add(
                    PluginCacheEntry(
                        id = id,
                        title = title,
                        summary = item.optionalString("summary"),
                        sizeBytes = item.optLong("sizeBytes", -1L).takeIf { it >= 0L },
                        updatedAtEpochMs = item.optLong("updatedAtEpochMs", 0L).takeIf { it > 0L },
                        details = decodeDetails(item.optJSONArray("details"))
                    )
                )
            }
        }.let(::sanitizeEntries)
    }

    /** 详情数组容错解析：缺失/损坏回空列表，label/value 任一缺失即丢弃该条 */
    private fun decodeDetails(array: JSONArray?): List<PluginCacheDetail> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optionalString("label")?.takeIf { it.isNotBlank() } ?: continue
                val value = item.optionalString("value")?.takeIf { it.isNotBlank() } ?: continue
                add(PluginCacheDetail(label, value))
            }
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
}
