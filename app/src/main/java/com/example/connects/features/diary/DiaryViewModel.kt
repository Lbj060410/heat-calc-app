package com.example.connects.features.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.MealType
import com.example.connects.domain.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 某一天里某一餐次的记录。空餐次也会保留，日志页要显示「这顿还没记」。 */
data class DiaryMealGroup(
    val mealType: MealType,
    val entries: List<FoodEntry>,
    val totalCalories: Int
)

data class DiaryUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val groups: List<DiaryMealGroup> = emptyList(),
    val totalCalories: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val entryCount: Int get() = groups.sumOf { it.entries.size }
    val isEmpty: Boolean get() = entryCount == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiaryUiState())
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    /** 当前选中的日期，切换日期时由 flatMapLatest 取消上一个日期的订阅。 */
    private val selectedDate = MutableStateFlow(LocalDate.now())

    init {
        // 旧实现每次选日期都新起一个永不被取消的 collect，切几次日期就有几个订阅
        // 同时在回写 _uiState，页面可能显示上一个日期的记录。
        viewModelScope.launch {
            selectedDate
                .flatMapLatest { date ->
                    _uiState.update {
                        it.copy(selectedDate = date, isLoading = true, error = null)
                    }
                    foodRepository.getEntriesByDate(date.toEpochDay().toInt())
                        .map { entries -> toUiState(date, entries) }
                        .catch { e ->
                            emit(
                                _uiState.value.copy(
                                    isLoading = false,
                                    error = e.message ?: "加载失败"
                                )
                            )
                        }
                }
                .collect { _uiState.value = it }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    /**
     * 删除一条已记录的饮食。
     *
     * 不需要手动刷新列表：getEntriesByDate 是 Room 的 Flow，删表就会自动重新发出，
     * 上面的 flatMapLatest 收到新数据后会把当日分组和总热量一起重算。
     */
    fun deleteEntry(entry: FoodEntry) {
        if (entry.id <= 0) return
        viewModelScope.launch {
            try {
                foodRepository.deleteFoodEntry(entry.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "删除失败") }
            }
        }
    }

    private fun toUiState(date: LocalDate, entries: List<FoodEntry>): DiaryUiState {
        val groups = MealType.group(entries).map { (meal, mealEntries) ->
            DiaryMealGroup(
                mealType = meal,
                entries = mealEntries,
                totalCalories = mealEntries.sumOf { it.totalCalories.toInt() }
            )
        }
        return DiaryUiState(
            selectedDate = date,
            groups = groups,
            totalCalories = entries.sumOf { it.totalCalories.toInt() },
            isLoading = false
        )
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
