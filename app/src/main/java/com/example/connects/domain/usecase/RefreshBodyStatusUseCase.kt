package com.example.connects.domain.usecase

import com.example.connects.core.util.todayEpochDay
import com.example.connects.domain.model.BodyStatus
import com.example.connects.domain.model.BodyStatusSnapshot
import com.example.connects.domain.model.CalorieAdjustment
import com.example.connects.domain.model.CalorieAdvice
import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.repository.FoodRepository
import com.example.connects.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 到期时评估身体状态（7 天一次）并推进自适应热量引擎（15 天一次）。
 *
 * 评估与调整解耦：
 *  - 评估：每 [CalculateBodyStatusUseCase.ASSESS_WINDOW_DAYS] 天刷新一次身体状态标签、
 *    TDEE 估算与执行建议，但不改热量目标。
 *  - 调整：每 [CalculateBodyStatusUseCase.ADJUST_WINDOW_DAYS] 天评估一次热量目标，
 *    满 1 个月后交给自适应引擎改数字。
 *
 * 体重趋势用更长历史（[CalculateBodyStatusUseCase.TREND_WINDOW_DAYS] 天）拟合，
 * 趋势更稳；饮食与腰围仍按 15 天窗口。
 *
 * 例外：结果为「数据不足」时不写入、也不开始倒计时。否则新用户刚装上 App 就被
 * 「数据不足」锁满一个周期，即使第 3 天就有足够数据也不会更新。
 *
 * 第一个月严格用公式值，让数据先攒够再交给引擎。
 *
 * 「读结算 → 计算 → 写结算」不是原子操作，若被并发调用会让同一个周期被调整两次
 * （两个 delta 叠加）。这里用 [Singleton] + [Mutex] 串行化：后到的调用拿到锁后
 * 会重新读到刚写下的结算日，判定为未到期直接返回。
 */
