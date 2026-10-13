package com.example.connects.data.repository

import com.example.connects.BuildConfig
import com.example.connects.core.network.RetrofitClient
import com.example.connects.core.network.NetworkResult
import com.example.connects.data.model.ChatMessage
import com.example.connects.data.model.DeepSeekRequest
import com.example.connects.domain.repository.AiRepository
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor() : AiRepository {

    private val apiService = RetrofitClient.deepSeekApi
    private val apiKey = BuildConfig.AI_API_KEY

    override suspend fun generateDietAdvice(userProfile: String, foodLog: String): NetworkResult<String> {
        return try {
            val messages = listOf(
                ChatMessage(
                    role = "system",
                    content = "你是一个专业的营养和健康管理助手。你帮助用户记录饮食、分析营养成分、提供健康建议。请用简洁、友好的中文回答。"
                ),
                ChatMessage(
                    role = "user",
                    content = "基于用户档案：$userProfile 和饮食记录：$foodLog，请给出饮食建议。"
                )
            )
            
            val request = DeepSeekRequest(
                model = "deepseek-chat",
                messages = messages,
                temperature = 0.7,
                maxTokens = 2000
            )

            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            val advice = response.choices.firstOrNull()?.message?.content ?: "无法生成建议"
            NetworkResult.Success(advice)
        } catch (e: Exception) {
            NetworkResult.Error(e)
        }
    }
}
