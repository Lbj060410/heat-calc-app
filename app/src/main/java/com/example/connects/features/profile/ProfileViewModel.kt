package com.example.connects.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.database.dao.UserWeightLogDao
import com.example.connects.core.database.entity.UserWeightLogEntity
import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WeightChartRange {
    MONTH,
    YEAR,
    ALL
}

data class WeightRecord(
    val date: LocalDate,
    val weight: Float
)

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val weightHistory: List<WeightRecord> = emptyList(),
    val firstWeightRecordDate: LocalDate? = null,
    val selectedRange: WeightChartRange = WeightChartRange.MONTH,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userWeightLogDao: UserWeightLogDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var allWeightHistory: List<WeightRecord> = emptyList()
    private var generatedDefaultName: String? = null

    init {
        observeUserProfile()
        observeWeightHistory()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                userRepository.getUserProfile().collect { profile ->
                    val displayName = resolveDisplayName(profile.name)
                    _uiState.value = _uiState.value.copy(
                        userProfile = profile.copy(name = displayName),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load profile"
                )
            }
        }
    }

    private fun observeWeightHistory() {
        viewModelScope.launch {
            try {
                userWeightLogDao.getAllLogsAsc().collect { logs ->
                    allWeightHistory = logs.map { log ->
                        WeightRecord(
                            date = LocalDate.ofEpochDay(log.dateEpochDay.toLong()),
                            weight = log.weightKg
                        )
                    }
                    val selectedRange = _uiState.value.selectedRange
                    val filtered = filterWeightHistory(selectedRange, allWeightHistory)
                    _uiState.value = _uiState.value.copy(
                        weightHistory = filtered,
                        firstWeightRecordDate = allWeightHistory.firstOrNull()?.date
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load weight history"
                )
            }
        }
    }

    fun selectChartRange(range: WeightChartRange) {
        val filtered = filterWeightHistory(range, allWeightHistory)
        _uiState.value = _uiState.value.copy(
            selectedRange = range,
            weightHistory = filtered
        )
    }

    fun updateUserProfile(
        name: String,
        gender: String,
        height: Float,
        currentWeight: Float,
        age: Int,
        goal: String,
        activityLevel: String
    ) {
        viewModelScope.launch {
            try {
                val normalizedName = resolveDisplayName(name)
                val existingProfile = _uiState.value.userProfile
                val updatedProfile = UserProfile(
                    name = normalizedName,
                    gender = gender,
                    heightCm = height,
                    weightKg = currentWeight,
                    age = age,
                    goal = goal,
                    activityLevel = activityLevel,
                    dailyCaloricTarget = existingProfile?.dailyCaloricTarget ?: 2000
                )

                userRepository.updateUserProfile(updatedProfile)
                upsertTodayWeightLog(currentWeight)

                _uiState.value = _uiState.value.copy(
                    userProfile = updatedProfile,
                    isEditing = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to update profile"
                )
            }
        }
    }

    private suspend fun upsertTodayWeightLog(weightKg: Float) {
        val todayEpochDay = LocalDate.now().toEpochDay().toInt()
        val existingLog = userWeightLogDao.getByDate(todayEpochDay)
        if (existingLog == null) {
            userWeightLogDao.insert(
                UserWeightLogEntity(
                    weightKg = weightKg,
                    dateEpochDay = todayEpochDay
                )
            )
        } else if (existingLog.weightKg != weightKg) {
            userWeightLogDao.update(existingLog.copy(weightKg = weightKg))
        }
    }

    private fun filterWeightHistory(
        range: WeightChartRange,
        source: List<WeightRecord>
    ): List<WeightRecord> {
        if (source.isEmpty()) return emptyList()

        val today = LocalDate.now()
        val startDate = when (range) {
            WeightChartRange.MONTH -> today.minusMonths(1)
            WeightChartRange.YEAR -> today.minusYears(1)
            WeightChartRange.ALL -> source.first().date
        }

        return source.filter { !it.date.isBefore(startDate) }
    }

    private fun resolveDisplayName(rawName: String): String {
        if (rawName.isNotBlank()) return rawName
        if (generatedDefaultName == null) {
            generatedDefaultName = generateRandomUsername()
        }
        return generatedDefaultName!!
    }

    private fun generateRandomUsername(): String {
        val prefixes = listOf("Calm", "Bright", "Fit", "Active", "Smart", "Swift")
        val suffixes = listOf("Panda", "Runner", "Fox", "Tiger", "Whale", "Falcon")
        val number = Random.nextInt(100, 1000)
        return "${prefixes.random()}${suffixes.random()}$number"
    }

    fun startEditing() {
        _uiState.value = _uiState.value.copy(isEditing = true)
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(isEditing = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}