package com.example.connects.domain.usecase

import com.example.connects.domain.model.ActivityLevel
import javax.inject.Inject
import kotlin.math.max

class CalculateBmrUseCase @Inject constructor() {

    /**
     * Mifflin-St Jeor 公式计算基础代谢率 (BMR) 并应用活动系数得出 TDEE
     *
     * BMR 公式:
     * 男性: BMR = (10 × 体重 + 6.25 × 身高 - 5 × 年龄 + 5)
     * 女性: BMR = (10 × 体重 + 6.25 × 身高 - 5 × 年龄 - 161)
     *
     * TDEE (每日总消耗) = BMR × 活动系数
     */
    operator fun invoke(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        isMale: Boolean,
        activityLevel: ActivityLevel = ActivityLevel.SEDENTARY
    ): Float {
        val bmr = (10f * weightKg) + (6.25f * heightCm) - (5f * ageYears.toFloat()) + if (isMale) 5f else -161f
        val safeBmr = max(bmr, 800f)
        
        return safeBmr * activityLevel.multiplier
    }
}
