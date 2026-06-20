package com.example.connects.domain.repository

import com.example.connects.core.network.NetworkResult
import com.example.connects.domain.model.FoodItem

interface AiRepository {
    suspend fun recognizeFoodFromImage(base64Image: String): NetworkResult<FoodItem>
    suspend fun generateDietAdvice(userProfile: String, foodLog: String): NetworkResult<String>
}
