package com.example.connects.core.network

import com.example.connects.core.network.dto.AiRequestDto
import com.example.connects.core.network.dto.AiResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AiApiService {
    @POST("v1/messages")
    suspend fun analyzeImage(
        @Header("Authorization") authHeader: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Body request: AiRequestDto
    ): AiResponseDto
}
