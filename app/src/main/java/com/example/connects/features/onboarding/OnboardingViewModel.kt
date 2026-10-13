package com.example.connects.features.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.domain.model.ActivityLevel
import com.example.connects.domain.repository.UserRepository
import com.example.connects.domain.usecase.CalculateDailyCalorieTargetUseCase
import com.example.connects.domain.usecase.CalculateMacroTargetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OnboardingUiState(
    val heightInput: String = "170",
    val waistInput: String = "75",
    val weightInput: String = "60",
    val ageInput: String = "25",
    val genderInput: String = "male",
    val selectedGoal: String = "maintain",
    val selectedActivityLevel: ActivityLevel = ActivityLevel.DEFAULT,
    val isInputValid: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val userRepository: UserRepository,
    private val calculateDailyCalorieTargetUseCase: CalculateDailyCalorieTargetUseCase,
    private val calculateMacroTargetsUseCase: CalculateMacroTargetsUseCase
) : ViewModel() {

    var uiState by mutableStateOf(OnboardingUiState())
        private set

    var heightInput by mutableStateOf("170")
        private set
    var waistInput by mutableStateOf("75")
        private set
    var weightInput by mutableStateOf("60")
        private set
    var ageInput by mutableStateOf("25")
        private set
    var genderInput by mutableStateOf("male")
        private set
    var selectedGoal by mutableStateOf("maintain")
        private set
    var selectedActivityLevel by mutableStateOf(ActivityLevel.DEFAULT)
        private set

    fun updateHeight(value: String) {
        heightInput = value
        updateValidation()
    }

    fun updateWaist(value: String) {
        waistInput = value
        updateValidation()
    }

    fun updateWeight(value: String) {
        weightInput = value
        updateValidation()
    }

    fun updateAge(value: String) {
        ageInput = value
        updateValidation()
    }

    fun updateGender(value: String) {
        genderInput = value
    }

    fun updateGoal(value: String) {
        selectedGoal = value
    }

    fun updateActivityLevel(value: ActivityLevel) {
        selectedActivityLevel = value
    }

    private fun updateValidation() {
        val isValid = isInputValid()
        uiState = uiState.copy(
            heightInput = heightInput,
            waistInput = waistInput,
            weightInput = weightInput,
            ageInput = ageInput,
            genderInput = genderInput,
            selectedGoal = selectedGoal,
            selectedActivityLevel = selectedActivityLevel,
            isInputValid = isValid
        )
    }

    fun isInputValid(): Boolean {
        val h = heightInput.toFloatOrNull()
        val waist = waistInput.toFloatOrNull()
        val w = weightInput.toFloatOrNull()
        val a = ageInput.toIntOrNull()

        return h != null && h > 50 && h < 300 &&
               waist != null && waist > 30 && waist < 200 &&
               w != null && w > 20 && w < 500 &&
               a != null && a > 10 && a < 150
    }

    fun submitProfile(onComplete: () -> Unit) {
        if (!isInputValid()) return

        val finalHeight = heightInput.toFloat()
        val finalWaist = waistInput.toFloat()
        val finalWeight = weightInput.toFloat()
        val finalAge = ageInput.toInt()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val adjustedCalories = calculateDailyCalorieTargetUseCase(
                    weightKg = finalWeight,
                    heightCm = finalHeight,
                    ageYears = finalAge,
                    gender = genderInput,
                    goal = selectedGoal,
                    activityLevel = selectedActivityLevel
                )

                userPreferences.saveUserProfile(
                    name = "",
                    gender = genderInput,
                    height = finalHeight,
                    weight = finalWeight,
                    age = finalAge,
                    goal = selectedGoal,
                    activityLevel = selectedActivityLevel.storageKey,
                    dailyCaloricTarget = adjustedCalories
                )

                // 引导页刚填过体重和腰围，直接落记录：既避免进入应用后又弹一次弹窗，
                // 也让第一个结算周期就有腰围基线可用（同时写下「正式记录的第一天」）。
                userRepository.recordWeight(finalWeight)
                userRepository.recordWaist(finalWaist)

                // 营养素目标按体重算出并持久化，首页直接读，避免各处各算一套。
                userPreferences.saveMacroTargets(
                    calculateMacroTargetsUseCase(
                        weightKg = finalWeight,
                        goal = selectedGoal,
                        targetCalories = adjustedCalories
                    )
                )

                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
