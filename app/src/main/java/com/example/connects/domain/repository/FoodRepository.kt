package com.example.connects.domain.repository

import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.FoodItem
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun getAllFoodItems(): Flow<List<FoodItem>>

    fun getFavoriteFoodItems(): Flow<List<FoodItem>>

    fun getRecentFoodItems(): Flow<List<FoodItem>>

    fun getCustomFoodItemsLatest(): Flow<List<FoodItem>>

    fun searchFoodItems(query: String): Flow<List<FoodItem>>

    suspend fun getFoodItemById(id: Long): FoodItem?

    suspend fun addCustomFoodItem(foodItem: FoodItem): Long

    suspend fun updateCustomFoodItem(foodItem: FoodItem)

    suspend fun setFoodFavorite(id: Long, isFavorite: Boolean)

    suspend fun markFoodUsed(id: Long, usedAt: Long = System.currentTimeMillis())

    suspend fun deleteCustomFoodItem(id: Long)

    fun getEntriesByDate(dateEpochDay: Int): Flow<List<FoodEntry>>

    fun getEntriesByDateRange(startEpochDay: Int, endEpochDay: Int): Flow<List<FoodEntry>>

    suspend fun addFoodEntry(entry: FoodEntry): Long

    suspend fun deleteFoodEntry(id: Long)

    suspend fun updateFoodEntry(entry: FoodEntry)

    fun getTotalCaloriesByDate(dateEpochDay: Int): Flow<Float>
}
