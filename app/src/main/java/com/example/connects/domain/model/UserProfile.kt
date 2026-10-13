package com.example.connects.domain.model

data class UserProfile(
    val name: String,
    val gender: String,                    // "male" / "female"
    val heightCm: Float,
    val weightKg: Float,
    val age: Int,
    val goal: String,                      // "lose" / "gain" / "maintain"
    val activityLevel: String,             // ActivityLevel.storageKey，如 "sedentary" / "lightly_active"
    val dailyCaloricTarget: Int
)
