package com.example.connects.features.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.domain.model.ActivityLevel
import com.example.connects.domain.usecase.CalculateBmrUseCase
import com.example.connects.domain.usecase.CalculateTdeeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OnboardingUiState(
    val heightInput: String = "170",
    val weightInput: String = "60",
    val ageInput: String = "25",
    val genderInput: String = "male",
    val selectedGoal: String = "maintain",
    val selectedActivityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    val isInputValid: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val calculateBmrUseCase: CalculateBmrUseCase,
    private val calculateTdeeUseCase: CalculateTdeeUseCase
) : ViewModel() {

    var uiState by mutableStateOf(OnboardingUiState())
        private set

    var heightInput by mutableStateOf("170")
        private set
    var weightInput by mutableStateOf("60")
        private set
    var ageInput by mutableStateOf("25")
        private set
    var genderInput by mutableStateOf("male")
        private set
    var selectedGoal by mutableStateOf("maintain")
        private set
    var selectedActivityLevel by mutableStateOf(ActivityLevel.SEDENTARY)
        private set

    fun updateHeight(value: String) {
        heightInput = value
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
        val w = weightInput.toFloatOrNull()
        val a = ageInput.toIntOrNull()

        return h != null && h > 50 && h < 300 &&
               w != null && w > 20 && w < 500 &&
               a != null && a > 10 && a < 150
    }

    fun submitProfile(onComplete: () -> Unit) {
        if (!isInputValid()) return

        val finalHeight = heightInput.toFloat()
        val finalWeight = weightInput.toFloat()
        val finalAge = ageInput.toInt()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isMale = genderInput == "male"
                // 使用更新后的 CalculateBmrUseCase 直接计算 TDEE
                val tdee = calculateBmrUseCase(finalWeight, finalHeight, finalAge, isMale, selectedActivityLevel)

                val adjustedCalories = when (selectedGoal) {
                    "lose" -> (tdee * 0.85).toInt()
                    "gain" -> (tdee * 1.15).toInt()
                    else -> tdee.toInt()
                }

                userPreferences.saveUserProfile(
                    name = "",
                    gender = genderInput,
                    height = finalHeight,
                    weight = finalWeight,
                    age = finalAge,
                    goal = selectedGoal,
                    activityLevel = selectedActivityLevel.name.lowercase(),
                    dailyCaloricTarget = adjustedCalories
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
