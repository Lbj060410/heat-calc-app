package com.example.connects.domain.usecase

import com.example.connects.core.util.todayEpochDay
import com.example.connects.domain.model.BodyStatus
import com.example.connects.domain.model.BodyStatusSnapshot
import com.example.connects.domain.model.CalorieAdjustment
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
 * 到期时结算一次身体状态，并把结论固定写进本地存储。
 *
 * 「固定结算」的含义：结论一旦写入，此后 [CalculateBodyStatusUseCase.WINDOW_DAYS] 天内不再变化，
 * 即使中途体重或饮食数据变了也不会改，直到下一个结算日才重新评估。
 *
 * 例外：结果为「数据不足」时不写入、也不开始倒计时。否则新用户刚装上 App 就被
 * 「数据不足」锁满 15 天，即使第 3 天就有足够数据也不会更新。
 *
 * 结算成功后顺带推进自适应热量引擎：
 *   1. 用「摄入 − 体重变化」反推真实每日消耗并落盘；
 *   2. 从「正式记录的第一天」起满 1 个月后，按目标和实测趋势调整热量与营养素，
 *      之后每个 15 天周期跟着结算一起调整（与身体状态同频）。
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
        val settlement = userRepository.getBodyStatusSettlement().first()
        val isDue = settlement.settledDay <= 0 ||
            today - settlement.settledDay >= CalculateBodyStatusUseCase.WINDOW_DAYS
        if (!isDue) return

        val profile = userRepository.getUserProfile().first()
        val windowStart = today - (CalculateBodyStatusUseCase.WINDOW_DAYS - 1)
        val weightLogs = userRepository.getWeightLogsBetween(windowStart, today).first()
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
        if (newEstimate != null) userRepository.saveEstimatedTdee(newEstimate)

        userRepository.saveBodyStatusSettlement(snapshot.status, today)

        // 自适应引擎是附加能力：它出错不该连累已经算好的身体状态结算，
        // 也不该把错误抛到「我的」页让用户看到一句莫名其妙的报错。
        runCatching {
            adjustIfEligible(
                today = today,
                profile = profile,
                snapshot = snapshot,
                estimatedTdee = newEstimate
            )
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
            lastRun = state.lastAdjustRun
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
    }

    companion object {
        /** 正式记录起满这么多天后，目标才交给自适应引擎（第一个月用公式值）。 */
        const val ADAPTIVE_START_DAYS = 30
    }
}