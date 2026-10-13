package com.example.connects.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_waist_logs")
data class UserWaistLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val waistCm: Float,
    val dateEpochDay: Int,
    val createdAt: Long = System.currentTimeMillis()
)