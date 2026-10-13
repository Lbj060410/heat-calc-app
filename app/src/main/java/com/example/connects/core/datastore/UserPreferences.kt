package com.example.connects.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.connects.domain.model.AdaptiveTargetState
import com.example.connects.domain.model.BodyStatus
import com.example.connects.domain.model.BodyStatusSettlement
import com.example.connects.domain.model.CalorieAdjustment
import com.example.connects.domain.model.MacroTargets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserPreferences(private val context: Context) {

    companion object {
        val KEY_IS_ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_GENDER = stringPreferencesKey("user_gender")
        val KEY_USER_HEIGHT = floatPreferencesKey("user_height")
        val KEY_USER_WEIGHT = floatPreferencesKey("user_weight")
        val KEY_USER_AGE = intPreferencesKey("user_age")
        val KEY_USER_GOAL = stringPreferencesKey("user_goal")
        val KEY_ACTIVITY_LEVEL = stringPreferencesKey("activity_level")
        val KEY_DAILY_CALORIE_TARGET = intPreferencesKey("daily_calorie_target")
        val KEY_BODY_STATUS = stringPreferencesKey("body_status")
        val KEY_BODY_STATUS_SETTLED_DAY = intPreferencesKey("body_status_settled_day")

        /** 正式记录的第一天（epochDay）。自适应引擎的「第一个月」从这里开始算。 */
        val KEY_FIRST_RECORD_DAY = intPreferencesKey("first_record_day")
        /** 目标是否已由自适应引擎接管；为 true 时禁止用公式值覆盖目标。 */
        val KEY_IS_ADAPTIVE_TARGET = booleanPreferencesKey("is_adaptive_target")
        /** 由体重变化反推的每日消耗；未估出时不存在。 */
        val KEY_ESTIMATED_TDEE = intPreferencesKey("estimated_tdee")
        val KEY_TARGET_PROTEIN_G = floatPreferencesKey("target_protein_g")
        val KEY_TARGET_CARBS_G = floatPreferencesKey("target_carbs_g")
        val KEY_TARGET_FAT_G = floatPreferencesKey("target_fat_g")
        val KEY_LAST_ADJUST_DAY = intPreferencesKey("last_adjust_day")
        val KEY_LAST_ADJUST_BEFORE = intPreferencesKey("last_adjust_before")
        val KEY_LAST_ADJUST_AFTER = intPreferencesKey("last_adjust_after")
        val KEY_LAST_ADJUST_REASON = stringPreferencesKey("last_adjust_reason")
        val KEY_LAST_ADJUST_DIRECTION = stringPreferencesKey("last_adjust_direction")
        val KEY_LAST_ADJUST_RUN = intPreferencesKey("last_adjust_run")
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_ONBOARDING_COMPLETE] ?: false
        }

    suspend fun saveUserProfile(
        name: String = "",
        gender: String,
        height: Float,
        weight: Float,
        age: Int,
        goal: String,
        activityLevel: String,
        dailyCaloricTarget: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_NAME] = name
            preferences[KEY_USER_GENDER] = gender
            preferences[KEY_USER_HEIGHT] = height
            preferences[KEY_USER_WEIGHT] = weight
            preferences[KEY_USER_AGE] = age
            preferences[KEY_USER_GOAL] = goal
            preferences[KEY_ACTIVITY_LEVEL] = activityLevel
            preferences[KEY_DAILY_CALORIE_TARGET] = dailyCaloricTarget
            preferences[KEY_IS_ONBOARDING_COMPLETE] = true
        }
    }

    val userName: Flow<String> = context.dataStore.data.map { it[KEY_USER_NAME] ?: "" }
    val userGender: Flow<String> = context.dataStore.data.map { it[KEY_USER_GENDER] ?: "male" }
    val userHeight: Flow<Float> = context.dataStore.data.map { it[KEY_USER_HEIGHT] ?: 170f }
    val userWeight: Flow<Float> = context.dataStore.data.map { it[KEY_USER_WEIGHT] ?: 60f }
    val userAge: Flow<Int> = context.dataStore.data.map { it[KEY_USER_AGE] ?: 25 }
    val userGoal: Flow<String> = context.dataStore.data.map { it[KEY_USER_GOAL] ?: "maintain" }
    val activityLevel: Flow<String> = context.dataStore.data.map { it[KEY_ACTIVITY_LEVEL] ?: "sedentary" }
    val dailyCaloricTarget: Flow<Int> = context.dataStore.data.map { it[KEY_DAILY_CALORIE_TARGET] ?: 2000 }

    suspend fun updateUserWeight(weight: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_WEIGHT] = weight
        }
    }

    suspend fun updateDailyCaloricTarget(target: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DAILY_CALORIE_TARGET] = target
        }
    }

    /** 已结算的身体状态。settledDay 为 0 表示还没结算过（数据不足，15 天周期未开始）。 */
    val bodyStatusSettlement: Flow<BodyStatusSettlement> = context.dataStore.data.map { preferences ->
        val status = preferences[KEY_BODY_STATUS]
            ?.let { stored -> BodyStatus.entries.firstOrNull { it.name == stored } }
            ?: BodyStatus.INSUFFICIENT_DATA
        BodyStatusSettlement(
            status = status,
            settledDay = preferences[KEY_BODY_STATUS_SETTLED_DAY] ?: 0
        )
    }

    suspend fun saveBodyStatusSettlement(status: BodyStatus, settledDay: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BODY_STATUS] = status.name
            preferences[KEY_BODY_STATUS_SETTLED_DAY] = settledDay
        }
    }

    /** 正式记录的第一天；从没记录过时为 null。 */
    val firstRecordDay: Flow<Int?> = context.dataStore.data.map { it[KEY_FIRST_RECORD_DAY] }

    /** 只写一次：已经有了就不动，保证「第一天」不会被后续记录刷新。 */
    suspend fun setFirstRecordDayIfAbsent(day: Int) {
        context.dataStore.edit { preferences ->
            if (preferences[KEY_FIRST_RECORD_DAY] == null) {
                preferences[KEY_FIRST_RECORD_DAY] = day
            }
        }
    }

    val isAdaptiveTarget: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_IS_ADAPTIVE_TARGET] ?: false }

    val estimatedTdee: Flow<Int?> = context.dataStore.data.map { it[KEY_ESTIMATED_TDEE] }

    suspend fun saveEstimatedTdee(tdee: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ESTIMATED_TDEE] = tdee
        }
    }

    /** 营养素目标（克）。从未设置过时为 null，界面回落到按热量百分比推算。 */
    val macroTargets: Flow<MacroTargets?> = context.dataStore.data.map { preferences ->
        val protein = preferences[KEY_TARGET_PROTEIN_G]
        val carbs = preferences[KEY_TARGET_CARBS_G]
        val fat = preferences[KEY_TARGET_FAT_G]
        if (protein == null && carbs == null && fat == null) {
            null
        } else {
            MacroTargets(
                proteinG = protein ?: 0f,
                carbsG = carbs ?: 0f,
                fatG = fat ?: 0f
            )
        }
    }

    suspend fun saveMacroTargets(macroTargets: MacroTargets) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TARGET_PROTEIN_G] = macroTargets.proteinG
            preferences[KEY_TARGET_CARBS_G] = macroTargets.carbsG
            preferences[KEY_TARGET_FAT_G] = macroTargets.fatG
        }
    }

    val adaptiveTargetState: Flow<AdaptiveTargetState> = context.dataStore.data.map { preferences ->
        AdaptiveTargetState(
            isAdaptive = preferences[KEY_IS_ADAPTIVE_TARGET] ?: false,
            estimatedTdee = preferences[KEY_ESTIMATED_TDEE],
            lastAdjustDirection = preferences[KEY_LAST_ADJUST_DIRECTION]
                ?: AdaptiveTargetState.DIRECTION_NONE,
            lastAdjustRun = preferences[KEY_LAST_ADJUST_RUN] ?: 0
        )
    }

    /** 自适应引擎一次性落盘：目标热量 + 估算消耗 + 营养素 + 调整方向记录。 */
    suspend fun saveAdaptiveTarget(
        targetCalories: Int,
        estimatedTdee: Int,
        macroTargets: MacroTargets,
        direction: String,
        directionRun: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DAILY_CALORIE_TARGET] = targetCalories
            preferences[KEY_ESTIMATED_TDEE] = estimatedTdee
            preferences[KEY_TARGET_PROTEIN_G] = macroTargets.proteinG
            preferences[KEY_TARGET_CARBS_G] = macroTargets.carbsG
            preferences[KEY_TARGET_FAT_G] = macroTargets.fatG
            preferences[KEY_LAST_ADJUST_DIRECTION] = direction
            preferences[KEY_LAST_ADJUST_RUN] = directionRun
            preferences[KEY_IS_ADAPTIVE_TARGET] = true
        }
    }

    /** 最近一次调整记录；从未调整过时 day=0。 */
    val calorieAdjustment: Flow<CalorieAdjustment> = context.dataStore.data.map { preferences ->
        CalorieAdjustment(
            day = preferences[KEY_LAST_ADJUST_DAY] ?: 0,
            beforeCalories = preferences[KEY_LAST_ADJUST_BEFORE] ?: 0,
            afterCalories = preferences[KEY_LAST_ADJUST_AFTER] ?: 0,
            reason = preferences[KEY_LAST_ADJUST_REASON].orEmpty()
        )
    }

    suspend fun saveCalorieAdjustment(adjustment: CalorieAdjustment) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_ADJUST_DAY] = adjustment.day
            preferences[KEY_LAST_ADJUST_BEFORE] = adjustment.beforeCalories
            preferences[KEY_LAST_ADJUST_AFTER] = adjustment.afterCalories
            preferences[KEY_LAST_ADJUST_REASON] = adjustment.reason
        }
    }
}
