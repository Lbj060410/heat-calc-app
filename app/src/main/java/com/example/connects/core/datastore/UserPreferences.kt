package com.example.connects.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
    val activityLevel: Flow<String> = context.dataStore.data.map { it[KEY_ACTIVITY_LEVEL] ?: "moderate" }
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
}
