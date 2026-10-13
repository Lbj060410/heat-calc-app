package com.example.connects.domain.usecase

import com.example.connects.domain.model.ActivityLevel
import javax.inject.Inject

/**
 * 由身体数据 + 目标算出每日热量目标。
 *
 * 引导页和「我的」页保存资料都走这里，避免两处各写一套、算出来的数还不一样。
 * 减脂扣 15%，增肌加 15%，维持就是 TDEE。
 */
class CalculateDailyCalorieTargetUseCase @Inject constructor(
    private val calculateBmrUseCase: CalculateBmrUseCase,
    private val calculateTdeeUseCase: CalculateTdeeUseCase
) {
    operator fun invoke(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        gender: String,
        goal: String,
        activityLevel: ActivityLevel
    ): Int {
        val bmr = calculateBmrUseCase(weightKg, heightCm, ageYears, gender == "male")
        val tdee = calculateTdeeUseCase(bmr, activityLevel)
        return when (goal) {
            "lose" -> (tdee * 0.85f).toInt()
            "gain" -> (tdee * 1.15f).toInt()
            else -> tdee.toInt()
        }
    }

    /** 活动水平是从存储里读出来的字符串时用这个重载。 */
    operator fun invoke(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        gender: String,
        goal: String,
        activityLevel: String?
    ): Int = invoke(
        weightKg = weightKg,
        heightCm = heightCm,
        ageYears = ageYears,
        gender = gender,
        goal = goal,
        activityLevel = ActivityLevel.fromStorage(activityLevel)
    )
}
