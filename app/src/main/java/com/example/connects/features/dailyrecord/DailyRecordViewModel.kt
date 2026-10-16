package com.example.connects.features.dailyrecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.util.todayEpochDayFlow
import com.example.connects.domain.model.WeightMeasurementContext
import com.example.connects.domain.repository.UserRepository
import com.example.connects.domain.usecase.CalculateBodyStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DailyRecordUiState(
    val weightInput: String = "",
    val waistInput: String = "",
    val measurementContext: WeightMeasurementContext = WeightMeasurementContext.autoInfer(
        java.time.LocalTime.now().hour
    ),
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * 进入应用时的必填记录。
 *
 * - 体重：每天一次，用于看每日体重变化趋势。
 * - 腰围：每 15 天一次，用于身体状态的脂肪/瘦体重判断。
 *
 * 两者都是必填，所以共用一个不可关闭的弹窗：只显示当前到期的字段，
 * 保存完一项后另一项（如果也到期）继续显示，直到全部记录完才放行。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyRecordViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyRecordUiState())
    val uiState: StateFlow<DailyRecordUiState> = _uiState.asStateFlow()

    private val _needsWeightInput = MutableStateFlow<Boolean?>(null)
    val needsWeightInput: StateFlow<Boolean?> = _needsWeightInput.asStateFlow()

    private val _needsWaistInput = MutableStateFlow<Boolean?>(null)
    val needsWaistInput: StateFlow<Boolean?> = _needsWaistInput.asStateFlow()

    /**
     * 本次会话内用户点了「跳过」。
     *
     * 只存在内存里、不落盘：跳过后本次不再弹窗，下次进入应用（重建 ViewModel）
     * 会因为当天仍缺记录而重新弹出。
     */
    private val _isSkipped = MutableStateFlow(false)
    val isSkipped: StateFlow<Boolean> = _isSkipped.asStateFlow()

    init {
        observeTodayWeightRecorded()
        observeWaistRecorded()
    }

    /** null 表示还没读出结果，界面先不弹，避免每次冷启动都闪一下弹窗。 */
    private fun observeTodayWeightRecorded() {
        viewModelScope.launch {
            todayEpochDayFlow()
                .flatMapLatest { day -> userRepository.observeHasWeightLog(day) }
                .collect { hasLog -> _needsWeightInput.value = !hasLog }
        }
    }

    private fun observeWaistRecorded() {
        viewModelScope.launch {
            todayEpochDayFlow()
                .flatMapLatest { today ->
                    userRepository.observeLatestWaistLog().map { latest ->
                        // 从没量过也算到期；量过则满 15 天后再要求一次。
                        // 间隔必须和身体状态的结算周期一致，否则结算时拿不到本周期的腰围。
                        latest == null ||
                            today - latest.dateEpochDay >= CalculateBodyStatusUseCase.WINDOW_DAYS
                    }
                }
                .collect { isDue -> _needsWaistInput.value = isDue }
        }
    }

    fun updateWeightInput(value: String) {
        _uiState.value = _uiState.value.copy(weightInput = value, error = null)
    }

    fun updateWeightContext(context: WeightMeasurementContext) {
        _uiState.value = _uiState.value.copy(measurementContext = context, error = null)
    }

    fun updateWaistInput(value: String) {
        _uiState.value = _uiState.value.copy(waistInput = value, error = null)
    }

    /** 本次会话跳过：不记录任何数据，下次进入应用会重新弹窗。 */
    fun skip() {
        _isSkipped.value = true
    }

    fun save() {
        val needsWeight = _needsWeightInput.value == true
        val needsWaist = _needsWaistInput.value == true

        val weight = _uiState.value.weightInput.toFloatOrNull()
        val waist = _uiState.value.waistInput.toFloatOrNull()

        if (needsWeight && (weight == null || weight <= 0f)) {
            _uiState.value = _uiState.value.copy(error = "请输入有效体重")
            return
        }
        if (needsWaist && (waist == null || waist <= 0f)) {
            _uiState.value = _uiState.value.copy(error = "请输入有效腰围")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                if (needsWeight && weight != null) {
                    userRepository.recordWeight(
                        weightKg = weight,
                        measurementContext = _uiState.value.measurementContext
                    )
                }
                if (needsWaist && waist != null) userRepository.recordWaist(waist)
                _uiState.value = DailyRecordUiState()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "保存失败"
                )
            }
        }
    }
}
