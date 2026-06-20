package com.example.connects.data.repository

import com.example.connects.core.datastore.UserPreferences
import com.example.connects.data.mapper.UserProfileMapper
import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.repository.UserRepository
import com.example.connects.domain.usecase.CalculateBmrUseCase
import com.example.connects.domain.usecase.CalculateTdeeUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userPreferences: UserPreferences,
    private val userProfileMapper: UserProfileMapper,
    private val calculateBmrUseCase: CalculateBmrUseCase,
    private val calculateTdeeUseCase: CalculateTdeeUseCase
) : UserRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        return combine(
            userPreferences.userName,
            userPreferences.userGender,
            userPreferences.userHeight,
            userPreferences.userWeight,
            userPreferences.userAge,
            userPreferences.userGoal,
            userPreferences.activityLevel,
            userPreferences.dailyCaloricTarget
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val arr = values as Array<Any>
            userProfileMapper.toDomain(
                name = arr[0] as String,
                gender = arr[1] as String,
                height = arr[2] as Float,
                weight = arr[3] as Float,
                age = arr[4] as Int,
                goal = arr[5] as String,
                activityLevel = arr[6] as String,
                dailyCaloricTarget = arr[7] as Int
            )
        }
    }

    override suspend fun updateUserProfile(userProfile: UserProfile) {
        userPreferences.saveUserProfile(
            name = userProfile.name,
            gender = userProfile.gender,
            height = userProfile.heightCm,
            weight = userProfile.weightKg,
            age = userProfile.age,
            goal = userProfile.goal,
            activityLevel = userProfile.activityLevel,
            dailyCaloricTarget = userProfile.dailyCaloricTarget
        )
    }

    override fun getDailyCalorieTarget(): Flow<Int> {
        return userPreferences.dailyCaloricTarget
    }

    override suspend fun calculateAndSaveDailyTarget(
        height: Float,
        weight: Float,
        age: Int,
        gender: String,
        goal: String,
        activityLevel: String
    ) {
        val isMale = gender == "male"
        val bmr = calculateBmrUseCase(weight, height, age, isMale)
        val tdee = calculateTdeeUseCase(bmr, activityLevel)

        // 根据目标调整热量
        val adjustedCalories = when (goal) {
            "lose" -> (tdee * 0.85).toInt()      // 热量缺口 15%
            "gain" -> (tdee * 1.15).toInt()      // 热量盈余 15%
            else -> tdee.toInt()                 // 维持
        }

        userPreferences.updateDailyCaloricTarget(adjustedCalories)
    }
}
