package com.example.connects.domain.usecase

import com.example.connects.domain.model.AdaptiveTargetState
import com.example.connects.domain.model.BodyStatus
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/** 一次热量调整的结论：新目标 + 调整量 + 给用户看的理由。 */
data class CalorieAdjustmentPlan(
    val newTargetCalories: Int,
    val delta: Int,
    val reason: String,
    /** "up" / "down" / "none"。 */
    val direction: String,
    /** 本次调整后同方向连续次数，用于限制连续同向调整。 */
    val consecutiveRun: Int
)

/**
 * 按目标（意图）+ 实测身体状态（体重回归趋势、腰围）调整每日热量。
 *
 * 处理原则：意图和实测冲突时相信实测。速率目标以「每周期（15 天）相对体重的变化」衡量：
 *   减脂 0.5~1.0%/周 → 1.0~2.1%/15 天；增肌 0.25~0.5%/周 → 0.5~1.1%/15 天；维持 ±0。
 * 单次调整幅度上限 ±200 kcal，同方向最多连续两轮，之后强制观察一轮。
 * 所有调整最终都要过安全下限：女性 ≥1200、男性 ≥1500 kcal；赤字不超过 TDEE 的 25%、
 * 盈余不超过 20%；BMI<18.5 不再下调。
 */
class AdjustCalorieTargetUseCase @Inject constructor() {

    operator fun invoke(
        goal: String,
        currentTarget: Int,
        estimatedTdee: Int,
        bodyStatus: BodyStatus,
        weightChangeKg: Float,
        waistChangeCm: Float?,
        weightKg: Float,
        heightCm: Float,
        isMale: Boolean,
        lastDirection: String,
        lastRun: Int
    ): CalorieAdjustmentPlan {
        val rate = if (weightKg > 0f) weightChangeKg / weightKg else 0f
        val bmi = if (heightCm > 0f) weightKg / ((heightCm / 100f) * (heightCm / 100f)) else 0f

        var delta = 0
        var reason = ""

        when (goal) {
            "lose" -> {
                val waistNotDropping = waistChangeCm == null || waistChangeCm > -WAIST_DROP_CM
                when {
                    bmi in 0.1f..LOW_BMI -> {
                        reason = "BMI 偏低，不再下调热量"
                    }
                    rate <= -TOO_FAST_LOSS_RATE -> {
                        delta = INCREASE
                        reason = "体重下降过快（超过 2.1%/周期），回调热量保护瘦体重"
                    }
                    rate > -MIN_LOSS_RATE && waistNotDropping -> {
                        delta = DECREASE
                        reason = "体重基本没降且腰围没有下降，下调热量"
                    }
                    rate > -MIN_LOSS_RATE -> {
                        reason = "体重持平但腰围在下降，属于减脂重塑，保持热量"
                    }
                    else -> {
                        reason = "处于合理减脂速率，保持热量"
                    }
                }
            }

            "gain" -> {
                when {
                    rate > TOO_FAST_GAIN_RATE || bodyStatus == BodyStatus.FAT_GAIN -> {
                        delta = DECREASE
                        reason = "增重过快或腰围上涨，下调热量"
                    }
                    rate < MIN_GAIN_RATE -> {
                        delta = INCREASE
                        reason = "增重不足（低于 0.5%/周期），上调热量"
                    }
                    else -> {
                        reason = "增肌速率合理，保持热量"
                    }
                }
            }

            else -> {
                when {
                    rate > MAINTAIN_DRIFT_RATE -> {
                        delta = DECREASE_MAINTAIN
                        reason = "体重上涨，下调热量"
                    }
                    rate < -MAINTAIN_DRIFT_RATE -> {
                        delta = INCREASE_MAINTAIN
                        reason = "体重下降，上调热量"
                    }
                    else -> {
                        reason = "体重稳定，保持热量"
                    }
                }
            }
        }

        delta = delta.coerceIn(-MAX_ADJUST, MAX_ADJUST)
        var direction = directionOf(delta)

        // 同方向最多连续两轮，避免一路加/减把自己推到极端。
        var nextRun = runAfter(direction, lastDirection, lastRun)
        if (direction != AdaptiveTargetState.DIRECTION_NONE &&
            direction == lastDirection && lastRun >= MAX_SAME_DIRECTION_RUN
        ) {
            delta = 0
            direction = AdaptiveTargetState.DIRECTION_NONE
            nextRun = 0
            reason = "已连续 $lastRun 轮同向调整，本周期先观察"
        }

        // 安全下限与上限。
        val genderFloor = if (isMale) MALE_FLOOR else FEMALE_FLOOR
        val deficitFloor = (estimatedTdee * MAX_DEFICIT_RATIO).roundToInt()
        val surplusCap = (estimatedTdee * MAX_SURPLUS_RATIO).roundToInt()

        var proposed = (currentTarget + delta)
            .coerceAtLeast(max(genderFloor, deficitFloor))
            .coerceAtMost(surplusCap)

        val actualDelta = proposed - currentTarget
        if (actualDelta == 0 && delta != 0) {
            proposed = currentTarget
            reason = "已到安全边界（$proposed kcal），暂不再调整"
        }
        delta = actualDelta
        direction = directionOf(delta)
        nextRun = runAfter(direction, lastDirection, lastRun)

        if (delta == 0 && reason.isBlank()) reason = "保持当前热量"

        return CalorieAdjustmentPlan(
            newTargetCalories = proposed,
            delta = delta,
            reason = reason,
            direction = direction,
            consecutiveRun = nextRun
        )
    }

    private fun directionOf(delta: Int): String = when {
        delta > 0 -> AdaptiveTargetState.DIRECTION_UP
        delta < 0 -> AdaptiveTargetState.DIRECTION_DOWN
        else -> AdaptiveTargetState.DIRECTION_NONE
    }

    private fun runAfter(direction: String, lastDirection: String, lastRun: Int): Int = when {
        direction == AdaptiveTargetState.DIRECTION_NONE -> 0
        direction == lastDirection -> lastRun + 1
        else -> 1
    }

    private companion object {
        // 每 15 天相对体重的变化速率阈值。
        const val MIN_LOSS_RATE = 0.006f          // 0.6%
        const val TOO_FAST_LOSS_RATE = 0.021f     // 2.1%
        const val MIN_GAIN_RATE = 0.005f          // 0.5%
        const val TOO_FAST_GAIN_RATE = 0.011f     // 1.1%
        const val MAINTAIN_DRIFT_RATE = 0.007f    // 0.7%

        /** 腰围下降超过 1cm 才算「在掉」。 */
        const val WAIST_DROP_CM = 1.0f

        const val INCREASE = 150
        const val DECREASE = -150
        const val INCREASE_MAINTAIN = 100
        const val DECREASE_MAINTAIN = -100

        const val MAX_ADJUST = 200
        const val MAX_SAME_DIRECTION_RUN = 2

        const val FEMALE_FLOOR = 1200
        const val MALE_FLOOR = 1500
        const val MAX_DEFICIT_RATIO = 0.75f
        const val MAX_SURPLUS_RATIO = 1.20f
        const val LOW_BMI = 18.5f
    }
}