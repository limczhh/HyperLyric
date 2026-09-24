package com.lidesheng.hyperlyric.ui.page.lyricenhancement

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

    suspend fun fetch(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(buildModelsUrl(baseUrl))
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
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
        }

    private fun buildModelsUrl(baseUrl: String): String {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        require(normalizedUrl.isNotBlank()) { "Base URL is blank" }

        return when {
            normalizedUrl.endsWith("/models", ignoreCase = true) -> normalizedUrl
            normalizedUrl.endsWith("/chat/completions", ignoreCase = true) ->
                "${normalizedUrl.dropLast("/chat/completions".length)}/models"

            else -> "$normalizedUrl/models"
        }
    }
}
