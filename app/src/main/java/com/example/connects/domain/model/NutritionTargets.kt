package com.example.connects.domain.model

/** 三大营养素的目标摄入量（克）。 */
data class MacroTargets(
    val proteinG: Float,
    val carbsG: Float,
    val fatG: Float
)

/**
 * 自适应热量引擎的持久化状态。
 *
 * 满 1 个月后目标交给引擎接管：每 15 天结算身体状态时一起调整热量与营养素，
 * 期间用户在「我的」页改昵称等操作不得覆盖这个目标。
 */
data class AdaptiveTargetState(
    /** 目标是否已由自适应引擎接管。为 true 时禁止用公式值重算覆盖。 */
    val isAdaptive: Boolean = false,
    /** 由「摄入 − 体重变化」反推出来的每日消耗；null 表示还没估出来。 */
    val estimatedTdee: Int? = null,
    /** 上一次调整方向："up" / "down" / "none"。 */
    val lastAdjustDirection: String = "none",
    /** 同方向连续调整的次数，用于限制「连续同向调整」。 */
    val lastAdjustRun: Int = 0
) {
    companion object {
        const val DIRECTION_UP = "up"
        const val DIRECTION_DOWN = "down"
        const val DIRECTION_NONE = "none"
    }
}

/** 最近一次热量目标调整记录，用于向用户解释「目标为什么变了」。 */
data class CalorieAdjustment(
    /** 调整当天（epochDay）；0 表示从未调整过。 */
    val day: Int = 0,
    val beforeCalories: Int = 0,
    val afterCalories: Int = 0,
    val reason: String = ""
) {
    val delta: Int get() = afterCalories - beforeCalories
    val happened: Boolean get() = day > 0 && delta != 0
}