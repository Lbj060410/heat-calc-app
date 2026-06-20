package com.example.connects.domain.usecase

import com.example.connects.domain.model.DailySummary
import com.example.connects.domain.repository.FoodRepository
import com.example.connects.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetDailySummaryUseCase @Inject constructor(
    private val foodRepository: FoodRepository,
    private val userRepository: UserRepository
) {
    operator fun invoke(dateEpochDay: Int): Flow<DailySummary> {
        return combine(
            foodRepository.getEntriesByDate(dateEpochDay),
            userRepository.getDailyCalorieTarget()
        ) { entries, target ->
            val totalCalories = entries.sumOf { (it.totalCalories.toInt()) }.toFloat()
            val totalProtein = entries.sumOf { ((it.amountGrams / 100f) * 0f).toInt() }.toFloat()  // 需要从 FoodItem 获取
            val totalCarbs = entries.sumOf { ((it.amountGrams / 100f) * 0f).toInt() }.toFloat()
            val totalFat = entries.sumOf { ((it.amountGrams / 100f) * 0f).toInt() }.toFloat()

            DailySummary(
                date = dateEpochDay.toLong(),
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalCarbs = totalCarbs,
                totalFat = totalFat,
                entries = entries,
                dailyTarget = target,
                caloriesSurplus = totalCalories - target
            )
        }
    }
}
