package com.example.connects.domain.usecase

import com.example.connects.core.network.NetworkResult
import com.example.connects.domain.repository.AiRepository
import javax.inject.Inject

class GetAiDietAdviceUseCase @Inject constructor(
    private val aiRepository: AiRepository
) {
    suspend operator fun invoke(userProfile: String, foodLog: String): NetworkResult<String> {
        return aiRepository.generateDietAdvice(userProfile, foodLog)
    }
}
