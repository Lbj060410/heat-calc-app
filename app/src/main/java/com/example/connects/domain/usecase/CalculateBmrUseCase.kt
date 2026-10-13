package com.example.connects.domain.usecase

import javax.inject.Inject
import kotlin.math.max

class CalculateBmrUseCase @Inject constructor() {

    /**
     * Mifflin-St Jeor 公式计算基础代谢率 (BMR)。
     *
     * BMR 公式:
     * 男性: BMR = (10 × 体重 + 6.25 × 身高 - 5 × 年龄 + 5)
     * 女性: BMR = (10 × 体重 + 6.25 × 身高 - 5 × 年龄 - 161)
     *
     * 这里只算 BMR，不乘活动系数——每日总消耗请再用 [CalculateTdeeUseCase]。
     * 之前这个函数顺手把活动系数也乘了进去，名字却叫 BMR，调用方再乘一次活动系数
     * 就会把系数用两遍。
     */
    operator fun invoke(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        isMale: Boolean
    ): Float {
        val bmr = (10f * weightKg) + (6.25f * heightCm) - (5f * ageYears.toFloat()) +
            if (isMale) 5f else -161f
        return max(bmr, 800f)
    }
}
