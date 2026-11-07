package com.lidesheng.hyperlyric.root.lyricenhancement.translation

import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.root.utils.HookLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Small Anthropic-compatible client with timeouts below the host Processor deadline. */
internal class AnthropicTranslationClient {
    private companion object {
        const val LOG_TAG = "LyricEnhancement/AiTranslation/AnthropicClient"
        const val CONNECT_TIMEOUT_MS = 8_000
        // Leave the total request deadline to the scheduler's 60-second wait.
        const val READ_TIMEOUT_MS = 65_000
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 4096
    }

    private val parser = TranslationResponseParser()

    fun request(
        config: AiTranslationConfig,
        song: Song,
        texts: List<String>,
    ): List<TranslationItem>? {
        if (Thread.currentThread().isInterrupted) return null
        if (config.apiKey.isBlank()) {
            HookLogger.w(LOG_TAG, "跳过翻译请求: reason=missing_api_key")
            return null
        }

        val requestItems = texts.mapIndexedNotNull { index, text ->
            text.trim().takeIf(::shouldRequestTranslation)?.let {
                TranslationItem(index = index, trans = it)
            }
        }
        if (requestItems.isEmpty()) {
            HookLogger.d(LOG_TAG, "跳过翻译请求: reason=no_translatable_lines")
            return emptyList()
        }

        val requestIndices = requestItems.map { it.index }.toSet()
        val lyrics = JSONArray().apply {
            requestItems.forEach { item ->
                put(JSONObject().put("index", item.index).put("text", item.trans))
            }
        }
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", JSONObject().put("lyrics", lyrics).toString())
        )
        val systemPrompt = TranslationPrompt.build(config, song)
        val maxTokens = if (config.maxTokens > 0) config.maxTokens else DEFAULT_MAX_TOKENS

        val requestBody = JSONObject()
            .put("model", config.model)
            .put("system", systemPrompt)
            .put("messages", messages)
            .put("max_tokens", maxTokens)
            .apply {
                // Anthropic API forbids specifying both temperature and top_p simultaneously.
                if (config.topP < 1.0f && config.temperature >= 1.0f) {
                    put("top_p", config.topP.coerceIn(0f, 1f).toDouble())
                } else {
                    put("temperature", config.temperature.coerceIn(0f, 1f).toDouble())
                }
            }

        val cleanBaseUrl = config.baseUrl.trim().removeSuffix("/")
        val normalizedUrl = if (cleanBaseUrl.endsWith("/chat/completions", ignoreCase = true)) {
            cleanBaseUrl.dropLast("/chat/completions".length).removeSuffix("/")
        } else {
            cleanBaseUrl
        }
        val apiUrl = when {
            normalizedUrl.endsWith("/messages", ignoreCase = true) -> normalizedUrl
            normalizedUrl.endsWith("/v1", ignoreCase = true) -> "$normalizedUrl/messages"
            else -> "$normalizedUrl/v1/messages"
        }

        var connection: HttpURLConnection? = null
        return try {
            HookLogger.d(LOG_TAG, "发送翻译请求: model=${config.model}, url=$apiUrl")
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", config.apiKey.trim())
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestBody.toString())
            }

            if (Thread.currentThread().isInterrupted) return null
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorDetails = runCatching {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                HookLogger.e(LOG_TAG, "翻译请求失败: code=$responseCode, details=$errorDetails")
                return null
            }

            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(responseBody)
            val content = extractContent(root) ?: run {
                HookLogger.w(LOG_TAG, "翻译响应为空")
                return null
            }
            HookLogger.d(LOG_TAG, "翻译请求完成: code=$responseCode")
            parser.parse(content, requestIndices)
        } catch (_: SocketTimeoutException) {
            HookLogger.w(LOG_TAG, "翻译网络请求超时: timeoutMs=$READ_TIMEOUT_MS")
            null
        } catch (error: InterruptedIOException) {
            if (Thread.currentThread().isInterrupted ||
                error.message.equals("thread interrupted", ignoreCase = true)
            ) {
                HookLogger.d(LOG_TAG, "翻译网络请求已取消: reason=interrupted")
            } else {
                HookLogger.w(LOG_TAG, "翻译网络请求中断: type=${error.javaClass.simpleName}")
            }
            null
        } catch (_: EOFException) {
            HookLogger.w(LOG_TAG, "翻译连接意外关闭: reason=EOF")
            null
        } catch (error: IOException) {
            HookLogger.e(LOG_TAG, "翻译网络请求异常: type=${error.javaClass.simpleName}", error)
            null
        } catch (error: Exception) {
            HookLogger.e(LOG_TAG, "翻译网络请求异常: type=${error.javaClass.simpleName}", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractContent(root: JSONObject): String? {
        val contentArray = root.optJSONArray("content")
        if (contentArray != null) {
            val sb = StringBuilder()
            for (i in 0 until contentArray.length()) {
                val block = contentArray.optJSONObject(i)
                if (block?.optString("type") == "text") {
                    sb.append(block.optString("text", ""))
                }
            }
            if (sb.isNotBlank()) return sb.toString()
        }
        return root.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content", "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun shouldRequestTranslation(text: String): Boolean =
        text.isNotBlank() && text.any { it.isLetter() }
}
