package com.example.connects.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.util.todayEpochDayFlow
import com.example.connects.domain.model.ActivityLevel
import com.example.connects.domain.model.BodyStatusSettlement
import com.example.connects.domain.model.BodyStatusSnapshot
import com.example.connects.domain.model.CalorieAdjustment
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.UserProfile
import com.example.connects.domain.model.UserWaistLog
import com.example.connects.domain.model.UserWeightLog
import com.example.connects.domain.repository.FoodRepository
import com.example.connects.domain.repository.UserRepository
import com.example.connects.domain.usecase.CalculateBmrUseCase
import com.example.connects.domain.usecase.CalculateBodyStatusUseCase
import com.example.connects.domain.usecase.CalculateDailyCalorieTargetUseCase
import com.example.connects.domain.usecase.CalculateMacroTargetsUseCase
import com.example.connects.domain.usecase.CalculateTdeeUseCase
import com.example.connects.domain.usecase.RefreshBodyStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val bodyStatus: BodyStatusSnapshot? = null,
    /** 最近一次腰围（cm），用于编辑资料时回填；从未记录过时为 null。 */
    val latestWaistCm: Float? = null,
    /** 最近一次热量目标调整，用于解释「目标为什么变了」。 */
    val adjustment: CalorieAdjustment = CalorieAdjustment(),
    /** 目标是否已由自适应引擎接管（第一个月为 false）。 */
    val isAdaptiveTarget: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val foodRepository: FoodRepository,
    private val calculateDailyCalorieTargetUseCase: CalculateDailyCalorieTargetUseCase,
    private val calculateBodyStatusUseCase: CalculateBodyStatusUseCase,
    private val refreshBodyStatusUseCase: RefreshBodyStatusUseCase,
    private val calculateMacroTargetsUseCase: CalculateMacroTargetsUseCase,
    private val calculateBmrUseCase: CalculateBmrUseCase,
    private val calculateTdeeUseCase: CalculateTdeeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeUserProfile()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 打开页面先看是否到结算日；只有到期（或从未结算过）才会写新结论。
                refreshBodyStatusUseCase()

                combine(
                    todayEpochDayFlow(),
                    userRepository.getBodyStatusSettlement()
                ) { today, settlement -> today to settlement }
                    .flatMapLatest { (today, settlement) ->
                        // 已结算：依据固定取结算窗口，保证展示的数字和结论同源；
                        // 未结算（数据不足）：跟着今天滚动，攒够数据后由上面那次结算接手。
                        val windowEnd = if (settlement.settledDay > 0) settlement.settledDay else today
                        val windowStart = windowEnd - (CalculateBodyStatusUseCase.WINDOW_DAYS - 1)
                        // 引擎状态单独 combine，避免一次 combine 超过 5 个 Flow。
                        val engine = combine(
                            userRepository.getUserProfile(),
                            userRepository.getEstimatedTdee(),
                            userRepository.getCalorieAdjustment(),
                            userRepository.getAdaptiveTargetState(),
                            userRepository.observeLatestWaistLog()
                        ) { profile, estimatedTdee, adjustment, adaptiveState, latestWaistLog ->
                            EngineState(
                                profile = profile,
                                estimatedTdee = estimatedTdee,
                                adjustment = adjustment,
                                isAdaptiveTarget = adaptiveState.isAdaptive,
                                latestWaistCm = latestWaistLog?.waistCm
                            )
                        }
                        combine(
                            engine,
                            userRepository.getWeightLogsBetween(windowStart, windowEnd),
                            foodRepository.getEntriesByDateRange(windowStart, windowEnd),
                            userRepository.observeLatestWaistLogBefore(windowStart),
                            userRepository.observeLatestWaistLogInRange(windowStart, windowEnd)
                        ) { engineState, weightLogs, foodEntries, waistBaseline, waistPeriodEnd ->
                            ProfileData(
                                profile = engineState.profile,
                                weightLogs = weightLogs,
                                foodEntries = foodEntries,
                                waistBaseline = waistBaseline,
                                waistPeriodEnd = waistPeriodEnd,
                                settlement = settlement,
                                estimatedTdee = engineState.estimatedTdee,
                                adjustment = engineState.adjustment,
                                isAdaptiveTarget = engineState.isAdaptiveTarget,
                                latestWaistCm = engineState.latestWaistCm
                            )
                        }
                    }
                    .collect { data ->
                        val displayName = if (data.profile.name.isBlank()) {
                            // 随机昵称生成后立刻落盘。以前只存在内存里，
                            // 每次冷启动都会换一个名字。
                            generateRandomUsername().also { generated ->
                                userRepository.updateUserProfile(data.profile.copy(name = generated))
                            }
                        } else {
                            data.profile.name
                        }

                        // 盈亏判断的参照系：优先用反推的真实消耗，没有就用公式值。
                        // 不能拿「当前热量目标」当参照，目标会被自适应引擎调整，会自证循环。
                        val referenceTdee = data.estimatedTdee ?: formulaTdee(data.profile)

                        val evaluated = calculateBodyStatusUseCase(
                            weightLogs = data.weightLogs,
                            waistBaseline = data.waistBaseline,
                            waistPeriodEnd = data.waistPeriodEnd,
                            foodEntries = data.foodEntries,
                            referenceTdee = referenceTdee
                        )

                        _uiState.value = _uiState.value.copy(
                            userProfile = data.profile.copy(name = displayName),
                            // 已结算就用写死的结论，15 天内不随中间数据变化。
                            bodyStatus = if (data.settlement.settledDay > 0) {
                                evaluated.copy(
                                    status = data.settlement.status,
                                    settledDay = data.settlement.settledDay
                                )
                            } else {
                                evaluated
                            },
                            adjustment = data.adjustment,
                            isAdaptiveTarget = data.isAdaptiveTarget,
                            latestWaistCm = data.latestWaistCm,
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

    private data class ProfileData(
        val profile: UserProfile,
        val weightLogs: List<UserWeightLog>,
        val foodEntries: List<FoodEntry>,
        val waistBaseline: UserWaistLog?,
        val waistPeriodEnd: UserWaistLog?,
        val settlement: BodyStatusSettlement,
        val estimatedTdee: Int?,
        val adjustment: CalorieAdjustment,
        val isAdaptiveTarget: Boolean,
        val latestWaistCm: Float?
    )

    private data class EngineState(
        val profile: UserProfile,
        val estimatedTdee: Int?,
        val adjustment: CalorieAdjustment,
        val isAdaptiveTarget: Boolean,
        val latestWaistCm: Float?
    )

    /** 用当前资料按公式估一个 TDEE，供还没有反推值时兜底当盈亏参照。 */
    private fun formulaTdee(profile: UserProfile): Int {
        val bmr = calculateBmrUseCase(
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            ageYears = profile.age,
            isMale = profile.gender == "male"
        )
        return calculateTdeeUseCase(bmr, profile.activityLevel).roundToInt()
    }

    fun updateUserProfile(
        name: String,
        gender: String,
        height: Float,
        currentWeight: Float,
        waistCm: Float?,
        age: Int,
        goal: String,
        activityLevel: String
    ) {
        viewModelScope.launch {
            try {
                val normalizedName = name.ifBlank { generateRandomUsername() }
                val adaptive = userRepository.getAdaptiveTargetState().first()
                val existingTarget = _uiState.value.userProfile?.dailyCaloricTarget
                    ?: userRepository.getUserProfile().first().dailyCaloricTarget

                // 目标被自适应引擎接管后，保存资料不能再用公式值覆盖它，
                // 否则改个昵称就会把引擎算出来的目标抹掉。
                val targetCalories = if (adaptive.isAdaptive) {
                    existingTarget
                } else {
                    calculateDailyCalorieTargetUseCase(
                        weightKg = currentWeight,
                        heightCm = height,
                        ageYears = age,
                        gender = gender,
                        goal = goal,
                        activityLevel = activityLevel
                    )
                }

                val updatedProfile = UserProfile(
                    name = normalizedName,
                    gender = gender,
                    heightCm = height,
                    weightKg = currentWeight,
                    age = age,
                    goal = goal,
                    activityLevel = ActivityLevel.fromStorage(activityLevel).storageKey,
                    dailyCaloricTarget = targetCalories
                )

                userRepository.updateUserProfile(updatedProfile)
                userRepository.recordWeight(currentWeight)

                // 腰围引导页填完就改不了，这里补一个入口：填了有效值就覆盖今天的记录。
                // 超出合理范围的值不落库，避免污染趋势判断。
                if (waistCm != null && waistCm in MIN_WAIST_CM..MAX_WAIST_CM) {
                    userRepository.recordWaist(waistCm)
                }

                // 营养素目标跟着（可能变化了的）体重与目标重算，保持和热量一致。
                userRepository.saveMacroTargets(
                    calculateMacroTargetsUseCase(
                        weightKg = currentWeight,
                        goal = goal,
                        targetCalories = targetCalories
                    )
                )

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

    companion object {
        /** 腰围合理范围（cm），与引导页的校验保持一致。 */
        private const val MIN_WAIST_CM = 30f
        private const val MAX_WAIST_CM = 200f
    }
}
