package com.example.mymind.network

import com.google.gson.annotations.SerializedName

/** OpenAI 兼容格式的请求体（DeepSeek / GPT-4 均支持） */
data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    @SerializedName("temperature") val temperature: Double = 0.7
)

data class ChatMessage(
    @SerializedName("role") val role: String,  // "system" | "user" | "assistant"
    @SerializedName("content") val content: String
)

data class ChatResponse(
    @SerializedName("id") val id: String?,
    @SerializedName("choices") val choices: List<Choice>?,
    @SerializedName("error") val error: ApiError?
)

data class Choice(
    @SerializedName("message") val message: ChatMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class ApiError(
    @SerializedName("message") val message: String?,
    @SerializedName("type") val type: String?
)
