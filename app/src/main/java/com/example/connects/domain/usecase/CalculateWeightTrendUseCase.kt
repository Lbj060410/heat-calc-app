package com.example.connects.domain.usecase

import com.example.connects.domain.model.UserWeightLog
import javax.inject.Inject

/**
 * 用最小二乘线性回归拟合体重随时间的变化，返回观察区间内的「拟合变化量」（kg）。
 *
 * 相比「最后一条 − 第一条」，回归会把区间内的所有点都算进去，能削弱单日水分波动
 * 被当成趋势的问题（用户决定：体重趋势用线性回归）。
 * 样本不足以拟合一条直线（少于 2 条、或全部落在同一天）时返回 null。
 */
class CalculateWeightTrendUseCase @Inject constructor() {

    operator fun invoke(logs: List<UserWeightLog>): Float? {
        val points = logs.sortedBy { it.dateEpochDay }
        if (points.size < MIN_SAMPLES) return null

        val firstDay = points.first().dateEpochDay
        val span = points.last().dateEpochDay - firstDay
        if (span <= 0) return null

        val n = points.size.toDouble()
        val meanX = points.sumOf { (it.dateEpochDay - firstDay).toDouble() } / n
        val meanY = points.sumOf { it.weightKg.toDouble() } / n

        var sxy = 0.0
        var sxx = 0.0
        points.forEach { log ->
            val dx = (log.dateEpochDay - firstDay).toDouble() - meanX
            val dy = log.weightKg.toDouble() - meanY
            sxy += dx * dy
            sxx += dx * dx
        }
        if (sxx <= 0.0) return null

        val slopePerDay = sxy / sxx
        return (slopePerDay * span).toFloat()
    }

    private companion object {
        const val MIN_SAMPLES = 2
    }
}