package com.example.connects.core.network.dto

data class AiResponseDto(
    val id: String,
    val choices: List<ChoiceDto>,
    val usage: UsageDto
)

data class ChoiceDto(
    val message: MessageDto,
    val finish_reason: String
)

data class UsageDto(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
