package com.example.connects.domain.repository

import com.example.connects.domain.model.AdaptiveTargetState
import com.example.connects.domain.model.BodyStatus
import com.example.connects.domain.model.BodyStatusSettlement
import com.example.connects.domain.model.CalorieAdjustment
import com.example.connects.domain.model.MacroTargets
import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.model.UserWaistLog
import com.example.connects.domain.model.UserWeightLog
import com.example.connects.domain.model.WeightMeasurementContext
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUserProfile(): Flow<UserProfile>
    suspend fun updateUserProfile(userProfile: UserProfile)
    fun getDailyCalorieTarget(): Flow<Int>
    suspend fun recordWeight(
        weightKg: Float,
        measurementContext: WeightMeasurementContext = WeightMeasurementContext.MORNING_FASTING
    )

    /** 指定日期是否已记录体重，用于每天首次进入应用时的体重弹窗。 */
    fun observeHasWeightLog(dateEpochDay: Int): Flow<Boolean>

    /** 区间内的体重记录（按日期升序），用于「我的」页身体状态评估。 */
    fun getWeightLogsBetween(startEpochDay: Int, endEpochDay: Int): Flow<List<UserWeightLog>>

    /** 记录一次腰围。同一天重复记录会覆盖，不会产生两条。 */
    suspend fun recordWaist(waistCm: Float)

    /** 最近一次腰围记录；从未记录过时为 null。用于「每 15 天必填一次」的到期判断。 */
    fun observeLatestWaistLog(): Flow<UserWaistLog?>

    /** 周期内的最后一次腰围记录，作为该周期的期末值。 */
    fun observeLatestWaistLogInRange(
        startEpochDay: Int,
        endEpochDay: Int
    ): Flow<UserWaistLog?>

    /** 周期开始之前的最后一次腰围记录，作为该周期的期初基线。 */
    fun observeLatestWaistLogBefore(dateEpochDay: Int): Flow<UserWaistLog?>

    /** 已结算的身体状态（每 15 天结算一次，期间固定不变）。 */
    fun getBodyStatusSettlement(): Flow<BodyStatusSettlement>

    suspend fun saveBodyStatusSettlement(status: BodyStatus, settledDay: Int)

    /** 正式记录的第一天；没有存储值时回退到最早一条体重记录。 */
    fun getFirstRecordDay(): Flow<Int?>

    /** 首次正式记录时写入，之后不再改动。 */
    suspend fun ensureFirstRecordDay(day: Int)

    /** 由「摄入 − 体重变化」反推出来的每日消耗；null 表示还没估出。 */
    fun getEstimatedTdee(): Flow<Int?>

    suspend fun saveEstimatedTdee(tdee: Int)

    /** 自适应引擎状态（是否接管、上次调整方向与连续次数）。 */
    fun getAdaptiveTargetState(): Flow<AdaptiveTargetState>

    /** 营养素目标（克）；从未设置过时为 null。 */
    fun getMacroTargets(): Flow<MacroTargets?>

    suspend fun saveMacroTargets(macroTargets: MacroTargets)

    /** 一次性落盘自适应目标，并把目标标记为「已接管」。 */
    suspend fun saveAdaptiveTarget(
        targetCalories: Int,
        estimatedTdee: Int,
        macroTargets: MacroTargets,
        direction: String,
        directionRun: Int
    )

    /** 最近一次热量调整记录，用于解释「目标为什么变了」。 */
    fun getCalorieAdjustment(): Flow<CalorieAdjustment>

    suspend fun saveCalorieAdjustment(adjustment: CalorieAdjustment)
}
