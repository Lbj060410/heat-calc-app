package com.example.connects.features.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DiaryUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val foodEntries: List<FoodEntry> = emptyList(),
    val totalCalories: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()
    
    init {
        loadFoodEntriesForDate(_uiState.value.selectedDate)
    }
    
    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadFoodEntriesForDate(date)
    }
    
    private fun loadFoodEntriesForDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 获取选中日期的食物记录
                val epochDay = date.toEpochDay().toInt()
                foodRepository.getEntriesByDate(epochDay).collect { entryList ->
                    // 计算总热量
                    val totalCals = entryList.sumOf { it.totalCalories.toInt() }
                    
                    _uiState.value = _uiState.value.copy(
                        foodEntries = entryList,
                        totalCalories = totalCals,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
    
    fun getFormattedDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
        return date.format(formatter)
    }
    
    fun getDayOfWeek(date: LocalDate): String {
        val dayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return dayNames[date.dayOfWeek.value % 7]
    }
}
