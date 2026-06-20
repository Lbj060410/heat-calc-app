package com.example.connects.core.network

import com.example.connects.data.model.DeepSeekRequest
import com.example.connects.data.model.DeepSeekResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface DeepSeekApiService {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: DeepSeekRequest
    ): DeepSeekResponse
}
