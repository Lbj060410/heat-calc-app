package com.example.connects.data.mapper

import com.example.connects.domain.model.UserProfile
import javax.inject.Inject

class UserProfileMapper @Inject constructor() {
    fun toDomain(
        name: String,
        gender: String,
        height: Float,
        weight: Float,
        age: Int,
        goal: String,
        activityLevel: String,
        dailyCaloricTarget: Int
    ): UserProfile {
        return UserProfile(
            name = name,
            gender = gender,
            heightCm = height,
            weightKg = weight,
            age = age,
            goal = goal,
            activityLevel = activityLevel,
            dailyCaloricTarget = dailyCaloricTarget
        )
    }
}
