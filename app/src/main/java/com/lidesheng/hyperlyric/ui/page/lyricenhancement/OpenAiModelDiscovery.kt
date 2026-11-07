package com.lidesheng.hyperlyric.ui.page.lyricenhancement

import com.lidesheng.hyperlyric.common.RootConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class ModelListHttpException(val statusCode: Int) : IOException()

internal object OpenAiModelDiscovery {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetch(
        provider: String = RootConstants.AI_TRANS_PROVIDER_OPENAI,
        baseUrl: String,
        apiKey: String
    ): List<String> = withContext(Dispatchers.IO) {
        val isAnthropic = provider.equals(RootConstants.AI_TRANS_PROVIDER_ANTHROPIC, ignoreCase = true)
        val candidateUrls = if (isAnthropic) {
            getAnthropicCandidateUrls(baseUrl)
        } else {
            getOpenAiCandidateUrls(baseUrl)
        }

        var lastException: Exception? = null
        for (url in candidateUrls) {
            try {
                val requestBuilder = Request.Builder().url(url).get()
                if (isAnthropic) {
                    requestBuilder.header("x-api-key", apiKey.trim())
                    requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                    requestBuilder.header("api-key", apiKey.trim())
                    requestBuilder.header("anthropic-version", "2023-06-01")
                } else {
                    requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
                    requestBuilder.header("api-key", apiKey.trim())
                }
                val request = requestBuilder.build()

                val result = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ModelListHttpException(response.code)
                    }

                    val body = response.body?.string().orEmpty()
                    val data = JSONObject(body).optJSONArray("data")
                    if (data == null) {
                        emptyList()
                    } else {
                        (0 until data.length())
                            .mapNotNull { index -> data.optJSONObject(index)?.opt("id") as? String }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                    }
                }
                return@withContext result
            } catch (error: Exception) {
                lastException = error
            }
        }
        throw lastException ?: IOException("Failed to fetch models")
    }

    private fun getAnthropicCandidateUrls(baseUrl: String): List<String> {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        require(cleanBaseUrl.isNotBlank()) { "Base URL is blank" }
        val normalizedUrl = if (cleanBaseUrl.endsWith("/chat/completions", ignoreCase = true)) {
            cleanBaseUrl.dropLast("/chat/completions".length).trimEnd('/')
        } else {
            cleanBaseUrl
        }

        val candidates = mutableListOf<String>()

        // 1. Primary Anthropic standard (/v1/models or /models)
        when {
            normalizedUrl.endsWith("/models", ignoreCase = true) -> candidates.add(normalizedUrl)
            normalizedUrl.endsWith("/v1", ignoreCase = true) -> candidates.add("$normalizedUrl/models")
            normalizedUrl.endsWith("/messages", ignoreCase = true) ->
                candidates.add("${normalizedUrl.dropLast("/messages".length).trimEnd('/')}/models")
            else -> candidates.add("$normalizedUrl/v1/models")
        }

        // 2. Compatibility fallback: platforms like Xiaomi MiMo (https://api.xiaomimimo.com/anthropic)
        // host messages at /anthropic/v1/messages, but host models at root /v1/models
        if (normalizedUrl.contains("/anthropic", ignoreCase = true)) {
            val rootWithoutAnthropic = normalizedUrl
                .replace(Regex("/anthropic(/v1)?", RegexOption.IGNORE_CASE), "")
                .trimEnd('/')
            if (rootWithoutAnthropic.isNotBlank()) {
                candidates.add("$rootWithoutAnthropic/v1/models")
                candidates.add("$rootWithoutAnthropic/models")
            }
        }

        // 3. Fallback without /v1
        if (!normalizedUrl.endsWith("/v1", ignoreCase = true) && !normalizedUrl.endsWith("/models", ignoreCase = true)) {
            candidates.add("$normalizedUrl/models")
        }

        return candidates.distinct()
    }

    private fun getOpenAiCandidateUrls(baseUrl: String): List<String> {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        require(cleanBaseUrl.isNotBlank()) { "Base URL is blank" }
        val normalizedUrl = if (cleanBaseUrl.endsWith("/chat/completions", ignoreCase = true)) {
            cleanBaseUrl.dropLast("/chat/completions".length).trimEnd('/')
        } else {
            cleanBaseUrl
        }

        val candidates = mutableListOf<String>()
        when {
            normalizedUrl.endsWith("/models", ignoreCase = true) -> candidates.add(normalizedUrl)
            normalizedUrl.endsWith("/v1", ignoreCase = true) -> candidates.add("$normalizedUrl/models")
            else -> {
                candidates.add("$normalizedUrl/models")
                candidates.add("$normalizedUrl/v1/models")
            }
        }
        return candidates.distinct()
    }
}
