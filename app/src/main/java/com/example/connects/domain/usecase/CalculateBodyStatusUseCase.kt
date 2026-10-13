package com.example.connects.domain.usecase

import com.example.connects.domain.model.BodyStatus
import com.example.connects.domain.model.BodyStatusSnapshot
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.UserWaistLog
import com.example.connects.domain.model.UserWeightLog
import javax.inject.Inject

/**
 * 由最近一个对比窗口的「体重趋势 + 腰围趋势 + 热量摄入」推断身体状态。
 *
 * 判定思路（体重趋势定大方向，腰围区分脂肪还是瘦体重，热量作为没有腰围时的代理）：
 *   - 体重上升 + 腰围上升        -> 增肥
 *   - 体重上升 + 腰围持平/下降   -> 增肌
 *   - 体重持平 + 腰围下降        -> 减脂（典型的减脂重塑）
 *   - 体重持平 + 腰围上升        -> 增肥
 *   - 体重下降 + 热量明显赤字    -> 减肥
 *   - 体重下降 + 其余            -> 减脂
 *   - 以上都不符合               -> 保持
 *
 * 体重趋势用最小二乘线性回归（[CalculateWeightTrendUseCase]），不是首尾相减，
 * 这样能削弱单日水分波动被当成趋势的问题。
 *
 * 热量盈亏的参照系是「估算消耗」（estimatedTdee），不是「当前热量目标」。
 * 目标本身会被自适应引擎调整，拿目标当参照会变成自证循环（目标一降，同样的摄入就变盈余）。
 *
 * 腰围走的是「上周期期末 -> 本周期期末」，而不是限定在窗口内：腰围 15 天才量一次，
 * 窗口内通常只有一个点，算不出区间内的趋势。缺腰围时自动退回用热量盈余当代理判断。
 *
 * 记录太少时返回「数据不足」，不拿一两条数据就下结论。
 */
class CalculateBodyStatusUseCase @Inject constructor(
    private val calculateWeightTrendUseCase: CalculateWeightTrendUseCase
) {

    operator fun invoke(
        weightLogs: List<UserWeightLog>,
        waistBaseline: UserWaistLog?,
        waistPeriodEnd: UserWaistLog?,
        foodEntries: List<FoodEntry>,
        referenceTdee: Int
    ): BodyStatusSnapshot {
        // 每天的摄入合计。没记录饮食的那天不按 0 计，否则会严重低估日均摄入。
        val dailyTotals = foodEntries
            .groupBy { it.dateEpochDay }
            .map { (_, entries) -> entries.sumOf { it.totalCalories.toDouble() } }

        val intakeDays = dailyTotals.size
        val averageIntake = if (intakeDays >= MIN_INTAKE_DAYS) {
            dailyTotals.average().toInt()
        } else {
            null
        }

        val sortedWeights = weightLogs.sortedBy { it.dateEpochDay }
        val weightChangeKg = if (sortedWeights.size >= MIN_WEIGHT_SAMPLES) {
            calculateWeightTrendUseCase(sortedWeights)
        } else {
            null
        }

        val waistChangeCm = if (waistBaseline != null && waistPeriodEnd != null) {
            waistPeriodEnd.waistCm - waistBaseline.waistCm
        } else {
            null
        }

        return BodyStatusSnapshot(
            status = classify(weightChangeKg, waistChangeCm, averageIntake, referenceTdee),
            windowDays = WINDOW_DAYS,
            referenceTdee = referenceTdee,
            averageIntake = averageIntake,
            weightChangeKg = weightChangeKg,
            waistChangeCm = waistChangeCm,
            intakeDays = intakeDays,
            weightSamples = sortedWeights.size
        )
    }

    private fun classify(
        weightChangeKg: Float?,
        waistChangeCm: Float?,
        averageIntake: Int?,
        referenceTdee: Int
    ): BodyStatus {
        if (weightChangeKg == null || averageIntake == null || referenceTdee <= 0) {
            return BodyStatus.INSUFFICIENT_DATA
        }

        val ratio = averageIntake.toFloat() / referenceTdee
        val surplus = ratio >= SURPLUS_RATIO
        val deficit = ratio <= DEFICIT_RATIO

        val waistUp = waistChangeCm != null && waistChangeCm >= WAIST_STABLE_CM
        val waistDown = waistChangeCm != null && waistChangeCm <= -WAIST_STABLE_CM

        return when {
            // 体重上升：腰围跟着涨说明长的主要是脂肪，否则多半是瘦体重。
            // 没有腰围数据时，退回用热量盈余当代理。
            weightChangeKg >= WEIGHT_STABLE_KG -> when {
                waistChangeCm != null -> if (waistUp) BodyStatus.FAT_GAIN else BodyStatus.MUSCLE_GAIN
                surplus -> BodyStatus.FAT_GAIN
                else -> BodyStatus.MUSCLE_GAIN
            }

            weightChangeKg <= -WEIGHT_STABLE_KG ->
                if (deficit) BodyStatus.WEIGHT_LOSS else BodyStatus.FAT_LOSS

            // 体重没变时靠腰围区分：腰围掉是减脂重塑，腰围涨是脂肪在增加。
            waistDown -> BodyStatus.FAT_LOSS
            waistUp -> BodyStatus.FAT_GAIN
            deficit -> BodyStatus.FAT_LOSS
            else -> BodyStatus.MAINTAIN
        }
    }

    companion object {
        /** 每 15 天为一个对比窗口，同时也是「必填一次腰围」的间隔。 */
        const val WINDOW_DAYS = 15

        /** 窗口内体重变化不超过这个值就算「基本没变」。 */
        private const val WEIGHT_STABLE_KG = 0.5f

        /**
         * 腰围变化不超过这个值就算「基本没变」。
         * 量腰围本身有 1cm 上下的误差，阈值取太小会把测量噪声当成趋势。
         */
        private const val WAIST_STABLE_CM = 1.0f

        /** 日均摄入达到消耗的这个倍数以上算「明显盈余」。 */
        private const val SURPLUS_RATIO = 1.10f

        /** 日均摄入低于消耗的这个倍数算「明显赤字」。 */
        private const val DEFICIT_RATIO = 0.90f

        /** 至少这么多天的饮食记录才评估热量盈亏。 */
        private const val MIN_INTAKE_DAYS = 5

        /** 至少两条体重记录才能算趋势。 */
        private const val MIN_WEIGHT_SAMPLES = 2
    }
}