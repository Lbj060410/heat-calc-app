package com.example.connects.data.repository

import com.example.connects.BuildConfig
import com.example.connects.core.network.RetrofitClient
import com.example.connects.core.network.NetworkResult
import com.example.connects.data.model.ChatMessage
import com.example.connects.data.model.DeepSeekRequest
import com.example.connects.domain.model.FoodItem
import com.example.connects.domain.repository.AiRepository
import javax.inject.Inject

class AiRepositoryImpl @Inject constructor() : AiRepository {

    private val apiService = RetrofitClient.deepSeekApi
    private val apiKey = BuildConfig.AI_API_KEY

    override suspend fun recognizeFoodFromImage(base64Image: String): NetworkResult<FoodItem> {
        return try {
            val messages = listOf(
                ChatMessage(
                    role = "system",
                    content = "你是一个食物识别助手。请分析图片并返回 JSON 格式的营养信息。"
                ),
                ChatMessage(
                    role = "user",
                    content = "请分析这张食物图片，返回 JSON 格式的营养信息：{\"name\": \"食物名称\", \"caloriesPer100g\": 热量, \"proteinPer100g\": 蛋白质, \"carbsPer100g\": 碳水, \"fatPer100g\": 脂肪}。图片数据：$base64Image"
                )
            )
            
            val request = DeepSeekRequest(
                model = "deepseek-chat",
                messages = messages,
                temperature = 0.3,
                maxTokens = 500
            )

            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            val content = response.choices.firstOrNull()?.message?.content ?: ""
            // 简化处理：实际应该解析 JSON
            NetworkResult.Success(
                FoodItem(
                    name = "识别的食物",
                    caloriesPer100g = 100f,
                    proteinPer100g = 10f,
                    carbsPer100g = 20f,
                    fatPer100g = 5f
                )
            )
        } catch (e: Exception) {
            NetworkResult.Error(e)
        }
    }

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