@Singleton
class RefreshBodyStatusUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val foodRepository: FoodRepository,
    private val calculateBodyStatusUseCase: CalculateBodyStatusUseCase,
    private val estimateTdeeUseCase: EstimateTdeeUseCase,
    private val adjustCalorieTargetUseCase: AdjustCalorieTargetUseCase,
    private val calculateMacroTargetsUseCase: CalculateMacroTargetsUseCase,
    private val calculateBmrUseCase: CalculateBmrUseCase,
    private val calculateTdeeUseCase: CalculateTdeeUseCase
) {

    private val mutex = Mutex()

    suspend operator fun invoke(today: Int = todayEpochDay()) {
        mutex.withLock {
            refresh(today)
        }
    }

    private suspend fun refresh(today: Int) {
        // 评估（7 天）与热量调整（15 天）两个周期独立判断，任一到期就进入计算。
        val settlement = userRepository.getBodyStatusSettlement().first()
        val assessDue = settlement.settledDay <= 0 ||
            today - settlement.settledDay >= CalculateBodyStatusUseCase.ASSESS_WINDOW_DAYS

        val lastAdjustEvalDay = userRepository.getLastAdjustEvalDay().first()
        val adjustDue = lastAdjustEvalDay <= 0 ||
            today - lastAdjustEvalDay >= CalculateBodyStatusUseCase.ADJUST_WINDOW_DAYS

        if (!assessDue && !adjustDue) return

        val profile = userRepository.getUserProfile().first()
        // 体重趋势用更长的 60 天历史拟合（更稳），饮食和腰围仍按 15 天窗口。
        val weightWindowStart = today - (CalculateBodyStatusUseCase.TREND_WINDOW_DAYS - 1)
        val windowStart = today - (CalculateBodyStatusUseCase.WINDOW_DAYS - 1)
        val weightLogs = userRepository.getWeightLogsBetween(weightWindowStart, today).first()
        val foodEntries = foodRepository.getEntriesByDateRange(windowStart, today).first()

        // 腰围按「上个周期期末 -> 本周期期末」比较，所以基线取窗口开始之前的最后一条。
        val waistBaseline = userRepository.observeLatestWaistLogBefore(windowStart).first()
        val waistPeriodEnd = userRepository.observeLatestWaistLogInRange(windowStart, today).first()

        val formulaTdee = calculateTdeeUseCase(
            calculateBmrUseCase(
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                ageYears = profile.age,
                isMale = profile.gender == "male"
            ),
            profile.activityLevel
        )
        val estimatedTdee = userRepository.getEstimatedTdee().first()
        val referenceTdee = estimatedTdee ?: formulaTdee.roundToInt()

        val snapshot = calculateBodyStatusUseCase(
            weightLogs = weightLogs,
            waistBaseline = waistBaseline,
            waistPeriodEnd = waistPeriodEnd,
            foodEntries = foodEntries,
            referenceTdee = referenceTdee
        )
        if (snapshot.status == BodyStatus.INSUFFICIENT_DATA) return

        val newEstimate = estimateTdeeUseCase(
            averageIntake = snapshot.averageIntake,
            weightChangeKg = snapshot.weightChangeKg,
            windowDays = CalculateBodyStatusUseCase.WINDOW_DAYS,
            intakeDays = snapshot.intakeDays,
            weightSamples = snapshot.weightSamples,
            formulaTdee = formulaTdee
        )
        // 评估：每 7 天刷新身体状态与 TDEE 估算，但不动热量目标。
        if (assessDue) {
            if (newEstimate != null) userRepository.saveEstimatedTdee(newEstimate)
            userRepository.saveBodyStatusSettlement(snapshot.status, today)
        }

        // 调整：每 15 天评估一次热量目标（满 1 个月后交给自适应引擎）。
        // 自适应引擎是附加能力：它出错不该连累已经算好的身体状态结算。
        if (adjustDue) {
            runCatching {
                adjustIfEligible(
                    today = today,
                    profile = profile,
                    snapshot = snapshot,
                    estimatedTdee = newEstimate
                )
            }
            // 无论本轮是否真的改了数字，都记录「已评估过调整」，15 天后才会再评估。
            userRepository.saveLastAdjustEvalDay(today)
        }
    }

    /** 满 1 个月且本轮能估出消耗时，才让自适应引擎接管目标。 */
    private suspend fun adjustIfEligible(
        today: Int,
        profile: UserProfile,
        snapshot: BodyStatusSnapshot,
        estimatedTdee: Int?
    ) {
        val firstRecordDay = userRepository.getFirstRecordDay().first() ?: today
        if (today - firstRecordDay < ADAPTIVE_START_DAYS) return

        val weightChangeKg = snapshot.weightChangeKg ?: return
        if (estimatedTdee == null) return

        val state = userRepository.getAdaptiveTargetState().first()
        val plan = adjustCalorieTargetUseCase(
            goal = profile.goal,
            currentTarget = profile.dailyCaloricTarget,
            estimatedTdee = estimatedTdee,
            bodyStatus = snapshot.status,
            weightChangeKg = weightChangeKg,
            waistChangeCm = snapshot.waistChangeCm,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            isMale = profile.gender == "male",
            lastDirection = state.lastAdjustDirection,
            lastRun = state.lastAdjustRun,
            // 实际摄入既是判断「该不该改目标」的依据，也是给用户的执行反馈。
            averageIntake = snapshot.averageIntake,
            intakeDays = snapshot.intakeDays
        )

        val macroTargets = calculateMacroTargetsUseCase(
            weightKg = profile.weightKg,
            goal = profile.goal,
            targetCalories = plan.newTargetCalories
        )
        userRepository.saveAdaptiveTarget(
            targetCalories = plan.newTargetCalories,
            estimatedTdee = estimatedTdee,
            macroTargets = macroTargets,
            direction = plan.direction,
            directionRun = plan.consecutiveRun
        )

        if (plan.delta != 0) {
            userRepository.saveCalorieAdjustment(
                CalorieAdjustment(
                    day = today,
                    beforeCalories = profile.dailyCaloricTarget,
                    afterCalories = plan.newTargetCalories,
                    reason = plan.reason
                )
            )
        }

        // 依从性偏离时目标不动，只把执行建议落盘；本轮真的改了目标就把旧建议清掉，
        // 免得「本期建议」还挂着上一周期的内容。
        userRepository.saveCalorieAdvice(
            if (plan.blockedByAdherence) {
                CalorieAdvice(
                    day = today,
                    reason = plan.reason,
                    adherencePercent = plan.adherenceRatio?.let { (it * 100).roundToInt() }
                )
            } else {
                CalorieAdvice()
            }
        )
    }

    companion object {
        /** 正式记录起满这么多天后，目标才交给自适应引擎（第一个月用公式值）。 */
        const val ADAPTIVE_START_DAYS = 30
    }
}