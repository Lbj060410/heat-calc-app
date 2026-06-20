package com.example.connects.domain.model

data class DailySummary(
    val date: Long,  // LocalDate.toEpochDay()
    val totalCalories: Float,
    val totalProtein: Float,
    val totalCarbs: Float,
    val totalFat: Float,
    val totalFiber: Float = 0f,
    val entries: List<FoodEntry> = emptyList(),
    val dailyTarget: Int = 2000,  // 默认目标热量
    val caloriesSurplus: Float = 0f  // 正数=超出，负数=未达
)
