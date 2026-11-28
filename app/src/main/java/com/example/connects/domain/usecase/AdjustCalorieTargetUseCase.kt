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
    val consecutiveRun: Int,
    /** 本周期日均摄入相对当前目标的达成率（0.85 = 只吃到目标的 85%）；记录不足时为 null。 */
    val adherenceRatio: Float? = null,
    /** 本次是否因执行偏离目标被拦下 —— 只给执行建议，没有改数字。 */
    val blockedByAdherence: Boolean = false
)

/**
 * 按目标（意图）+ 实测身体状态（体重回归趋势、腰围）调整每日热量。
 *
 * 处理原则：意图和实测冲突时相信实测。速率目标以「每周期（15 天）相对体重的变化」衡量：
 *   减脂 0.5~1.0%/周 → 1.0~2.1%/15 天；增肌 0.25~0.5%/周 → 0.5~1.1%/15 天；维持 ±0。
 * 单次调整幅度上限 ±200 kcal，同方向最多连续两轮，之后强制观察一轮。
 * 所有调整最终都要过安全下限：女性 ≥1200、男性 ≥1500 kcal；赤字不超过 TDEE 的 25%、
 * 盈余不超过 20%；BMI<18.5 不再下调。
 *
 * 【依从性闸门】目标只有在被真正执行时才是有效杠杆。窗口内日均摄入对目标的达成率
 * 低于 90% 或高于 110% 时，说明体重变化主要来自「没按目标吃」而不是「计划本身不对」，
 * 这时不改数字，只输出执行建议 —— 否则会出现「吃 2000 却没瘦，就把目标降到 1850」
 * 这类越吃越少的恶性循环。
 *
 * 减脂掉得过快（超过 [TOO_FAST_LOSS_RATE]）本该上调热量保护瘦体重，但这个例外同样要求
 * 达成率在区间内：上调的只是「目标数字」，只有真按目标吃的人才会因此多吃；欠吃的人
 * 上调后照样吃那么多，保护不到体型，反而让偏离越滚越大。所以欠吃时照拦、只给提醒。
 * 该例外优先于同向次数限制，避免连续几轮掉太快却只得到一句「先观察」。
 * 窗口内饮食记录少于 [MIN_ADHERENCE_DAYS] 天时日均摄入不可信，闸门不生效。
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
        lastRun: Int,
        /** 窗口内日均摄入 kcal；记录不足时传 null。 */
        averageIntake: Int? = null,
        /** 窗口内有饮食记录的天数。 */
        intakeDays: Int = 0
    ): CalorieAdjustmentPlan {
        val rate = if (weightKg > 0f) weightChangeKg / weightKg else 0f
        val bmi = if (heightCm > 0f) weightKg / ((heightCm / 100f) * (heightCm / 100f)) else 0f

        // 意图与实测冲突：减脂却涨重、增肌却掉重、维持却明显偏离。
        // 这是「目标偏高/偏低」的最强证据（真实消耗被高/低估了），此时相信实测调整目标，
        // 不再用「欠吃/超吃」闸门拦截 —— 否则会陷入「目标偏高 → 劝你补到目标 → 补足更胖」的死循环。
        val intentConflictsWithMeasurement = when (goal) {
            "lose" -> weightChangeKg >= WEIGHT_STABLE_KG
            "gain" -> weightChangeKg <= -WEIGHT_STABLE_KG
            else -> weightChangeKg >= WEIGHT_STABLE_KG || weightChangeKg <= -WEIGHT_STABLE_KG
        }

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
                        reason = if (weightChangeKg >= WEIGHT_STABLE_KG) {
                            "体重上涨，说明消耗被高估，下调热量"
                        } else {
                            "体重基本没降且腰围没有下降，下调热量"
                        }
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

        // 达成率：分母取「用户当时被要求吃的量」，即 currentTarget。
        val adherence = averageIntake
            ?.takeIf { it > 0 && currentTarget > 0 }
            ?.let { it.toFloat() / currentTarget }

        // 达成率在区间内算「按目标吃了」；记录不足时数据不可信，同样视为不拦。
        val ateToPlan = adherence == null || adherence in UNDER_EAT_RATIO..OVER_EAT_RATIO

        // 安全例外：减脂掉得太快本该上调热量保护瘦体重，但上调的只是「目标数字」——
        // 只有真按目标吃的人才会因此多吃。欠吃的人上调后照样吃那么多，保护不到体型，
        // 反而让偏离越滚越大，所以例外同样要求「按目标吃了」。
        val safetyIncrease = goal == "lose" && rate <= -TOO_FAST_LOSS_RATE && ateToPlan

        var direction = directionOf(delta)

        // 同方向最多连续两轮，避免一路加/减把自己推到极端。
        // 安全例外优先：否则连续三轮掉太快的人只会拿到一句「先观察」，没有任何保护。
        var nextRun = runAfter(direction, lastDirection, lastRun)
        if (!safetyIncrease &&
            direction != AdaptiveTargetState.DIRECTION_NONE &&
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

        // 依从性闸门放在最后：先让安全边界把 delta 夹完，再判断是否真的打算改。
        // 欠吃/超吃时目标不动 —— 包括减脂掉太快的情形，那时该做的是把摄入补到目标附近。
        // 但意图与实测冲突（减脂却涨重等）时跳过：体重已经证明目标偏高/偏低，应相信实测纠正。
        var blockedByAdherence = false
        val intake = averageIntake?.takeIf { it > 0 }

        if (delta != 0 && !safetyIncrease && !intentConflictsWithMeasurement &&
            intake != null && adherence != null
        ) {
            val advice = adherenceAdvice(adherence, intake, intakeDays)
            if (advice != null) {
                reason = advice
                delta = 0
                proposed = currentTarget
                direction = AdaptiveTargetState.DIRECTION_NONE
                nextRun = 0
                blockedByAdherence = true
            }
        }

        if (delta == 0 && reason.isBlank()) {
            reason = if (intakeDays < MIN_ADHERENCE_DAYS) {
                "本周期饮食记录只有 $intakeDays 天，数据偏少，先保持热量"
            } else {
                "保持当前热量"
            }
        }

        return CalorieAdjustmentPlan(
            newTargetCalories = proposed,
            delta = delta,
            reason = reason,
            direction = direction,
            consecutiveRun = nextRun,
            adherenceRatio = adherence,
            blockedByAdherence = blockedByAdherence
        )
    }

    /** 达成率偏离目标区间时给出的执行建议；在区间内返回 null（照常按趋势调）。 */
    private fun adherenceAdvice(
        adherence: Float,
        averageIntake: Int,
        intakeDays: Int
    ): String? = when {
        adherence < UNDER_EAT_RATIO ->
            "近 $intakeDays 天日均摄入约 $averageIntake kcal，只有目标的 " +
                "${(adherence * 100).roundToInt()}%。体重变化主要来自这个执行缺口，" +
                "先把摄入补到目标附近，再看趋势决定要不要改热量。"

        adherence > OVER_EAT_RATIO ->
            "近 $intakeDays 天日均摄入约 $averageIntake kcal，超出目标的 " +
                "${((adherence - 1f) * 100).roundToInt()}%。体重变化主要来自这个执行偏差，" +
                "先按目标吃，再看趋势决定要不要改热量。"

        else -> null
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
        /** 窗口内体重变化超过这个值就算「明显偏离」，与身体状态结算的稳定阈值一致。 */
        const val WEIGHT_STABLE_KG = 0.5f

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

        /** 日均摄入低于目标的这个比例算「欠吃」，闸门拦下所有改目标的动作。 */
        const val UNDER_EAT_RATIO = 0.90f

        /** 日均摄入高于目标的这个比例算「超吃」，同样拦下。 */
        const val OVER_EAT_RATIO = 1.10f

        /** 至少这么多天的饮食记录，日均摄入才足够可信到能启用闸门。 */
        const val MIN_ADHERENCE_DAYS = 5
    }
}