package com.example.connects.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.connects.core.database.AppDatabase
import com.example.connects.core.database.dao.FoodEntryDao
import com.example.connects.core.database.dao.FoodItemDao
import com.example.connects.core.database.dao.UserWeightLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE food_items ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
            db.execSQL("ALTER TABLE food_items ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE food_items ADD COLUMN lastUsedAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "calorie_app.db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Singleton
    @Provides
    fun provideFoodItemDao(database: AppDatabase): FoodItemDao {
        return database.foodItemDao()
    }

    @Singleton
    @Provides
    fun provideFoodEntryDao(database: AppDatabase): FoodEntryDao {
        return database.foodEntryDao()
    }

    @Singleton
    @Provides
    fun provideUserWeightLogDao(database: AppDatabase): UserWeightLogDao {
        return database.userWeightLogDao()
    }
}
