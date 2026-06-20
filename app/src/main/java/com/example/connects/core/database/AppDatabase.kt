package com.example.connects.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.connects.core.database.dao.FoodEntryDao
import com.example.connects.core.database.dao.FoodItemDao
import com.example.connects.core.database.dao.UserWeightLogDao
import com.example.connects.core.database.entity.FoodEntryEntity
import com.example.connects.core.database.entity.FoodItemEntity
import com.example.connects.core.database.entity.UserWeightLogEntity

@Database(
    entities = [FoodItemEntity::class, FoodEntryEntity::class, UserWeightLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun userWeightLogDao(): UserWeightLogDao
}
