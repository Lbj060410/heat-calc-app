package com.example.connects.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_items")
data class FoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "other",
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val fiberPer100g: Float = 0f,
    val sodiumPer100g: Float = 0f,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
