package com.example.connects.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_entries",
    foreignKeys = [ForeignKey(
        entity = FoodItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("dateEpochDay"), Index("foodItemId")]
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodItemId: Long? = null,
    val foodName: String,
    val amountGrams: Float,
    val totalCalories: Float,
    val totalProtein: Float = 0f,
    val totalCarbs: Float = 0f,
    val totalFat: Float = 0f,
    val totalFiber: Float = 0f,
    val sodium: Float = 0f,
    val mealType: String,
    val dateEpochDay: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    companion object {
        const val MEAL_TYPE_BREAKFAST = "早餐"
        const val MEAL_TYPE_LUNCH = "午餐"
        const val MEAL_TYPE_DINNER = "晚餐"
        const val MEAL_TYPE_SNACK = "加餐"
    }
}
