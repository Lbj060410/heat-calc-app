package com.example.connects.data.repository

import com.example.connects.core.database.dao.FoodEntryDao
import com.example.connects.core.database.dao.FoodItemDao
import com.example.connects.data.mapper.FoodEntryMapper
import com.example.connects.data.mapper.FoodItemMapper
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.FoodItem
import com.example.connects.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class FoodRepositoryImpl @Inject constructor(
    private val foodItemDao: FoodItemDao,
    private val foodEntryDao: FoodEntryDao,
    private val foodItemMapper: FoodItemMapper,
    private val foodEntryMapper: FoodEntryMapper
) : FoodRepository {

    override fun getAllFoodItems(): Flow<List<FoodItem>> {
        return foodItemDao.getAllFoodItems().map { entities ->
            foodItemMapper.toDomainList(entities)
        }
    }

    override fun getFavoriteFoodItems(): Flow<List<FoodItem>> {
        return foodItemDao.getFavorites().map { entities ->
            foodItemMapper.toDomainList(entities)
        }
    }

    override fun getRecentFoodItems(): Flow<List<FoodItem>> {
        return foodItemDao.getRecentFoods().map { entities ->
            foodItemMapper.toDomainList(entities)
        }
    }

    override fun getCustomFoodItemsLatest(): Flow<List<FoodItem>> {
        return foodItemDao.getCustomFoodsLatest().map { entities ->
            foodItemMapper.toDomainList(entities)
        }
    }

    override fun searchFoodItems(query: String): Flow<List<FoodItem>> {
        return foodItemDao.searchByName(query).map { entities ->
            foodItemMapper.toDomainList(entities)
        }
    }

    override suspend fun getFoodItemById(id: Long): FoodItem? {
        return foodItemDao.getById(id)?.let { foodItemMapper.toDomain(it) }
    }

    override suspend fun addCustomFoodItem(foodItem: FoodItem): Long {
        return foodItemDao.insert(foodItemMapper.toEntity(foodItem))
    }

    override suspend fun updateCustomFoodItem(foodItem: FoodItem) {
        foodItemDao.updateFoodFields(
            id = foodItem.id,
            name = foodItem.name,
            category = foodItem.category,
            caloriesPer100g = foodItem.caloriesPer100g,
            proteinPer100g = foodItem.proteinPer100g,
            carbsPer100g = foodItem.carbsPer100g,
            fatPer100g = foodItem.fatPer100g,
            fiberPer100g = foodItem.fiberPer100g,
            sodiumPer100g = foodItem.sodiumPer100g
        )
    }

    override suspend fun setFoodFavorite(id: Long, isFavorite: Boolean) {
        foodItemDao.updateFavorite(id, isFavorite)
    }

    override suspend fun markFoodUsed(id: Long, usedAt: Long) {
        foodItemDao.updateLastUsedAt(id, usedAt)
    }

    override suspend fun deleteCustomFoodItem(id: Long) {
        foodItemDao.deleteById(id)
    }

    override fun getEntriesByDate(dateEpochDay: Int): Flow<List<FoodEntry>> {
        return foodEntryDao.getEntriesByDate(dateEpochDay).map { entities ->
            foodEntryMapper.toDomainList(entities)
        }
    }

    override fun getEntriesByDateRange(startEpochDay: Int, endEpochDay: Int): Flow<List<FoodEntry>> {
        return foodEntryDao.getEntriesByDateRange(startEpochDay, endEpochDay).map { entities ->
            foodEntryMapper.toDomainList(entities)
        }
    }

    override suspend fun addFoodEntry(entry: FoodEntry): Long {
        return foodEntryDao.insert(foodEntryMapper.toEntity(entry))
    }

    override suspend fun deleteFoodEntry(id: Long) {
        foodEntryDao.deleteById(id)
    }

    override suspend fun updateFoodEntry(entry: FoodEntry) {
        foodEntryDao.update(foodEntryMapper.toEntity(entry))
    }

    override fun getTotalCaloriesByDate(dateEpochDay: Int): Flow<Float> {
        return foodEntryDao.getTotalCaloriesByDate(dateEpochDay)
    }
}
