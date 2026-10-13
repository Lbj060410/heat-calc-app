package com.example.connects.domain.usecase

import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 由「日均摄入 − 体重变化」反推真实每日消耗（自适应 TDEE）。
 *
 * 原理：一个周期内累积的热量盈亏会体现在体重上。
 *   estimatedTdee = 日均摄入 − (体重变化kg × 7700 / 天数)
 * 增重说明摄入大于消耗，右侧相减后得到比摄入更低的消耗；减重则相反。
 * 7700 kcal/kg 只是「增量近似」，但每个周期都重新反推，误差会自我修正，
 * 也能覆盖静态公式估算不了的代谢适应（adaptive thermogenesis）。
 *
 * 两道护栏（缺一不可）：
 *   1. 记录不足（饮食天数或体重条数不够）直接返回 null，不硬算；
 *   2. 把结果夹在公式 TDEE 的 0.8~1.3 倍之间。饮食自报普遍偏低，
 *      不加约束会得出过低消耗 → 目标被压低 → 恶性循环。
 */
class EstimateTdeeUseCase @Inject constructor() {

    operator fun invoke(
        averageIntake: Int?,
        weightChangeKg: Float?,
        windowDays: Int,
        intakeDays: Int,
        weightSamples: Int,
        formulaTdee: Float
    ): Int? {
        if (averageIntake == null || weightChangeKg == null) return null
        if (intakeDays < MIN_INTAKE_DAYS || weightSamples < MIN_WEIGHT_SAMPLES) return null
        if (windowDays <= 0 || formulaTdee <= 0f) return null

        val raw = averageIntake - (weightChangeKg * KCAL_PER_KG / windowDays)
        if (!raw.isFinite() || raw <= 0f) return null

        return raw.coerceIn(formulaTdee * LOWER_CLAMP, formulaTdee * UPPER_CLAMP).roundToInt()
    }

    private companion object {
        /** 每公斤体重大致对应的热量（增量近似）。 */
        const val KCAL_PER_KG = 7700f
        const val MIN_INTAKE_DAYS = 10
        const val MIN_WEIGHT_SAMPLES = 2
        const val LOWER_CLAMP = 0.8f
        const val UPPER_CLAMP = 1.3f
    }
}