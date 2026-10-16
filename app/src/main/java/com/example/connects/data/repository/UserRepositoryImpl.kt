package com.example.connects.data.repository

import com.example.connects.core.database.dao.UserWaistLogDao
import com.example.connects.core.database.dao.UserWeightLogDao
import com.example.connects.core.database.entity.UserWaistLogEntity
import com.example.connects.core.database.entity.UserWeightLogEntity
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.core.util.todayEpochDay
import com.example.connects.data.mapper.UserProfileMapper
import com.example.connects.data.mapper.UserWaistLogMapper
import com.example.connects.data.mapper.UserWeightLogMapper
import com.example.connects.domain.model.AdaptiveTargetState
import com.example.connects.domain.model.BodyStatus
import com.example.connects.domain.model.BodyStatusSettlement
import com.example.connects.domain.model.CalorieAdjustment
import com.example.connects.domain.model.MacroTargets
import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.model.UserWaistLog
import com.example.connects.domain.model.UserWeightLog
import com.example.connects.domain.model.WeightMeasurementContext
import com.example.connects.domain.repository.UserRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userPreferences: UserPreferences,
    private val userWeightLogDao: UserWeightLogDao,
    private val userWaistLogDao: UserWaistLogDao,
    private val userProfileMapper: UserProfileMapper,
    private val userWeightLogMapper: UserWeightLogMapper,
    private val userWaistLogMapper: UserWaistLogMapper
) : UserRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        // 分两次 combine：一次性 combine 8 个不同类型的 Flow 会被推断成
        // Comparable & Serializable 的交集类型，编译器提示这以后会变成错误。
        val basics = combine(
            userPreferences.userName,
            userPreferences.userGender,
            userPreferences.userHeight,
            userPreferences.userWeight,
            userPreferences.userAge
        ) { name, gender, height, weight, age ->
            ProfileBasics(name, gender, height, weight, age)
        }

        return combine(
            basics,
            userPreferences.userGoal,
            userPreferences.activityLevel,
            userPreferences.dailyCaloricTarget
        ) { base, goal, activityLevel, dailyCaloricTarget ->
            userProfileMapper.toDomain(
                name = base.name,
                gender = base.gender,
                height = base.height,
                weight = base.weight,
                age = base.age,
                goal = goal,
                activityLevel = activityLevel,
                dailyCaloricTarget = dailyCaloricTarget
            )
        }
    }

    override suspend fun updateUserProfile(userProfile: UserProfile) {
        userPreferences.saveUserProfile(
            name = userProfile.name,
            gender = userProfile.gender,
            height = userProfile.heightCm,
            weight = userProfile.weightKg,
            age = userProfile.age,
            goal = userProfile.goal,
            activityLevel = userProfile.activityLevel,
            dailyCaloricTarget = userProfile.dailyCaloricTarget
        )
    }

    override fun getDailyCalorieTarget(): Flow<Int> {
        return userPreferences.dailyCaloricTarget
    }

    override suspend fun recordWeight(
        weightKg: Float,
        measurementContext: WeightMeasurementContext
    ) {
        val todayEpochDay = LocalDate.now().toEpochDay().toInt()
        // 第一次记录体重就是「正式记录的第一天」，自适应引擎的第一个月从这里开始。
        userPreferences.setFirstRecordDayIfAbsent(todayEpochDay)
        val context = measurementContext.storageKey
        val existingLog = userWeightLogDao.getByDate(todayEpochDay)
        if (existingLog == null) {
            userWeightLogDao.insert(
                UserWeightLogEntity(
                    weightKg = weightKg,
                    dateEpochDay = todayEpochDay,
                    measurementContext = context
                )
            )
        } else if (existingLog.weightKg != weightKg || existingLog.measurementContext != context) {
            userWeightLogDao.update(
                existingLog.copy(weightKg = weightKg, measurementContext = context)
            )
        }
        userPreferences.updateUserWeight(weightKg)
    }

    override fun observeHasWeightLog(dateEpochDay: Int): Flow<Boolean> {
        return userWeightLogDao.observeHasLogForDate(dateEpochDay)
    }

    override fun getWeightLogsBetween(
        startEpochDay: Int,
        endEpochDay: Int
    ): Flow<List<UserWeightLog>> {
        return userWeightLogDao.getLogsBetween(startEpochDay, endEpochDay)
            .map { entities -> userWeightLogMapper.toDomainList(entities) }
    }

    override suspend fun recordWaist(waistCm: Float) {
        val todayEpochDay = todayEpochDay()
        val existingLog = userWaistLogDao.getByDate(todayEpochDay)
        if (existingLog == null) {
            userWaistLogDao.insert(
                UserWaistLogEntity(
                    waistCm = waistCm,
                    dateEpochDay = todayEpochDay
                )
            )
        } else if (existingLog.waistCm != waistCm) {
            userWaistLogDao.update(existingLog.copy(waistCm = waistCm))
        }
    }

    override fun observeLatestWaistLog(): Flow<UserWaistLog?> {
        return userWaistLogDao.observeLatest().map { userWaistLogMapper.toDomainOrNull(it) }
    }

    override fun observeLatestWaistLogInRange(
        startEpochDay: Int,
        endEpochDay: Int
    ): Flow<UserWaistLog?> {
        return userWaistLogDao.observeLatestInRange(startEpochDay, endEpochDay)
            .map { userWaistLogMapper.toDomainOrNull(it) }
    }

    override fun observeLatestWaistLogBefore(dateEpochDay: Int): Flow<UserWaistLog?> {
        return userWaistLogDao.observeLatestBefore(dateEpochDay)
            .map { userWaistLogMapper.toDomainOrNull(it) }
    }

    override fun getBodyStatusSettlement(): Flow<BodyStatusSettlement> {
        return userPreferences.bodyStatusSettlement
    }

    override suspend fun saveBodyStatusSettlement(status: BodyStatus, settledDay: Int) {
        userPreferences.saveBodyStatusSettlement(status, settledDay)
    }

    override fun getFirstRecordDay(): Flow<Int?> {
        // 老用户可能没存过第一天，用最早一条体重记录兜底，避免整段自适应判断失效。
        return combine(
            userPreferences.firstRecordDay,
            userWeightLogDao.observeEarliestDate()
        ) { stored, earliest -> stored ?: earliest }
    }

    override suspend fun ensureFirstRecordDay(day: Int) {
        userPreferences.setFirstRecordDayIfAbsent(day)
    }

    override fun getEstimatedTdee(): Flow<Int?> = userPreferences.estimatedTdee

    override suspend fun saveEstimatedTdee(tdee: Int) {
        userPreferences.saveEstimatedTdee(tdee)
    }

    override fun getAdaptiveTargetState(): Flow<AdaptiveTargetState> =
        userPreferences.adaptiveTargetState

    override fun getMacroTargets(): Flow<MacroTargets?> = userPreferences.macroTargets

    override suspend fun saveMacroTargets(macroTargets: MacroTargets) {
        userPreferences.saveMacroTargets(macroTargets)
    }

    override suspend fun saveAdaptiveTarget(
        targetCalories: Int,
        estimatedTdee: Int,
        macroTargets: MacroTargets,
        direction: String,
        directionRun: Int
    ) {
        userPreferences.saveAdaptiveTarget(
            targetCalories = targetCalories,
            estimatedTdee = estimatedTdee,
            macroTargets = macroTargets,
            direction = direction,
            directionRun = directionRun
        )
    }

    override fun getCalorieAdjustment(): Flow<CalorieAdjustment> = userPreferences.calorieAdjustment

    override suspend fun saveCalorieAdjustment(adjustment: CalorieAdjustment) {
        userPreferences.saveCalorieAdjustment(adjustment)
    }

    private data class ProfileBasics(
        val name: String,
        val gender: String,
        val height: Float,
        val weight: Float,
        val age: Int
    )
}
