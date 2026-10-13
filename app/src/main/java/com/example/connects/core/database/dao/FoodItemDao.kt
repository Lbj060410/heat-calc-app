package com.example.connects.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.connects.core.database.entity.FoodItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(foodItem: FoodItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foodItems: List<FoodItemEntity>)

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchByName(query: String): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE isCustom = :isCustom ORDER BY name ASC")
    fun getByType(isCustom: Boolean): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items ORDER BY lastUsedAt DESC, name ASC")
    fun getRecentFoods(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items ORDER BY name ASC")
    fun getAllFoodItems(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE isCustom = 1 ORDER BY createdAt DESC")
    fun getCustomFoodsLatest(): Flow<List<FoodItemEntity>>

    @Update
    suspend fun update(foodItem: FoodItemEntity)

    @Query("UPDATE food_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE food_items SET name = :name, category = :category, caloriesPer100g = :caloriesPer100g, proteinPer100g = :proteinPer100g, carbsPer100g = :carbsPer100g, fatPer100g = :fatPer100g, sodiumPer100g = :sodiumPer100g WHERE id = :id")
    suspend fun updateFoodFields(
        id: Long,
        name: String,
        category: String,
        caloriesPer100g: Float,
        proteinPer100g: Float,
        carbsPer100g: Float,
        fatPer100g: Float,
        sodiumPer100g: Float
    )

    @Query("UPDATE food_items SET lastUsedAt = :lastUsedAt WHERE id = :id")
    suspend fun updateLastUsedAt(id: Long, lastUsedAt: Long)

    @Delete
    suspend fun delete(foodItem: FoodItemEntity)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
