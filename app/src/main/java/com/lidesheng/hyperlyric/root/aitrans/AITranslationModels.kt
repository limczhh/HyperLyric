package com.lidesheng.hyperlyric.root.aitrans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TranslationRequestItem(val index: Int, val text: String)

@Serializable
internal data class TranslationRequest(val lyrics: List<TranslationRequestItem>)

@Serializable
data class TranslationItem(val index: Int, val trans: String)

@Serializable
internal data class TranslationResponse(val translations: List<TranslationItem>)

@Serializable
internal data class OpenAiChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    @SerialName("top_p") val topP: Float? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("presence_penalty") val presencePenalty: Float? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Float? = null
)

@Serializable
internal data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class ResponseFormat(
    val type: String
)

@Serializable
internal data class OpenAiChatResponse(
    val choices: List<Choice>
)

@Serializable
internal data class Choice(
    val message: ChatMessage
)
