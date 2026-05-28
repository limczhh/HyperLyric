package com.lidesheng.hyperlyric.root.aitrans

import android.util.Log
import com.lidesheng.hyperlyric.root.utils.HookLogger
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.AiTranslationConfigs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI-compatible Chat Completions client for lyric translation. */
internal object OpenAiTranslationClient {
    private const val TAG = "HyperLyricAITranslator"

    suspend fun request(
        configs: AiTranslationConfigs,
        song: Song? = null,
        texts: List<String>
    ): List<TranslationItem>? = withContext(Dispatchers.IO) {
        if (configs.apiKey.isNullOrBlank()) {
            HookLogger.e("OpenAiTranslationClient", "AITranslation : API: 无法启动：未检测到 API Key，请在设置中配置")
            return@withContext null
        }

        val baseUrl = configs.baseUrl?.removeSuffix("/") ?: "https://api.openai.com/v1"
        val apiUrl =
            if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val requestItems = texts.mapIndexedNotNull { index, text ->
            text.trim().takeIf(::shouldRequestTranslation)?.let {
                TranslationRequestItem(index = index, text = it)
            }
        }
        if (requestItems.isEmpty()) {
            Log.d(TAG, "跳过请求：没有需要翻译的行")
            return@withContext emptyList()
        }

        val payload = TranslationRequest(lyrics = requestItems)
        val requestIndices = requestItems.map { it.index }.toSet()
        val chatRequest = OpenAiChatRequest(
            model = configs.model.orEmpty(),
            messages = listOf(
                ChatMessage("system", AITranslationPrompt.build(configs, song)),
                ChatMessage("user", payload.toJson())
            ),
            responseFormat = ResponseFormat("json_object"),
            temperature = configs.temperature,
            topP = configs.topP,
            maxTokens = configs.maxTokens.takeIf { it > 0 },
            presencePenalty = configs.presencePenalty,
            frequencyPenalty = configs.frequencyPenalty
        )

        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            HookLogger.d("OpenAiTranslationClient", "AITranslation : API 请求：使用模型 ${configs.model}，地址 $apiUrl")

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 60 * 1000
                readTimeout = 3 * (60 * 1000)
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${configs.apiKey}")
            }

            OutputStreamWriter(connection.outputStream).use {
                it.write(chatRequest.toJson())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val responseObj = json.decodeFromString<OpenAiChatResponse>(responseBody)
                val content = responseObj.choices.firstOrNull()?.message?.content ?: run {
                    HookLogger.e("OpenAiTranslationClient", "AITranslation : API 错误：AI 返回的内容为空")
                    return@withContext null
                }
                HookLogger.d("OpenAiTranslationClient", "AITranslation : API 成功：已接收到 AI 返回的数据")
                AITranslationResponseParser.parse(content, requestIndices)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "无错误信息"
                HookLogger.e("OpenAiTranslationClient", "AITranslation : API 错误 (代码 $responseCode)：请检查网络状态或 Key 余额")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: EOFException) {
            HookLogger.w("OpenAiTranslationClient", "AITranslation : API 异常：连接被意外关闭 (EOF)")
            null
        } catch (e: Exception) {
            HookLogger.e("OpenAiTranslationClient", "AITranslation : 系统异常：网络请求出错 (${e.javaClass.simpleName})", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun shouldRequestTranslation(text: String): Boolean {
        if (text.isBlank()) return false
        return text.any { it.isLetter() }
    }
}
