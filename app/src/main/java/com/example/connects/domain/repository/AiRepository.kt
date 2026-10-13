package com.example.connects.domain.repository

import com.example.connects.core.network.NetworkResult

interface AiRepository {
    suspend fun generateDietAdvice(userProfile: String, foodLog: String): NetworkResult<String>
}
