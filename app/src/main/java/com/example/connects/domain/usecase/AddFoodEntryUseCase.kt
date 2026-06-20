package com.example.connects.domain.usecase

import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.repository.FoodRepository
import javax.inject.Inject

class AddFoodEntryUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {
    suspend operator fun invoke(entry: FoodEntry): Long {
        return foodRepository.addFoodEntry(entry)
    }
}
