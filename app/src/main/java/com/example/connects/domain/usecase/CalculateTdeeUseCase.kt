package com.example.connects.domain.usecase

import com.example.connects.domain.model.ActivityLevel
import javax.inject.Inject

class CalculateTdeeUseCase @Inject constructor() {
    operator fun invoke(bmr: Float, activityLevel: ActivityLevel): Float {
        return bmr * activityLevel.multiplier
    }

    // 保留兼容旧代码的 String 版本（如果需要）
    operator fun invoke(bmr: Float, activityLevelStr: String): Float {
        val level = try {
            ActivityLevel.valueOf(activityLevelStr.uppercase())
        } catch (e: Exception) {
            ActivityLevel.SEDENTARY
        }
        return bmr * level.multiplier
    }
}
