package com.example.connects.domain.model

data class UserWeightLog(
    val id: Long = 0,
    val weightKg: Float,
    val dateEpochDay: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val measurementContext: WeightMeasurementContext = WeightMeasurementContext.MORNING_FASTING
)
