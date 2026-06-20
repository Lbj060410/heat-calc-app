package com.example.connects.data.model

import com.google.gson.annotations.SerializedName

data class DeepSeekRequest(
    val model: String = "deepseek-chat",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2000,
    @SerializedName("stream")
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class DeepSeekResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)
