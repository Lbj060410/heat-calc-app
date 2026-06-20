package com.example.connects.data.repository

import com.example.connects.BuildConfig
import com.example.connects.core.network.RetrofitClient
import com.example.connects.data.model.ChatMessage
import com.example.connects.data.model.DeepSeekRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIRepository {
    
    private val apiService = RetrofitClient.deepSeekApi
    private val apiKey = BuildConfig.AI_API_KEY
    
    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = mutableListOf(
                ChatMessage(
                    role = "system",
                    content = "你是一个专业的营养和健康管理助手。你帮助用户记录饮食、分析营养成分、提供健康建议。请用简洁、友好的中文回答。"
                )
            )
            
            // 添加历史对话上下文
            messages.addAll(conversationHistory)
            
            // 添加当前用户消息
            messages.add(ChatMessage(role = "user", content = userMessage))
            
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
            
            val aiResponse = response.choices.firstOrNull()?.message?.content
            if (aiResponse != null) {
                Result.success(aiResponse)
            } else {
                Result.failure(Exception("AI 响应为空"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
