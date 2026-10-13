package com.example.connects.domain.model

data class FoodEntry(
    val id: Long = 0,
    val foodItemId: Long?,
    val foodName: String,
    val amountGrams: Float,
    val totalCalories: Float,
    val totalProtein: Float = 0f,
    val totalCarbs: Float = 0f,
    val totalFat: Float = 0f,
    val sodium: Float = 0f,
    val mealType: String,
    val dateEpochDay: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
)
