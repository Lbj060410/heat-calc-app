package com.example.connects.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.connects.core.database.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FoodEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FoodEntryEntity>)

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Delete
    suspend fun delete(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entries WHERE dateEpochDay = :dateEpochDay ORDER BY createdAt DESC")
    fun getEntriesByDate(dateEpochDay: Int): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE dateEpochDay BETWEEN :startDay AND :endDay ORDER BY dateEpochDay DESC, createdAt DESC")
    fun getEntriesByDateRange(startDay: Int, endDay: Int): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    fun getEntryById(id: Long): Flow<FoodEntryEntity?>

    @Query("SELECT COALESCE(SUM(totalCalories), 0) FROM food_entries WHERE dateEpochDay = :dateEpochDay")
    fun getTotalCaloriesByDate(dateEpochDay: Int): Flow<Float>

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM food_entries WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteByDate(dateEpochDay: Int)
}
