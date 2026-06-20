package com.example.connects.domain.usecase

import com.example.connects.core.network.NetworkResult
import com.example.connects.domain.model.FoodItem
import com.example.connects.domain.repository.AiRepository
import javax.inject.Inject

class RecognizeFoodFromImageUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(base64Image: String): NetworkResult<FoodItem> {
        return aiRepository.recognizeFoodFromImage(base64Image)
    }
}
