package com.example.connects.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_weight_logs")
data class UserWeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightKg: Float,
    val dateEpochDay: Int,
    val createdAt: Long = System.currentTimeMillis()
)
