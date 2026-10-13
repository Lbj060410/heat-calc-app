package com.example.connects.domain.model

/**
 * 领域层食物模型（不依赖 Room Entity）
 */
data class FoodItem(
    val id: Long = 0,
    val name: String,
    val category: String = FoodCategory.OTHER,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val carbsPer100g: Float,
    val fatPer100g: Float,
    val sodiumPer100g: Float = 0f,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

object FoodCategory {
    const val ALL = "all"
    const val CUSTOM = "custom"
    const val STAPLE = "staple"
    const val VEGETABLE = "vegetable"
    const val MEAT_DAIRY = "meat_dairy"
    const val FRUIT = "fruit"
    const val SNACK = "snack"
    const val OTHER = "other"
}
