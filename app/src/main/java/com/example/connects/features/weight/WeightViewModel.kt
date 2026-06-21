package com.example.connects.features.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeightRecordUiState(
    val input: String = "",
    val isSaving: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeightRecordUiState())
    val uiState: StateFlow<WeightRecordUiState> = _uiState.asStateFlow()

    fun updateInput(value: String) {
        _uiState.value = _uiState.value.copy(input = value, message = null)
    }

    fun saveWeight() {
        val weight = _uiState.value.input.toFloatOrNull()
        if (weight == null || weight <= 0f) {
            _uiState.value = _uiState.value.copy(message = "请输入有效体重")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null)
            try {
                userRepository.recordWeight(weight)
                _uiState.value = WeightRecordUiState(message = "体重已保存")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    message = e.message ?: "保存失败"
                )
            }
        }
    }
}
