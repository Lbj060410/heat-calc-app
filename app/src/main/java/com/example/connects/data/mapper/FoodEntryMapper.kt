package com.example.connects.data.mapper

import com.example.connects.core.database.entity.FoodEntryEntity
import com.example.connects.domain.model.FoodEntry
import javax.inject.Inject

class FoodEntryMapper @Inject constructor() {
    fun toDomain(entity: FoodEntryEntity): FoodEntry {
        return FoodEntry(
            id = entity.id,
            foodItemId = entity.foodItemId,
            foodName = entity.foodName,
            amountGrams = entity.amountGrams,
            totalCalories = entity.totalCalories,
            totalProtein = entity.totalProtein,
            totalCarbs = entity.totalCarbs,
            totalFat = entity.totalFat,
            sodium = entity.sodium,
            mealType = entity.mealType,
            dateEpochDay = entity.dateEpochDay,
            createdAt = entity.createdAt,
            note = entity.note
        )
    }

    fun toEntity(domain: FoodEntry): FoodEntryEntity {
        return FoodEntryEntity(
            id = domain.id,
            foodItemId = domain.foodItemId,
            foodName = domain.foodName,
            amountGrams = domain.amountGrams,
            totalCalories = domain.totalCalories,
            totalProtein = domain.totalProtein,
            totalCarbs = domain.totalCarbs,
            totalFat = domain.totalFat,
            sodium = domain.sodium,
            mealType = domain.mealType,
            dateEpochDay = domain.dateEpochDay,
            createdAt = domain.createdAt,
            note = domain.note
        )
    }

    fun toDomainList(entities: List<FoodEntryEntity>): List<FoodEntry> {
        return entities.map { toDomain(it) }
    }
}
