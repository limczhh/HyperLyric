package com.lidesheng.hyperlyric.plugin.ai.translation

import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import com.lidesheng.hyperlyric.plugin.api.PluginSong
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** Small OpenAI-compatible client with timeouts below the host Processor deadline. */
internal class OpenAiTranslationClient(
    private val logger: PluginLogger,
    parserLogger: PluginLogger,
) {
    private val parser = TranslationResponseParser(parserLogger)

    fun request(
        config: AiTranslationConfig,
        song: PluginSong,
        texts: List<String>,
    ): List<TranslationItem>? {
        if (Thread.currentThread().isInterrupted) return null
        if (config.apiKey.isBlank()) {
            logger.warn("跳过翻译请求: reason=missing_api_key")
            return null
        }

        val requestItems = texts.mapIndexedNotNull { index, text ->
            text.trim().takeIf(::shouldRequestTranslation)?.let {
                TranslationItem(index = index, trans = it)
            }
        }
        if (requestItems.isEmpty()) {
            logger.debug("跳过翻译请求: reason=no_translatable_lines")
            return emptyList()
        }

        val requestIndices = requestItems.map { it.index }.toSet()
        val lyrics = JSONArray().apply {
            requestItems.forEach { item ->
                put(JSONObject().put("index", item.index).put("text", item.trans))
            }
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", TranslationPrompt.build(config, song)))
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", JSONObject().put("lyrics", lyrics).toString())
            )
        val chatRequest = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", config.temperature.toDouble())
            .put("top_p", config.topP.toDouble())
            .apply {
                if (config.maxTokens > 0) put("max_tokens", config.maxTokens)
            }

        val baseUrl = config.baseUrl.trim().removeSuffix("/")
        val apiUrl = if (baseUrl.endsWith("/chat/completions")) {
            baseUrl
        } else {
            "$baseUrl/chat/completions"
        }

        var connection: HttpURLConnection? = null
        return try {
            logger.debug("发送翻译请求: model=${config.model}, url=$apiUrl")
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(chatRequest.toString())
            }

            if (Thread.currentThread().isInterrupted) return null
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                logger.error("翻译请求失败: code=$responseCode")
                return null
            }

            val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val content = JSONObject(responseBody)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    logger.warn("翻译响应为空")
                    return null
                }
            logger.debug("翻译请求完成: code=$responseCode")
            val parsed = parser.parse(content, requestIndices)
            parsed
        } catch (_: SocketTimeoutException) {
            logger.warn("翻译网络请求超时: timeoutMs=$READ_TIMEOUT_MS")
            null
        } catch (error: InterruptedIOException) {
            if (Thread.currentThread().isInterrupted ||
                error.message.equals("thread interrupted", ignoreCase = true)
            ) {
                logger.debug("翻译网络请求已取消: reason=interrupted")
            } else {
                logger.warn("翻译网络请求中断: type=${error.javaClass.simpleName}")
            }
            null
        } catch (_: EOFException) {
            logger.warn("翻译连接意外关闭: reason=EOF")
            null
        } catch (error: IOException) {
            logger.error(
                "翻译网络请求异常: type=${error.javaClass.simpleName}",
                error
            )
            null
        } catch (error: Exception) {
            logger.error(
                "翻译网络请求异常: type=${error.javaClass.simpleName}",
                error
            )
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun shouldRequestTranslation(text: String): Boolean =
        text.isNotBlank() && text.any { it.isLetter() }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        // Keep the network read just below the 35-second AI scheduler deadline.
        const val READ_TIMEOUT_MS = 34_000
    }
}
