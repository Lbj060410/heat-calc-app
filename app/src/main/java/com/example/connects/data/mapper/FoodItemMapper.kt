package com.example.connects.data.mapper

import com.example.connects.core.database.entity.FoodItemEntity
import com.example.connects.domain.model.FoodCategory
import com.example.connects.domain.model.FoodItem
import javax.inject.Inject

class FoodItemMapper @Inject constructor() {
    fun toDomain(entity: FoodItemEntity): FoodItem {
        val category = when {
            entity.isCustom -> FoodCategory.CUSTOM
            entity.name.contains("米饭") || entity.name.contains("馒头") ||
                entity.name.contains("面条") || entity.name.contains("燕麦") ->
                FoodCategory.STAPLE
            entity.name.contains("西兰花") || entity.name.contains("菠菜") ||
                entity.name.contains("番茄") ->
                FoodCategory.VEGETABLE
            entity.name.contains("鸡胸肉") || entity.name.contains("鸡蛋") ||
                entity.name.contains("牛奶") || entity.name.contains("酸奶") ->
                FoodCategory.MEAT_DAIRY
            entity.name.contains("苹果") || entity.name.contains("香蕉") ||
                entity.name.contains("橙子") ->
                FoodCategory.FRUIT
            entity.name.contains("饼干") || entity.name.contains("薯片") ||
                entity.name.contains("巧克力") ->
                FoodCategory.SNACK
            else -> FoodCategory.OTHER
        }

        return FoodItem(
            id = entity.id,
            name = entity.name,
            category = category,
            caloriesPer100g = entity.caloriesPer100g,
            proteinPer100g = entity.proteinPer100g,
            carbsPer100g = entity.carbsPer100g,
            fatPer100g = entity.fatPer100g,
            fiberPer100g = entity.fiberPer100g,
            sodiumPer100g = entity.sodiumPer100g,
            isCustom = entity.isCustom,
            isFavorite = entity.isFavorite,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: FoodItem): FoodItemEntity {
        return FoodItemEntity(
            id = domain.id,
            name = domain.name,
            caloriesPer100g = domain.caloriesPer100g,
            proteinPer100g = domain.proteinPer100g,
            carbsPer100g = domain.carbsPer100g,
            fatPer100g = domain.fatPer100g,
            fiberPer100g = domain.fiberPer100g,
            sodiumPer100g = domain.sodiumPer100g,
            isCustom = domain.isCustom,
            isFavorite = domain.isFavorite,
            lastUsedAt = domain.lastUsedAt,
            createdAt = domain.createdAt
        )
    }

    fun toDomainList(entities: List<FoodItemEntity>): List<FoodItem> {
        return entities.map { toDomain(it) }
    }
}
