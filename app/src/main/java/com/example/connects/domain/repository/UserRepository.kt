package com.example.connects.domain.repository

import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.model.WeightLogEntry
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(): Flow<UserProfile>
    suspend fun updateUserProfile(userProfile: UserProfile)
    fun getDailyCalorieTarget(): Flow<Int>
    fun getWeightHistory(): Flow<List<WeightLogEntry>>
    suspend fun recordWeight(weightKg: Float)
    suspend fun calculateAndSaveDailyTarget(
        height: Float,
        weight: Float,
        age: Int,
        gender: String,
        goal: String,
        activityLevel: String
    )
}
