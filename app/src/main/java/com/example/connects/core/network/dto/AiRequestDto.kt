package com.example.connects.core.network.dto

data class AiRequestDto(
    val model: String,
    val messages: List<MessageDto>,
    val max_tokens: Int = 1024
)

data class MessageDto(
    val role: String,  // "user" / "assistant"
    val content: String
)
