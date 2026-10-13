package com.example.connects.domain.model

data class UserWaistLog(
    val id: Long = 0,
    val waistCm: Float,
    val dateEpochDay: Int,
    val createdAt: Long = System.currentTimeMillis()
)