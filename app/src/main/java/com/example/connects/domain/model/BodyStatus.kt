package com.example.connects.domain.model

/**
 * 「我的」页展示的身体状态。
 *
 * 由【体重变化趋势】+【热量摄入相对目标的盈亏】推断，每 15 天结算一次并固定下来，
 * 到下一个结算日才会重新评估。只是给用户一个直观反馈，不是医学结论。
 */
enum class BodyStatus(val label: String, val hint: String) {
    MUSCLE_GAIN("增肌", "体重上升，热量只是小幅盈余，偏向增肌"),
    FAT_GAIN("增肥", "体重上升且热量明显盈余，脂肪增长偏多"),
    MAINTAIN("保持", "体重和热量摄入都保持稳定"),
    FAT_LOSS("减脂", "热量赤字下体重下降或持平，偏向减脂"),
    WEIGHT_LOSS("减肥", "热量明显赤字，体重下降较快"),
    INSUFFICIENT_DATA("数据不足", "本周期记录太少，继续记录后开始评估")
}

/** 已结算的身体状态：15 天内固定不变，直到下一个结算日。 */
data class BodyStatusSettlement(
    val status: BodyStatus,
    /** 结算当天（epochDay）。0 表示还没结算过——数据不足，15 天周期尚未开始。 */
    val settledDay: Int
)

/** 身体状态的计算结果，除状态本身外还带上推断依据，方便界面解释「为什么是这个结论」。 */
data class BodyStatusSnapshot(
    val status: BodyStatus,
    val windowDays: Int,
    /** 判断热量盈亏用的参照消耗（估算 TDEE，没有估算时用公式值）。 */
    val referenceTdee: Int,
    /** 窗口内有饮食记录的那些天的日均摄入 kcal；饮食记录不足时为 null。 */
    val averageIntake: Int?,
    /** 窗口内由线性回归拟合出的体重变化 kg；体重记录不足时为 null。 */
    val weightChangeKg: Float?,
    /** 本周期期末腰围 − 上周期期末腰围（cm）；缺少任一次记录时为 null。 */
    val waistChangeCm: Float?,
    /** 窗口内有饮食记录的天数。 */
    val intakeDays: Int,
    /** 窗口内的体重记录条数。 */
    val weightSamples: Int,
    /** 已结算结论的结算日；null 表示尚未结算，展示的是实时评估。 */
    val settledDay: Int? = null
)
