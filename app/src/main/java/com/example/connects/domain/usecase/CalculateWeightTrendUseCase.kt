package com.example.connects.domain.usecase

import com.example.connects.domain.model.UserWeightLog
import com.example.connects.domain.model.WeightMeasurementContext
import javax.inject.Inject

/**
 * 用「加权 Theil-Sen」计算体重随时间的变化，返回观察区间内的「拟合变化量」（kg）。
 *
 * 该方法把两类需求合并成一个估计器：
 * - **抗离群**：取所有点对斜率的中位数而不是均值，个别虚高的夜秤/水分波动点不会撬偏趋势
 *   （普通最小二乘里，窗首/窗尾的异常点杠杆作用大，短窗尤其容易带歪）。
 * - **时段加权**：并非人人都能早上空腹称，晨起空腹最可复现，给它的更高置信权重，
 *   让趋势更锚定最准的点；其他时段降权但不会剔除（见 [weightOf]）。
 *
 * 每条数据对应的测量时段由 [UserWeightLog.measurementContext] 提供。
 * 样本不足以拟合一条直线（少于 2 条、或全部落在同一天）时返回 null。
 */
class CalculateWeightTrendUseCase @Inject constructor() {

    operator fun invoke(logs: List<UserWeightLog>): Float? {
        val points = logs.sortedBy { it.dateEpochDay }
        if (points.size < MIN_SAMPLES) return null

        val firstDay = points.first().dateEpochDay
        val span = points.last().dateEpochDay - firstDay
        if (span <= 0) return null

        // 枚举所有点对：(dx>0 保证斜率分母恒正)，点对权重 = 两点置信权重之积。
        val slopes = ArrayList<SlopeWeight>(points.size * (points.size - 1) / 2)
        for (i in 0 until points.size) {
            for (j in i + 1 until points.size) {
                val dx = (points[j].dateEpochDay - points[i].dateEpochDay).toDouble()
                val dy = points[j].weightKg - points[i].weightKg
                val weight = weightOf(points[i].measurementContext) *
                    weightOf(points[j].measurementContext)
                slopes.add(SlopeWeight(slope = dy / dx, weight = weight))
            }
        }

        val slopePerDay = weightedMedianSlope(slopes) ?: return null
        return (slopePerDay * span).toFloat()
    }

    /** 按时段给置信权重（用户排定：晨起空腹最高，其余从高到低为 其他 > 练后 > 餐后）。
     *  权重只是高低关系，值可微调。 */
    private fun weightOf(context: WeightMeasurementContext): Double = when (context) {
        WeightMeasurementContext.MORNING_FASTING -> 1.0
        WeightMeasurementContext.OTHER -> 0.8
        WeightMeasurementContext.POST_WORKOUT -> 0.6
        WeightMeasurementContext.POST_MEAL -> 0.4
    }

    /** 按斜率升序累加权重的加权中位数。返回 null 当没有可用点对。 */
    private fun weightedMedianSlope(pairs: List<SlopeWeight>): Double? {
        if (pairs.isEmpty()) return null
        val sorted = pairs.sortedBy { it.slope }
        val total = sorted.sumOf { it.weight }
        var accumulated = 0.0
        for (pw in sorted) {
            accumulated += pw.weight
            if (accumulated >= total / 2.0) return pw.slope
        }
        return sorted.last().slope
    }

    private data class SlopeWeight(val slope: Double, val weight: Double)

    private companion object {
        const val MIN_SAMPLES = 2
    }
}