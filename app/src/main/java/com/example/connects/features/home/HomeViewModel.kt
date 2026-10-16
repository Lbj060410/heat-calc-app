package com.example.connects.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.core.util.todayEpochDay
import com.example.connects.core.util.todayEpochDayFlow
import com.example.connects.domain.model.FoodCategory
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.FoodItem
import com.example.connects.domain.model.MealType
import com.example.connects.domain.model.MacroTargets
import com.example.connects.domain.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val foodRepository: FoodRepository
) : ViewModel() {

    data class NutrientState(
        val consumed: Float = 0f,
        val target: Float = 0f
    ) {
        val progress: Float get() = if (target > 0) (consumed / target).coerceIn(0f, 1.2f) else 0f
        val isExceeded: Boolean get() = consumed > target
    }

    data class MealSummary(
        val mealType: String,
        val totalCalories: Int,
        val count: Int
    )

    data class HomeUiState(
        val targetCalories: Int = 2000,
        val consumedCalories: Int = 0,
        val userName: String = "",
        val avatarPath: String? = null,
        val userWeight: Float = 60f,
        val todayEntries: List<FoodEntry> = emptyList(),
        val mealSummaries: List<MealSummary> = MealType.ordered.map {
            MealSummary(it.label, 0, 0)
        },
        val carbs: NutrientState = NutrientState(target = 250f),
        val protein: NutrientState = NutrientState(target = 60f),
        val fat: NutrientState = NutrientState(target = 70f),
        val sodium: NutrientState = NutrientState(target = 2300f)
    ) {
        val remainingCalories: Int get() = targetCalories - consumedCalories
    }

    data class QuickFoodItem(
        val id: Long,
        val name: String,
        val category: String,
        val isCustom: Boolean,
        val isFavorite: Boolean,
        val lastUsedAt: Long,
        val caloriesPer100g: Float,
        val proteinPer100g: Float,
        val carbsPer100g: Float,
        val fatPer100g: Float,
        val sodiumPer100g: Float
    )

    data class FoodPickerUiState(
        val mealType: String = MealType.BREAKFAST.label,
        val searchQuery: String = "",
        val selectedCategory: String = FoodCategory.ALL,
        val allFoods: List<QuickFoodItem> = emptyList(),
        val visibleFoods: List<QuickFoodItem> = emptyList(),
        val isLoading: Boolean = true
    )

    data class EditableFoodData(
        val id: Long = 0,
        val name: String = "",
        val category: String = FoodCategory.OTHER,
        val caloriesPer100g: Float = 0f,
        val proteinPer100g: Float = 0f,
        val carbsPer100g: Float = 0f,
        val fatPer100g: Float = 0f,
        val sodiumPer100g: Float = 0f
    )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _foodPickerUiState = MutableStateFlow(FoodPickerUiState())
    val foodPickerUiState: StateFlow<FoodPickerUiState> = _foodPickerUiState.asStateFlow()

    init {
        observeDashboard()
        observeFoodPickerItems()
    }

    fun prepareFoodPicker(mealType: String) {
        _foodPickerUiState.value = _foodPickerUiState.value.copy(
            mealType = MealType.labelOf(mealType),
            searchQuery = "",
            selectedCategory = FoodCategory.ALL
        )
        applyFoodFilters()
    }

    fun onFoodSearchQueryChange(query: String) {
        _foodPickerUiState.value = _foodPickerUiState.value.copy(searchQuery = query)
        applyFoodFilters()
    }

    fun onFoodCategoryChange(category: String) {
        _foodPickerUiState.value = _foodPickerUiState.value.copy(selectedCategory = category)
        applyFoodFilters()
    }

    fun addFoodEntryFromQuickItem(item: QuickFoodItem, amountGrams: Float, mealType: String) {
        if (amountGrams <= 0f) return

        viewModelScope.launch {
            val normalizedMealType = MealType.labelOf(mealType)
            val ratio = amountGrams / 100f
            val totalCalories = item.caloriesPer100g * ratio

            val entry = FoodEntry(
                foodItemId = if (item.id > 0) item.id else null,
                foodName = item.name,
                amountGrams = amountGrams,
                totalCalories = totalCalories,
                totalCarbs = item.carbsPer100g * ratio,
                totalProtein = item.proteinPer100g * ratio,
                totalFat = item.fatPer100g * ratio,
                sodium = item.sodiumPer100g * ratio,
                mealType = normalizedMealType,
                dateEpochDay = todayEpochDay()
            )
            foodRepository.addFoodEntry(entry)
            if (item.id > 0) {
                foodRepository.markFoodUsed(item.id)
            }
        }
    }

    fun addCustomFoodAndEntry(data: AddFoodData, mealType: String) {
        val foodName = decodeGarbledText(data.foodName.trim())
        if (foodName.isEmpty()) return
        if (data.amountGrams <= 0f) return

        viewModelScope.launch {
            val caloriesPer100g = if (data.calories > 0f) {
                data.calories
            } else {
                data.carbs * 4f + data.protein * 4f + data.fat * 9f
            }

            // 同名食物直接复用已有的，不再每次录入都往食物库里插一条重复项
            val foodId = foodRepository.searchFoodItems(foodName).firstOrNull()
                ?.firstOrNull { it.name.equals(foodName, ignoreCase = true) }
                ?.id
                ?: foodRepository.addCustomFoodItem(
                    FoodItem(
                        name = foodName,
                        category = FoodCategory.OTHER,
                        caloriesPer100g = caloriesPer100g,
                        proteinPer100g = data.protein,
                        carbsPer100g = data.carbs,
                        fatPer100g = data.fat,
                        sodiumPer100g = data.sodium,
                        isCustom = true
                    )
                )

            val quick = QuickFoodItem(
                id = foodId,
                name = foodName,
                category = FoodCategory.OTHER,
                isCustom = true,
                isFavorite = false,
                lastUsedAt = System.currentTimeMillis(),
                caloriesPer100g = caloriesPer100g,
                proteinPer100g = data.protein,
                carbsPer100g = data.carbs,
                fatPer100g = data.fat,
                sodiumPer100g = data.sodium
            )
            addFoodEntryFromQuickItem(quick, data.amountGrams, mealType)
        }
    }

    fun toggleFavorite(foodId: Long, currentFavorite: Boolean) {
        if (foodId <= 0) return
        viewModelScope.launch {
            foodRepository.setFoodFavorite(foodId, !currentFavorite)
        }
    }

    fun updateCustomFood(data: EditableFoodData) {
        if (data.id <= 0 || data.name.isBlank()) return
        viewModelScope.launch {
            val origin = foodRepository.getFoodItemById(data.id) ?: return@launch
            if (!origin.isCustom) return@launch

            foodRepository.updateCustomFoodItem(
                origin.copy(
                    name = data.name.trim(),
                    category = data.category,
                    caloriesPer100g = data.caloriesPer100g,
                    proteinPer100g = data.proteinPer100g,
                    carbsPer100g = data.carbsPer100g,
                    fatPer100g = data.fatPer100g,
                    sodiumPer100g = data.sodiumPer100g
                )
            )
        }
    }

    fun deleteCustomFood(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            val food = foodRepository.getFoodItemById(id) ?: return@launch
            if (!food.isCustom) return@launch
            foodRepository.deleteCustomFoodItem(id)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard() {
        viewModelScope.launch {
            todayEpochDayFlow()
                .flatMapLatest { todayEpochDay ->
                    // typed combine 最多 5 个 Flow：用户名和头像先并成一对再进大合并。
                    val nameAndAvatar = combine(
                        userPreferences.userName,
                        userPreferences.avatarUri
                    ) { name, avatar -> name to avatar }

                    combine(
                        userPreferences.dailyCaloricTarget,
                        userPreferences.userWeight,
                        userPreferences.macroTargets,
                        nameAndAvatar,
                        foodRepository.getEntriesByDate(todayEpochDay)
                    ) { targetCalories, userWeight, macroTargets, nameAndAvatar, todayEntries ->
                        buildDashboard(
                            targetCalories = targetCalories,
                            userWeight = userWeight,
                            userName = nameAndAvatar.first,
                            avatarPath = nameAndAvatar.second,
                            macroTargets = macroTargets,
                            todayEntries = todayEntries
                        )
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    private fun buildDashboard(
        targetCalories: Int,
        userWeight: Float,
        userName: String,
        avatarPath: String?,
        macroTargets: MacroTargets?,
        todayEntries: List<FoodEntry>
    ): HomeUiState {
        val consumedCarbs = todayEntries.sumOf { it.totalCarbs.toDouble() }.toFloat()
        val consumedProtein = todayEntries.sumOf { it.totalProtein.toDouble() }.toFloat()
        val consumedFat = todayEntries.sumOf { it.totalFat.toDouble() }.toFloat()
        val consumedSodium = todayEntries.sumOf { it.sodium.toDouble() }.toFloat()
        val consumedCalories = todayEntries.sumOf { it.totalCalories.toDouble() }.toInt()

        // 营养素目标优先用持久化的克数（由自适应引擎按体重算出）。
        // 没有时退回按热量百分比粗估，保证老用户界面不空。
        val targetProtein = macroTargets?.proteinG ?: (targetCalories * 0.2f / 4f)
        val targetCarbs = macroTargets?.carbsG ?: (targetCalories * 0.5f / 4f)
        val targetFat = macroTargets?.fatG ?: (targetCalories * 0.3f / 9f)

        val mealSummaries = MealType.group(todayEntries).map { (meal, mealEntries) ->
            MealSummary(
                mealType = meal.label,
                totalCalories = mealEntries.sumOf { it.totalCalories.toInt() },
                count = mealEntries.size
            )
        }

        return HomeUiState(
            targetCalories = targetCalories,
            consumedCalories = consumedCalories,
            userName = userName,
            avatarPath = avatarPath,
            userWeight = userWeight,
            todayEntries = todayEntries,
            mealSummaries = mealSummaries,
            carbs = NutrientState(consumed = consumedCarbs, target = targetCarbs),
            protein = NutrientState(consumed = consumedProtein, target = targetProtein),
            fat = NutrientState(consumed = consumedFat, target = targetFat),
            sodium = NutrientState(consumed = consumedSodium, target = 2300f)
        )
    }

    private fun observeFoodPickerItems() {
        viewModelScope.launch {
            foodRepository.getAllFoodItems().collect { foods ->
                val all = ensurePresetFoods(foods)
                val quickItems = all.map { it.toQuickItem() }
                _foodPickerUiState.value = _foodPickerUiState.value.copy(
                    allFoods = quickItems,
                    isLoading = false
                )
                applyFoodFilters()
            }
        }
    }

    private suspend fun ensurePresetFoods(existing: List<FoodItem>): List<FoodItem> {
        if (existing.isEmpty()) {
            val presets = presetFoods()
            presets.forEach { foodRepository.addCustomFoodItem(it.copy(isCustom = false)) }
            return foodRepository.getAllFoodItems().firstOrNull().orEmpty()
        }
        // 老用户数据库已有食物：仅补齐本次新增的预置食物。
        // 按名称去重，避免重复插入；对应 MIGRATION_3_4 的同名收敛规则。
        val existingNames = existing.map { it.name.trim() }.toHashSet()
        val missing = presetFoods().filter { it.name.trim() !in existingNames }
        if (missing.isNotEmpty()) {
            missing.forEach { foodRepository.addCustomFoodItem(it.copy(isCustom = false)) }
            return foodRepository.getAllFoodItems().firstOrNull().orEmpty()
        }
        return existing
    }

    private fun applyFoodFilters() {
        val state = _foodPickerUiState.value
        val query = state.searchQuery.trim()

        val source = state.allFoods.sortedWith(
            compareByDescending<HomeViewModel.QuickFoodItem> { it.isFavorite }
                .thenByDescending { it.lastUsedAt }
                .thenBy { it.name }
        )

        val byCategory = when (state.selectedCategory) {
            FoodCategory.ALL -> source
            FoodCategory.CUSTOM -> source.filter { it.isCustom }
            else -> source.filter { it.category == state.selectedCategory }
        }

        val visible = if (query.isBlank()) {
            byCategory
        } else {
            byCategory.filter { it.name.contains(query, ignoreCase = true) }
        }

        _foodPickerUiState.value = state.copy(visibleFoods = visible)
    }

    private fun FoodItem.toQuickItem(idOverride: Long? = null): QuickFoodItem {
        return QuickFoodItem(
            id = idOverride ?: id,
            name = decodeGarbledText(name),
            category = if (isCustom) FoodCategory.CUSTOM else category,
            isCustom = isCustom,
            isFavorite = isFavorite,
            lastUsedAt = lastUsedAt,
            caloriesPer100g = caloriesPer100g,
            proteinPer100g = proteinPer100g,
            carbsPer100g = carbsPer100g,
            fatPer100g = fatPer100g,
            sodiumPer100g = sodiumPer100g
        )
    }

    private fun decodeGarbledText(value: String): String {
        return value
    }

    /**
     * 预置常见食物库。
     *
     * 数值以权威资料《中国食物成分表》及其公开引用数据为准，均按每 100g 可食部计：
     * 能量（kcal）、蛋白质（g）、碳水化合物（g）、脂肪（g）、钠（mg）。
     * 不同来源存在 ±10% 左右偏差，这里取常用均值；预置食物在首次启动或老用户
     * 数据库补齐时按名称去重后写入，不影响用户已有的自定义记录。
     */
    private fun presetFoods(): List<FoodItem> {
        return listOf(
            // ===== 主食类 =====
            FoodItem(name = "米饭", category = FoodCategory.STAPLE, caloriesPer100g = 116f, proteinPer100g = 2.6f, carbsPer100g = 25.9f, fatPer100g = 0.3f, sodiumPer100g = 2.5f),
            FoodItem(name = "馒头", category = FoodCategory.STAPLE, caloriesPer100g = 223f, proteinPer100g = 7f, carbsPer100g = 47f, fatPer100g = 1.1f, sodiumPer100g = 165f),
            FoodItem(name = "面条", category = FoodCategory.STAPLE, caloriesPer100g = 138f, proteinPer100g = 4.5f, carbsPer100g = 27f, fatPer100g = 1.2f, sodiumPer100g = 152f),
            FoodItem(name = "燕麦片", category = FoodCategory.STAPLE, caloriesPer100g = 367f, proteinPer100g = 15f, carbsPer100g = 61f, fatPer100g = 7f, sodiumPer100g = 23f),
            FoodItem(name = "小米粥", category = FoodCategory.STAPLE, caloriesPer100g = 46f, proteinPer100g = 1.4f, carbsPer100g = 8.4f, fatPer100g = 0.7f, sodiumPer100g = 4.3f),
            FoodItem(name = "红薯", category = FoodCategory.STAPLE, caloriesPer100g = 86f, proteinPer100g = 1.6f, carbsPer100g = 20.1f, fatPer100g = 0.2f, sodiumPer100g = 28.6f),
            FoodItem(name = "紫薯", category = FoodCategory.STAPLE, caloriesPer100g = 82f, proteinPer100g = 1.3f, carbsPer100g = 17.6f, fatPer100g = 0.2f, sodiumPer100g = 2.7f),
            FoodItem(name = "土豆", category = FoodCategory.STAPLE, caloriesPer100g = 81f, proteinPer100g = 2.6f, carbsPer100g = 17.5f, fatPer100g = 0.2f, sodiumPer100g = 5.9f),
            FoodItem(name = "玉米(鲜)", category = FoodCategory.STAPLE, caloriesPer100g = 112f, proteinPer100g = 4f, carbsPer100g = 22.8f, fatPer100g = 1.2f, sodiumPer100g = 1.1f),
            FoodItem(name = "全麦面包", category = FoodCategory.STAPLE, caloriesPer100g = 246f, proteinPer100g = 10f, carbsPer100g = 43f, fatPer100g = 4.2f, sodiumPer100g = 420f),
            FoodItem(name = "白面包", category = FoodCategory.STAPLE, caloriesPer100g = 312f, proteinPer100g = 8.3f, carbsPer100g = 58f, fatPer100g = 3.3f, sodiumPer100g = 330f),
            FoodItem(name = "年糕", category = FoodCategory.STAPLE, caloriesPer100g = 156f, proteinPer100g = 3.1f, carbsPer100g = 33f, fatPer100g = 0.6f, sodiumPer100g = 26f),
            FoodItem(name = "粽子", category = FoodCategory.STAPLE, caloriesPer100g = 195f, proteinPer100g = 4.5f, carbsPer100g = 37f, fatPer100g = 3f, sodiumPer100g = 20f),
            FoodItem(name = "汤圆", category = FoodCategory.STAPLE, caloriesPer100g = 247f, proteinPer100g = 5.5f, carbsPer100g = 56f, fatPer100g = 2f, sodiumPer100g = 42f),
            FoodItem(name = "饺子(猪肉馅)", category = FoodCategory.STAPLE, caloriesPer100g = 240f, proteinPer100g = 6.7f, carbsPer100g = 33f, fatPer100g = 9f, sodiumPer100g = 60f),
            FoodItem(name = "藜麦(熟)", category = FoodCategory.STAPLE, caloriesPer100g = 120f, proteinPer100g = 4.4f, carbsPer100g = 21f, fatPer100g = 1.9f, sodiumPer100g = 7f),
            FoodItem(name = "糙米(熟)", category = FoodCategory.STAPLE, caloriesPer100g = 111f, proteinPer100g = 2.6f, carbsPer100g = 23.5f, fatPer100g = 0.9f, sodiumPer100g = 4f),
            FoodItem(name = "荞麦面(熟)", category = FoodCategory.STAPLE, caloriesPer100g = 112f, proteinPer100g = 3f, carbsPer100g = 24f, fatPer100g = 0.5f, sodiumPer100g = 6f),
            FoodItem(name = "糯米饭", category = FoodCategory.STAPLE, caloriesPer100g = 114f, proteinPer100g = 2.2f, carbsPer100g = 25f, fatPer100g = 0.4f, sodiumPer100g = 2f),
            FoodItem(name = "粉丝(干)", category = FoodCategory.STAPLE, caloriesPer100g = 338f, proteinPer100g = 0.8f, carbsPer100g = 83f, fatPer100g = 0.2f, sodiumPer100g = 8f),
            FoodItem(name = "粉条(干)", category = FoodCategory.STAPLE, caloriesPer100g = 337f, proteinPer100g = 0.4f, carbsPer100g = 83.6f, fatPer100g = 0.1f, sodiumPer100g = 6f),
            FoodItem(name = "绿豆(干)", category = FoodCategory.STAPLE, caloriesPer100g = 316f, proteinPer100g = 21.6f, carbsPer100g = 62f, fatPer100g = 0.7f, sodiumPer100g = 3.2f),
            FoodItem(name = "赤小豆(干)", category = FoodCategory.STAPLE, caloriesPer100g = 324f, proteinPer100g = 20.2f, carbsPer100g = 63.4f, fatPer100g = 0.6f, sodiumPer100g = 2.2f),
            FoodItem(name = "莲子(干)", category = FoodCategory.STAPLE, caloriesPer100g = 344f, proteinPer100g = 17.2f, carbsPer100g = 64f, fatPer100g = 2f, sodiumPer100g = 0.4f),
            FoodItem(name = "馒头(蒸)", category = FoodCategory.STAPLE, caloriesPer100g = 223f, proteinPer100g = 7f, carbsPer100g = 47f, fatPer100g = 1.1f, sodiumPer100g = 160f),
            FoodItem(name = "花卷", category = FoodCategory.STAPLE, caloriesPer100g = 217f, proteinPer100g = 6.4f, carbsPer100g = 45.6f, fatPer100g = 1f, sodiumPer100g = 120f),
            FoodItem(name = "包子(猪肉馅)", category = FoodCategory.STAPLE, caloriesPer100g = 227f, proteinPer100g = 7.1f, carbsPer100g = 34.5f, fatPer100g = 8.4f, sodiumPer100g = 400f),
            FoodItem(name = "面条(熟)", category = FoodCategory.STAPLE, caloriesPer100g = 110f, proteinPer100g = 3.6f, carbsPer100g = 22f, fatPer100g = 0.7f, sodiumPer100g = 80f),
            FoodItem(name = "挂面(干)", category = FoodCategory.STAPLE, caloriesPer100g = 346f, proteinPer100g = 10.3f, carbsPer100g = 74f, fatPer100g = 0.7f, sodiumPer100g = 900f),
            FoodItem(name = "米粉(米线熟)", category = FoodCategory.STAPLE, caloriesPer100g = 109f, proteinPer100g = 0.7f, carbsPer100g = 24.5f, fatPer100g = 0.2f, sodiumPer100g = 5f),
            FoodItem(name = "油条", category = FoodCategory.STAPLE, caloriesPer100g = 388f, proteinPer100g = 6.9f, carbsPer100g = 51f, fatPer100g = 17.6f, sodiumPer100g = 385f),
            FoodItem(name = "烧饼", category = FoodCategory.STAPLE, caloriesPer100g = 254f, proteinPer100g = 7f, carbsPer100g = 45f, fatPer100g = 6.3f, sodiumPer100g = 40f),
            FoodItem(name = "馄饨(鲜肉)", category = FoodCategory.STAPLE, caloriesPer100g = 120f, proteinPer100g = 5.5f, carbsPer100g = 18f, fatPer100g = 3f, sodiumPer100g = 300f),

            // ===== 蔬菜类 =====
            FoodItem(name = "西兰花", category = FoodCategory.VEGETABLE, caloriesPer100g = 34f, proteinPer100g = 2.8f, carbsPer100g = 6.6f, fatPer100g = 0.4f, sodiumPer100g = 18.8f),
            FoodItem(name = "菠菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.6f, carbsPer100g = 3.6f, fatPer100g = 0.3f, sodiumPer100g = 85.2f),
            FoodItem(name = "番茄", category = FoodCategory.VEGETABLE, caloriesPer100g = 19f, proteinPer100g = 0.9f, carbsPer100g = 4f, fatPer100g = 0.2f, sodiumPer100g = 5f),
            FoodItem(name = "大白菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 1.5f, carbsPer100g = 3.6f, fatPer100g = 0.1f, sodiumPer100g = 60.6f),
            FoodItem(name = "油菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 26f, proteinPer100g = 1.8f, carbsPer100g = 3.8f, fatPer100g = 0.3f, sodiumPer100g = 55.8f),
            FoodItem(name = "胡萝卜", category = FoodCategory.VEGETABLE, caloriesPer100g = 39f, proteinPer100g = 1f, carbsPer100g = 8.8f, fatPer100g = 0.2f, sodiumPer100g = 71.4f),
            FoodItem(name = "黄瓜", category = FoodCategory.VEGETABLE, caloriesPer100g = 16f, proteinPer100g = 0.8f, carbsPer100g = 2.9f, fatPer100g = 0.2f, sodiumPer100g = 4.9f),
            FoodItem(name = "芹菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 22f, proteinPer100g = 1.2f, carbsPer100g = 4.5f, fatPer100g = 0.2f, sodiumPer100g = 159f),
            FoodItem(name = "茄子", category = FoodCategory.VEGETABLE, caloriesPer100g = 23f, proteinPer100g = 1.1f, carbsPer100g = 4.9f, fatPer100g = 0.2f, sodiumPer100g = 5.4f),
            FoodItem(name = "生菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 15f, proteinPer100g = 1.1f, carbsPer100g = 2.1f, fatPer100g = 0.4f, sodiumPer100g = 32.8f),
            FoodItem(name = "青椒", category = FoodCategory.VEGETABLE, caloriesPer100g = 22f, proteinPer100g = 1.4f, carbsPer100g = 5.4f, fatPer100g = 0.3f, sodiumPer100g = 3.3f),
            FoodItem(name = "南瓜", category = FoodCategory.VEGETABLE, caloriesPer100g = 22f, proteinPer100g = 0.7f, carbsPer100g = 5.3f, fatPer100g = 0.1f, sodiumPer100g = 0.8f),
            FoodItem(name = "洋葱", category = FoodCategory.VEGETABLE, caloriesPer100g = 40f, proteinPer100g = 1.1f, carbsPer100g = 9f, fatPer100g = 0.2f, sodiumPer100g = 4.4f),
            FoodItem(name = "四季豆", category = FoodCategory.VEGETABLE, caloriesPer100g = 31f, proteinPer100g = 2f, carbsPer100g = 5.7f, fatPer100g = 0.2f, sodiumPer100g = 4.6f),
            FoodItem(name = "香菇", category = FoodCategory.VEGETABLE, caloriesPer100g = 26f, proteinPer100g = 2.2f, carbsPer100g = 5.2f, fatPer100g = 0.3f, sodiumPer100g = 1.4f),
            FoodItem(name = "木耳(水发)", category = FoodCategory.VEGETABLE, caloriesPer100g = 27f, proteinPer100g = 1.5f, carbsPer100g = 6f, fatPer100g = 0.2f, sodiumPer100g = 8.5f),
            FoodItem(name = "山药", category = FoodCategory.VEGETABLE, caloriesPer100g = 57f, proteinPer100g = 1.5f, carbsPer100g = 12.4f, fatPer100g = 0.2f, sodiumPer100g = 18.6f),
            FoodItem(name = "冬瓜", category = FoodCategory.VEGETABLE, caloriesPer100g = 12f, proteinPer100g = 0.4f, carbsPer100g = 2.6f, fatPer100g = 0.2f, sodiumPer100g = 1.8f),
            FoodItem(name = "韭菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 25f, proteinPer100g = 2.4f, carbsPer100g = 4.6f, fatPer100g = 0.4f, sodiumPer100g = 8.1f),
            FoodItem(name = "空心菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 2f, carbsPer100g = 3.4f, fatPer100g = 0.3f, sodiumPer100g = 14.6f),
            FoodItem(name = "小白菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 17f, proteinPer100g = 1.5f, carbsPer100g = 2.7f, fatPer100g = 0.3f, sodiumPer100g = 57.5f),
            FoodItem(name = "芥蓝", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.4f, carbsPer100g = 4.5f, fatPer100g = 0.3f, sodiumPer100g = 8f),
            FoodItem(name = "芦笋", category = FoodCategory.VEGETABLE, caloriesPer100g = 19f, proteinPer100g = 1.2f, carbsPer100g = 3.7f, fatPer100g = 0.1f, sodiumPer100g = 6f),
            FoodItem(name = "苦瓜", category = FoodCategory.VEGETABLE, caloriesPer100g = 22f, proteinPer100g = 1f, carbsPer100g = 4.9f, fatPer100g = 0.1f, sodiumPer100g = 2.5f),
            FoodItem(name = "丝瓜", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 1f, carbsPer100g = 4.2f, fatPer100g = 0.2f, sodiumPer100g = 2.5f),
            FoodItem(name = "西葫芦", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 0.9f, carbsPer100g = 4.9f, fatPer100g = 0.1f, sodiumPer100g = 5f),
            FoodItem(name = "莲藕", category = FoodCategory.VEGETABLE, caloriesPer100g = 74f, proteinPer100g = 1.8f, carbsPer100g = 17.2f, fatPer100g = 0.2f, sodiumPer100g = 44.2f),
            FoodItem(name = "莴笋", category = FoodCategory.VEGETABLE, caloriesPer100g = 14f, proteinPer100g = 1f, carbsPer100g = 2.8f, fatPer100g = 0.1f, sodiumPer100g = 36.5f),
            FoodItem(name = "菜花(花椰菜)", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.1f, carbsPer100g = 4.7f, fatPer100g = 0.2f, sodiumPer100g = 31.6f),
            FoodItem(name = "圆白菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 1.2f, carbsPer100g = 4.6f, fatPer100g = 0.2f, sodiumPer100g = 31.6f),
            FoodItem(name = "香菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 33f, proteinPer100g = 1.8f, carbsPer100g = 6.2f, fatPer100g = 0.4f, sodiumPer100g = 48.5f),
            FoodItem(name = "大蒜", category = FoodCategory.VEGETABLE, caloriesPer100g = 128f, proteinPer100g = 4.5f, carbsPer100g = 27.6f, fatPer100g = 0.2f, sodiumPer100g = 19.6f),
            FoodItem(name = "姜", category = FoodCategory.VEGETABLE, caloriesPer100g = 46f, proteinPer100g = 1.3f, carbsPer100g = 10.3f, fatPer100g = 0.6f, sodiumPer100g = 14.9f),
            FoodItem(name = "大葱", category = FoodCategory.VEGETABLE, caloriesPer100g = 33f, proteinPer100g = 1.7f, carbsPer100g = 6.5f, fatPer100g = 0.3f, sodiumPer100g = 17.8f),
            FoodItem(name = "豌豆(鲜)", category = FoodCategory.VEGETABLE, caloriesPer100g = 105f, proteinPer100g = 7.4f, carbsPer100g = 21.2f, fatPer100g = 0.3f, sodiumPer100g = 9.7f),
            FoodItem(name = "绿豆芽", category = FoodCategory.VEGETABLE, caloriesPer100g = 18f, proteinPer100g = 2.1f, carbsPer100g = 2.9f, fatPer100g = 0.1f, sodiumPer100g = 4.4f),
            FoodItem(name = "黄豆芽", category = FoodCategory.VEGETABLE, caloriesPer100g = 47f, proteinPer100g = 4.5f, carbsPer100g = 5.6f, fatPer100g = 1.6f, sodiumPer100g = 7.2f),
            FoodItem(name = "荷兰豆", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.5f, carbsPer100g = 4.3f, fatPer100g = 0.3f, sodiumPer100g = 8.8f),
            FoodItem(name = "小青菜(上海青)", category = FoodCategory.VEGETABLE, caloriesPer100g = 15f, proteinPer100g = 1.5f, carbsPer100g = 2.8f, fatPer100g = 0.3f, sodiumPer100g = 73.5f),
            FoodItem(name = "娃娃菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 13f, proteinPer100g = 1.5f, carbsPer100g = 2.4f, fatPer100g = 0.2f, sodiumPer100g = 20f),
            FoodItem(name = "油麦菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 15f, proteinPer100g = 1.1f, carbsPer100g = 3.9f, fatPer100g = 0.3f, sodiumPer100g = 80f),
            FoodItem(name = "生菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 12f, proteinPer100g = 0.6f, carbsPer100g = 2f, fatPer100g = 0.2f, sodiumPer100g = 48f),
            FoodItem(name = "紫甘蓝", category = FoodCategory.VEGETABLE, caloriesPer100g = 30f, proteinPer100g = 1.4f, carbsPer100g = 6.1f, fatPer100g = 0.2f, sodiumPer100g = 30f),
            FoodItem(name = "苋菜(红)", category = FoodCategory.VEGETABLE, caloriesPer100g = 44f, proteinPer100g = 2.8f, carbsPer100g = 7f, fatPer100g = 0.3f, sodiumPer100g = 42f),
            FoodItem(name = "茼蒿", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 1.9f, carbsPer100g = 3.9f, fatPer100g = 0.3f, sodiumPer100g = 161f),
            FoodItem(name = "木耳菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 1.6f, carbsPer100g = 4.3f, fatPer100g = 0.3f, sodiumPer100g = 47f),
            FoodItem(name = "荠菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 27f, proteinPer100g = 2.9f, carbsPer100g = 4.7f, fatPer100g = 0.4f, sodiumPer100g = 42f),
            FoodItem(name = "芥菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 28f, proteinPer100g = 2.5f, carbsPer100g = 4.5f, fatPer100g = 0.2f, sodiumPer100g = 45f),
            FoodItem(name = "佛手瓜", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 1.2f, carbsPer100g = 4.2f, fatPer100g = 0.1f, sodiumPer100g = 2f),
            FoodItem(name = "扁豆", category = FoodCategory.VEGETABLE, caloriesPer100g = 37f, proteinPer100g = 2.7f, carbsPer100g = 6.8f, fatPer100g = 0.2f, sodiumPer100g = 4f),
            FoodItem(name = "豇豆", category = FoodCategory.VEGETABLE, caloriesPer100g = 32f, proteinPer100g = 2.5f, carbsPer100g = 5.9f, fatPer100g = 0.2f, sodiumPer100g = 2f),
            FoodItem(name = "毛豆(鲜)", category = FoodCategory.VEGETABLE, caloriesPer100g = 123f, proteinPer100g = 13.1f, carbsPer100g = 10.5f, fatPer100g = 5f, sodiumPer100g = 3.9f),
            FoodItem(name = "秋葵", category = FoodCategory.VEGETABLE, caloriesPer100g = 45f, proteinPer100g = 2.5f, carbsPer100g = 6.6f, fatPer100g = 0.6f, sodiumPer100g = 4f),
            FoodItem(name = "马蹄(荸荠)", category = FoodCategory.VEGETABLE, caloriesPer100g = 59f, proteinPer100g = 1.2f, carbsPer100g = 14.2f, fatPer100g = 0.2f, sodiumPer100g = 15.9f),
            FoodItem(name = "冬笋", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.1f, carbsPer100g = 4.7f, fatPer100g = 0.3f, sodiumPer100g = 5f),
            FoodItem(name = "金针菇", category = FoodCategory.VEGETABLE, caloriesPer100g = 32f, proteinPer100g = 1.7f, carbsPer100g = 6.1f, fatPer100g = 0.3f, sodiumPer100g = 4.3f),
            FoodItem(name = "杏鲍菇", category = FoodCategory.VEGETABLE, caloriesPer100g = 35f, proteinPer100g = 1.4f, carbsPer100g = 8f, fatPer100g = 0.2f, sodiumPer100g = 8f),
            FoodItem(name = "平菇", category = FoodCategory.VEGETABLE, caloriesPer100g = 20f, proteinPer100g = 1.7f, carbsPer100g = 4.2f, fatPer100g = 0.3f, sodiumPer100g = 5f),
            FoodItem(name = "彩椒(甜椒)", category = FoodCategory.VEGETABLE, caloriesPer100g = 22f, proteinPer100g = 1f, carbsPer100g = 5.4f, fatPer100g = 0.2f, sodiumPer100g = 3.3f),
            FoodItem(name = "海带(鲜)", category = FoodCategory.VEGETABLE, caloriesPer100g = 13f, proteinPer100g = 1.2f, carbsPer100g = 2.1f, fatPer100g = 0.1f, sodiumPer100g = 484f),
            FoodItem(name = "紫菜(干)", category = FoodCategory.VEGETABLE, caloriesPer100g = 250f, proteinPer100g = 29.4f, carbsPer100g = 44.1f, fatPer100g = 1.1f, sodiumPer100g = 430f),
            FoodItem(name = "木耳(干)", category = FoodCategory.VEGETABLE, caloriesPer100g = 265f, proteinPer100g = 12.1f, carbsPer100g = 65.6f, fatPer100g = 1.5f, sodiumPer100g = 48.5f),
            FoodItem(name = "银耳(干)", category = FoodCategory.VEGETABLE, caloriesPer100g = 261f, proteinPer100g = 10f, carbsPer100g = 67.3f, fatPer100g = 1.4f, sodiumPer100g = 7f),
            FoodItem(name = "香菇(干)", category = FoodCategory.VEGETABLE, caloriesPer100g = 244f, proteinPer100g = 20f, carbsPer100g = 61.7f, fatPer100g = 1.8f, sodiumPer100g = 16f),

            // ===== 肉/蛋/奶类 =====
            FoodItem(name = "鸡胸肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 133f, proteinPer100g = 19.4f, carbsPer100g = 0f, fatPer100g = 5f, sodiumPer100g = 34.4f),
            FoodItem(name = "鸡蛋", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 139f, proteinPer100g = 13.3f, carbsPer100g = 2.8f, fatPer100g = 8.8f, sodiumPer100g = 131.5f),
            FoodItem(name = "纯牛奶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 62f, proteinPer100g = 3.2f, carbsPer100g = 4.8f, fatPer100g = 3.5f, sodiumPer100g = 44f),
            FoodItem(name = "酸奶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 72f, proteinPer100g = 2.5f, carbsPer100g = 9.3f, fatPer100g = 3.1f, sodiumPer100g = 39f),
            FoodItem(name = "瘦猪肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 143f, proteinPer100g = 20f, carbsPer100g = 0f, fatPer100g = 6.2f, sodiumPer100g = 57.5f),
            FoodItem(name = "猪里脊", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 130f, proteinPer100g = 20.4f, carbsPer100g = 0f, fatPer100g = 4.4f, sodiumPer100g = 55f),
            FoodItem(name = "猪五花肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 568f, proteinPer100g = 7.7f, carbsPer100g = 0f, fatPer100g = 59f, sodiumPer100g = 30f),
            FoodItem(name = "猪排骨", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 278f, proteinPer100g = 16.7f, carbsPer100g = 0f, fatPer100g = 23.1f, sodiumPer100g = 65f),
            FoodItem(name = "瘦牛肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 125f, proteinPer100g = 20f, carbsPer100g = 0f, fatPer100g = 4f, sodiumPer100g = 84f),
            FoodItem(name = "瘦羊肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 118f, proteinPer100g = 20.5f, carbsPer100g = 0f, fatPer100g = 3.9f, sodiumPer100g = 69f),
            FoodItem(name = "鸡腿", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 168f, proteinPer100g = 17.9f, carbsPer100g = 0.8f, fatPer100g = 9.4f, sodiumPer100g = 55f),
            FoodItem(name = "鸡翅", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 194f, proteinPer100g = 17.4f, carbsPer100g = 0f, fatPer100g = 11.8f, sodiumPer100g = 60f),
            FoodItem(name = "鸭肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 240f, proteinPer100g = 15.5f, carbsPer100g = 0.6f, fatPer100g = 19f, sodiumPer100g = 70f),
            FoodItem(name = "三文鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 139f, proteinPer100g = 17.2f, carbsPer100g = 0f, fatPer100g = 7.8f, sodiumPer100g = 63f),
            FoodItem(name = "带鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 127f, proteinPer100g = 17.7f, carbsPer100g = 3.1f, fatPer100g = 4.9f, sodiumPer100g = 74.8f),
            FoodItem(name = "虾仁", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 87f, proteinPer100g = 16.6f, carbsPer100g = 2.4f, fatPer100g = 0.7f, sodiumPer100g = 165f),
            FoodItem(name = "南豆腐", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 60f, proteinPer100g = 6.2f, carbsPer100g = 2.6f, fatPer100g = 2.5f, sodiumPer100g = 7.2f),
            FoodItem(name = "豆腐干", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 142f, proteinPer100g = 16.2f, carbsPer100g = 4.5f, fatPer100g = 5.1f, sodiumPer100g = 164f),
            FoodItem(name = "豆浆(无糖)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 31f, proteinPer100g = 3f, carbsPer100g = 1.2f, fatPer100g = 1.6f, sodiumPer100g = 3.7f),
            FoodItem(name = "奶酪", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 328f, proteinPer100g = 25.7f, carbsPer100g = 4.4f, fatPer100g = 23.5f, sodiumPer100g = 584f),
            FoodItem(name = "培根", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 437f, proteinPer100g = 22f, carbsPer100g = 0f, fatPer100g = 37f, sodiumPer100g = 905f),
            FoodItem(name = "火腿肠", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 212f, proteinPer100g = 14f, carbsPer100g = 8.6f, fatPer100g = 14f, sodiumPer100g = 1200f),
            FoodItem(name = "牛腩", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 332f, proteinPer100g = 17.1f, carbsPer100g = 0f, fatPer100g = 29.3f, sodiumPer100g = 67f),
            FoodItem(name = "牛腱子", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 104f, proteinPer100g = 20.2f, carbsPer100g = 1.2f, fatPer100g = 1.9f, sodiumPer100g = 67f),
            FoodItem(name = "猪肝", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 129f, proteinPer100g = 19.3f, carbsPer100g = 5f, fatPer100g = 3.5f, sodiumPer100g = 68.6f),
            FoodItem(name = "牛百叶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 72f, proteinPer100g = 13.2f, carbsPer100g = 0f, fatPer100g = 2f, sodiumPer100g = 60f),
            FoodItem(name = "鸡心", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 172f, proteinPer100g = 13f, carbsPer100g = 0f, fatPer100g = 12.5f, sodiumPer100g = 80f),
            FoodItem(name = "鹌鹑蛋", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 160f, proteinPer100g = 12.8f, carbsPer100g = 2.1f, fatPer100g = 11.1f, sodiumPer100g = 106f),
            FoodItem(name = "基围虾", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 101f, proteinPer100g = 18.2f, carbsPer100g = 2.8f, fatPer100g = 1.4f, sodiumPer100g = 172f),
            FoodItem(name = "螃蟹", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 103f, proteinPer100g = 17.5f, carbsPer100g = 2.3f, fatPer100g = 2.6f, sodiumPer100g = 260f),
            FoodItem(name = "蛤蜊", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 62f, proteinPer100g = 10.1f, carbsPer100g = 2.8f, fatPer100g = 1.1f, sodiumPer100g = 425f),
            FoodItem(name = "牡蛎(生蚝)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 73f, proteinPer100g = 5.3f, carbsPer100g = 8.2f, fatPer100g = 2.1f, sodiumPer100g = 462f),
            FoodItem(name = "鱿鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 75f, proteinPer100g = 17f, carbsPer100g = 0f, fatPer100g = 1f, sodiumPer100g = 110f),
            FoodItem(name = "鳕鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 88f, proteinPer100g = 18f, carbsPer100g = 0.5f, fatPer100g = 0.5f, sodiumPer100g = 130f),
            FoodItem(name = "鲈鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 105f, proteinPer100g = 18.6f, carbsPer100g = 0f, fatPer100g = 3.4f, sodiumPer100g = 144f),
            FoodItem(name = "草鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 113f, proteinPer100g = 16.6f, carbsPer100g = 0f, fatPer100g = 5.2f, sodiumPer100g = 46f),
            FoodItem(name = "鲫鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 108f, proteinPer100g = 17.1f, carbsPer100g = 3.8f, fatPer100g = 2.7f, sodiumPer100g = 41.2f),
            FoodItem(name = "猪蹄", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 260f, proteinPer100g = 22.6f, carbsPer100g = 0f, fatPer100g = 18.8f, sodiumPer100g = 101f),
            FoodItem(name = "猪肚", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 110f, proteinPer100g = 15.2f, carbsPer100g = 0f, fatPer100g = 5.1f, sodiumPer100g = 70f),
            FoodItem(name = "牛里脊", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 107f, proteinPer100g = 22.2f, carbsPer100g = 0.9f, fatPer100g = 1.5f, sodiumPer100g = 53.6f),
            FoodItem(name = "羊肉卷(肥瘦)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 203f, proteinPer100g = 14.3f, carbsPer100g = 0f, fatPer100g = 16f, sodiumPer100g = 70f),
            FoodItem(name = "羊腿", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 90f, proteinPer100g = 18.4f, carbsPer100g = 0f, fatPer100g = 1.6f, sodiumPer100g = 70f),
            FoodItem(name = "鸡爪", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 254f, proteinPer100g = 23.9f, carbsPer100g = 0f, fatPer100g = 16.4f, sodiumPer100g = 210f),
            FoodItem(name = "鹅", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 251f, proteinPer100g = 16.8f, carbsPer100g = 0f, fatPer100g = 19f, sodiumPer100g = 55f),
            FoodItem(name = "鸭蛋", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 180f, proteinPer100g = 12.6f, carbsPer100g = 0.9f, fatPer100g = 13f, sodiumPer100g = 106f),
            FoodItem(name = "皮蛋(松花蛋)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 171f, proteinPer100g = 14.2f, carbsPer100g = 1.4f, fatPer100g = 11.3f, sodiumPer100g = 587f),
            FoodItem(name = "黄花鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 97f, proteinPer100g = 17.7f, carbsPer100g = 0.9f, fatPer100g = 2.5f, sodiumPer100g = 103f),
            FoodItem(name = "鲳鱼(平鱼)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 164f, proteinPer100g = 17.5f, carbsPer100g = 0f, fatPer100g = 10.2f, sodiumPer100g = 80f),
            FoodItem(name = "罗非鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 98f, proteinPer100g = 18.4f, carbsPer100g = 1.1f, fatPer100g = 2f, sodiumPer100g = 72f),
            FoodItem(name = "鲤鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 109f, proteinPer100g = 17.6f, carbsPer100g = 0f, fatPer100g = 3.8f, sodiumPer100g = 53.7f),
            FoodItem(name = "鳙鱼(胖头鱼)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 100f, proteinPer100g = 15.3f, carbsPer100g = 0f, fatPer100g = 4f, sodiumPer100g = 70f),
            FoodItem(name = "鳗鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 181f, proteinPer100g = 18.6f, carbsPer100g = 0f, fatPer100g = 11f, sodiumPer100g = 60f),
            FoodItem(name = "小龙虾", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 93f, proteinPer100g = 16.8f, carbsPer100g = 1.6f, fatPer100g = 2.1f, sodiumPer100g = 126f),
            FoodItem(name = "扇贝", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 60f, proteinPer100g = 11f, carbsPer100g = 2.1f, fatPer100g = 0.8f, sodiumPer100g = 195f),
            FoodItem(name = "鲍鱼", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 94f, proteinPer100g = 12f, carbsPer100g = 4.4f, fatPer100g = 0.7f, sodiumPer100g = 200f),
            FoodItem(name = "黄豆(干)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 359f, proteinPer100g = 35f, carbsPer100g = 34.2f, fatPer100g = 16f, sodiumPer100g = 2.2f),
            FoodItem(name = "老豆腐(北豆腐)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 82f, proteinPer100g = 8.2f, carbsPer100g = 3f, fatPer100g = 4.2f, sodiumPer100g = 7.2f),
            FoodItem(name = "嫩豆腐(南豆腐)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 57f, proteinPer100g = 5.7f, carbsPer100g = 2.4f, fatPer100g = 3.2f, sodiumPer100g = 12f),
            FoodItem(name = "豆腐脑", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 15f, proteinPer100g = 1.9f, carbsPer100g = 0f, fatPer100g = 0.8f, sodiumPer100g = 6f),
            FoodItem(name = "香干", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 140f, proteinPer100g = 16f, carbsPer100g = 4.8f, fatPer100g = 6f, sodiumPer100g = 200f),
            FoodItem(name = "腐竹(干)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 459f, proteinPer100g = 44.6f, carbsPer100g = 26.5f, fatPer100g = 21.7f, sodiumPer100g = 23.3f),
            FoodItem(name = "豆皮(干)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 386f, proteinPer100g = 44f, carbsPer100g = 20.8f, fatPer100g = 15.7f, sodiumPer100g = 20f),
            FoodItem(name = "千张(百叶)", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 260f, proteinPer100g = 24f, carbsPer100g = 5.6f, fatPer100g = 16f, sodiumPer100g = 40f),
            FoodItem(name = "素鸡", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 194f, proteinPer100g = 14f, carbsPer100g = 4.9f, fatPer100g = 13f, sodiumPer100g = 50f),
            FoodItem(name = "油豆腐", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 244f, proteinPer100g = 17f, carbsPer100g = 4.9f, fatPer100g = 17.4f, sodiumPer100g = 120f),

            // ===== 水果类 =====
            FoodItem(name = "苹果", category = FoodCategory.FRUIT, caloriesPer100g = 53f, proteinPer100g = 0.3f, carbsPer100g = 13.5f, fatPer100g = 0.2f, sodiumPer100g = 1.6f),
            FoodItem(name = "香蕉", category = FoodCategory.FRUIT, caloriesPer100g = 93f, proteinPer100g = 1.4f, carbsPer100g = 22f, fatPer100g = 0.2f, sodiumPer100g = 0.8f),
            FoodItem(name = "橙子", category = FoodCategory.FRUIT, caloriesPer100g = 48f, proteinPer100g = 0.8f, carbsPer100g = 11.1f, fatPer100g = 0.2f, sodiumPer100g = 1.2f),
            FoodItem(name = "草莓", category = FoodCategory.FRUIT, caloriesPer100g = 32f, proteinPer100g = 1f, carbsPer100g = 7.1f, fatPer100g = 0.2f, sodiumPer100g = 4.2f),
            FoodItem(name = "葡萄", category = FoodCategory.FRUIT, caloriesPer100g = 45f, proteinPer100g = 0.4f, carbsPer100g = 10.3f, fatPer100g = 0.4f, sodiumPer100g = 1.3f),
            FoodItem(name = "桃子", category = FoodCategory.FRUIT, caloriesPer100g = 42f, proteinPer100g = 0.6f, carbsPer100g = 10.1f, fatPer100g = 0.1f, sodiumPer100g = 5.7f),
            FoodItem(name = "猕猴桃", category = FoodCategory.FRUIT, caloriesPer100g = 61f, proteinPer100g = 0.8f, carbsPer100g = 14.5f, fatPer100g = 0.6f, sodiumPer100g = 10f),
            FoodItem(name = "西瓜", category = FoodCategory.FRUIT, caloriesPer100g = 26f, proteinPer100g = 0.5f, carbsPer100g = 6.1f, fatPer100g = 0.1f, sodiumPer100g = 3.1f),
            FoodItem(name = "梨", category = FoodCategory.FRUIT, caloriesPer100g = 45f, proteinPer100g = 0.3f, carbsPer100g = 13.1f, fatPer100g = 0.1f, sodiumPer100g = 2.1f),
            FoodItem(name = "榴莲", category = FoodCategory.FRUIT, caloriesPer100g = 150f, proteinPer100g = 2.6f, carbsPer100g = 28.3f, fatPer100g = 3.3f, sodiumPer100g = 25f),
            FoodItem(name = "蓝莓", category = FoodCategory.FRUIT, caloriesPer100g = 57f, proteinPer100g = 0.7f, carbsPer100g = 14.5f, fatPer100g = 0.3f, sodiumPer100g = 1f),
            FoodItem(name = "火龙果", category = FoodCategory.FRUIT, caloriesPer100g = 55f, proteinPer100g = 1.1f, carbsPer100g = 13.3f, fatPer100g = 0.2f, sodiumPer100g = 2.7f),
            FoodItem(name = "芒果", category = FoodCategory.FRUIT, caloriesPer100g = 60f, proteinPer100g = 0.8f, carbsPer100g = 15f, fatPer100g = 0.2f, sodiumPer100g = 9.3f),
            FoodItem(name = "樱桃", category = FoodCategory.FRUIT, caloriesPer100g = 46f, proteinPer100g = 1.1f, carbsPer100g = 10.2f, fatPer100g = 0.2f, sodiumPer100g = 6f),
            FoodItem(name = "菠萝", category = FoodCategory.FRUIT, caloriesPer100g = 44f, proteinPer100g = 0.5f, carbsPer100g = 10.8f, fatPer100g = 0.1f, sodiumPer100g = 6.3f),
            FoodItem(name = "圣女果", category = FoodCategory.FRUIT, caloriesPer100g = 25f, proteinPer100g = 1f, carbsPer100g = 5.8f, fatPer100g = 0.2f, sodiumPer100g = 5f),
            FoodItem(name = "牛油果", category = FoodCategory.FRUIT, caloriesPer100g = 160f, proteinPer100g = 2f, carbsPer100g = 5.4f, fatPer100g = 15.3f, sodiumPer100g = 10f),
            FoodItem(name = "荔枝", category = FoodCategory.FRUIT, caloriesPer100g = 71f, proteinPer100g = 0.9f, carbsPer100g = 16.6f, fatPer100g = 0.2f, sodiumPer100g = 1.5f),
            FoodItem(name = "柚子", category = FoodCategory.FRUIT, caloriesPer100g = 42f, proteinPer100g = 0.8f, carbsPer100g = 9.5f, fatPer100g = 0.2f, sodiumPer100g = 3f),
            FoodItem(name = "李子", category = FoodCategory.FRUIT, caloriesPer100g = 38f, proteinPer100g = 0.7f, carbsPer100g = 8.7f, fatPer100g = 0.2f, sodiumPer100g = 3.8f),
            FoodItem(name = "枇杷", category = FoodCategory.FRUIT, caloriesPer100g = 41f, proteinPer100g = 0.8f, carbsPer100g = 9.3f, fatPer100g = 0.2f, sodiumPer100g = 4f),
            FoodItem(name = "石榴", category = FoodCategory.FRUIT, caloriesPer100g = 72f, proteinPer100g = 1.4f, carbsPer100g = 18.1f, fatPer100g = 0.2f, sodiumPer100g = 1.3f),
            FoodItem(name = "红枣(干)", category = FoodCategory.FRUIT, caloriesPer100g = 264f, proteinPer100g = 3.2f, carbsPer100g = 67.8f, fatPer100g = 0.5f, sodiumPer100g = 6.2f),
            FoodItem(name = "哈密瓜", category = FoodCategory.FRUIT, caloriesPer100g = 34f, proteinPer100g = 0.5f, carbsPer100g = 7.7f, fatPer100g = 0.1f, sodiumPer100g = 26f),
            FoodItem(name = "杨梅", category = FoodCategory.FRUIT, caloriesPer100g = 28f, proteinPer100g = 0.8f, carbsPer100g = 6.7f, fatPer100g = 0.2f, sodiumPer100g = 2f),
            FoodItem(name = "无花果", category = FoodCategory.FRUIT, caloriesPer100g = 59f, proteinPer100g = 1.5f, carbsPer100g = 13f, fatPer100g = 0.3f, sodiumPer100g = 3f),
            FoodItem(name = "桑葚", category = FoodCategory.FRUIT, caloriesPer100g = 49f, proteinPer100g = 1.7f, carbsPer100g = 13.8f, fatPer100g = 0.4f, sodiumPer100g = 2f),
            FoodItem(name = "甘蔗", category = FoodCategory.FRUIT, caloriesPer100g = 55f, proteinPer100g = 0.4f, carbsPer100g = 13.8f, fatPer100g = 0.1f, sodiumPer100g = 2f),

            // ===== 零食类 =====
            FoodItem(name = "饼干", category = FoodCategory.SNACK, caloriesPer100g = 433f, proteinPer100g = 6.6f, carbsPer100g = 71.7f, fatPer100g = 13.6f, sodiumPer100g = 204f),
            FoodItem(name = "薯片", category = FoodCategory.SNACK, caloriesPer100g = 548f, proteinPer100g = 7.4f, carbsPer100g = 50.3f, fatPer100g = 35.3f, sodiumPer100g = 385f),
            FoodItem(name = "黑巧克力", category = FoodCategory.SNACK, caloriesPer100g = 546f, proteinPer100g = 4.9f, carbsPer100g = 61f, fatPer100g = 31f, sodiumPer100g = 20f),
            FoodItem(name = "威化饼干", category = FoodCategory.SNACK, caloriesPer100g = 505f, proteinPer100g = 5.4f, carbsPer100g = 70.3f, fatPer100g = 25.4f, sodiumPer100g = 260f),
            FoodItem(name = "蛋糕", category = FoodCategory.SNACK, caloriesPer100g = 320f, proteinPer100g = 8.6f, carbsPer100g = 45.7f, fatPer100g = 12.7f, sodiumPer100g = 240f),
            FoodItem(name = "冰淇淋", category = FoodCategory.SNACK, caloriesPer100g = 127f, proteinPer100g = 2.4f, carbsPer100g = 17.3f, fatPer100g = 5.3f, sodiumPer100g = 54.2f),
            FoodItem(name = "牛奶巧克力", category = FoodCategory.SNACK, caloriesPer100g = 554f, proteinPer100g = 4.3f, carbsPer100g = 59f, fatPer100g = 30f, sodiumPer100g = 50f),
            FoodItem(name = "核桃", category = FoodCategory.SNACK, caloriesPer100g = 654f, proteinPer100g = 15.2f, carbsPer100g = 19.1f, fatPer100g = 57.9f, sodiumPer100g = 6.4f),
            FoodItem(name = "腰果", category = FoodCategory.SNACK, caloriesPer100g = 553f, proteinPer100g = 18.2f, carbsPer100g = 30.2f, fatPer100g = 43.8f, sodiumPer100g = 260f),
            FoodItem(name = "瓜子(炒葵花籽)", category = FoodCategory.SNACK, caloriesPer100g = 624f, proteinPer100g = 22.6f, carbsPer100g = 12.5f, fatPer100g = 54.4f, sodiumPer100g = 300f),
            FoodItem(name = "黑芝麻", category = FoodCategory.SNACK, caloriesPer100g = 531f, proteinPer100g = 19.1f, carbsPer100g = 24f, fatPer100g = 46.1f, sodiumPer100g = 8f),
            FoodItem(name = "白芝麻", category = FoodCategory.SNACK, caloriesPer100g = 536f, proteinPer100g = 18.4f, carbsPer100g = 23.9f, fatPer100g = 46.5f, sodiumPer100g = 9f),
            FoodItem(name = "开心果", category = FoodCategory.SNACK, caloriesPer100g = 614f, proteinPer100g = 20.6f, carbsPer100g = 21.9f, fatPer100g = 53f, sodiumPer100g = 260f),
            FoodItem(name = "杏仁", category = FoodCategory.SNACK, caloriesPer100g = 578f, proteinPer100g = 21.2f, carbsPer100g = 22.9f, fatPer100g = 48.2f, sodiumPer100g = 5f),
            FoodItem(name = "南瓜籽(炒)", category = FoodCategory.SNACK, caloriesPer100g = 582f, proteinPer100g = 36f, carbsPer100g = 23f, fatPer100g = 46f, sodiumPer100g = 5f),
            FoodItem(name = "花生(炒)", category = FoodCategory.SNACK, caloriesPer100g = 601f, proteinPer100g = 24.1f, carbsPer100g = 20.6f, fatPer100g = 48f, sodiumPer100g = 34f),
            FoodItem(name = "牛肉干", category = FoodCategory.SNACK, caloriesPer100g = 458f, proteinPer100g = 29.5f, carbsPer100g = 4f, fatPer100g = 36f, sodiumPer100g = 1500f),
            FoodItem(name = "甜甜圈", category = FoodCategory.SNACK, caloriesPer100g = 421f, proteinPer100g = 5.6f, carbsPer100g = 51f, fatPer100g = 22f, sodiumPer100g = 60f),
            FoodItem(name = "爆米花", category = FoodCategory.SNACK, caloriesPer100g = 387f, proteinPer100g = 12.6f, carbsPer100g = 77f, fatPer100g = 4.5f, sodiumPer100g = 60f),

            // ===== 其他（调味/饮品/油等）=====
            FoodItem(name = "蜂蜜", category = FoodCategory.OTHER, caloriesPer100g = 321f, proteinPer100g = 0.4f, carbsPer100g = 75.6f, fatPer100g = 1.9f, sodiumPer100g = 0.3f),
            FoodItem(name = "白砂糖", category = FoodCategory.OTHER, caloriesPer100g = 400f, proteinPer100g = 0f, carbsPer100g = 99.9f, fatPer100g = 0f, sodiumPer100g = 0.4f),
            FoodItem(name = "黑咖啡", category = FoodCategory.OTHER, caloriesPer100g = 2f, proteinPer100g = 0.1f, carbsPer100g = 0.3f, fatPer100g = 0f, sodiumPer100g = 2f),
            FoodItem(name = "可乐", category = FoodCategory.OTHER, caloriesPer100g = 43f, proteinPer100g = 0f, carbsPer100g = 10.6f, fatPer100g = 0f, sodiumPer100g = 8f),
            FoodItem(name = "橄榄油", category = FoodCategory.OTHER, caloriesPer100g = 900f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 100f, sodiumPer100g = 2f),
            FoodItem(name = "花生酱", category = FoodCategory.OTHER, caloriesPer100g = 594f, proteinPer100g = 25.6f, carbsPer100g = 20f, fatPer100g = 51.5f, sodiumPer100g = 495f),
            FoodItem(name = "果酱", category = FoodCategory.OTHER, caloriesPer100g = 273f, proteinPer100g = 0.4f, carbsPer100g = 66f, fatPer100g = 0.1f, sodiumPer100g = 11.8f),
            FoodItem(name = "酱油", category = FoodCategory.OTHER, caloriesPer100g = 63f, proteinPer100g = 5.6f, carbsPer100g = 10.1f, fatPer100g = 0.1f, sodiumPer100g = 5857f),
            FoodItem(name = "珍珠奶茶", category = FoodCategory.OTHER, caloriesPer100g = 90f, proteinPer100g = 0.3f, carbsPer100g = 22f, fatPer100g = 0.2f, sodiumPer100g = 60f),
            FoodItem(name = "花生油", category = FoodCategory.OTHER, caloriesPer100g = 899f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 99.9f, sodiumPer100g = 0.8f),
            FoodItem(name = "菜籽油", category = FoodCategory.OTHER, caloriesPer100g = 899f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 99.9f, sodiumPer100g = 0f),
            FoodItem(name = "芝麻油", category = FoodCategory.OTHER, caloriesPer100g = 898f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 99.7f, sodiumPer100g = 0f),
            FoodItem(name = "猪油", category = FoodCategory.OTHER, caloriesPer100g = 897f, proteinPer100g = 0f, carbsPer100g = 0f, fatPer100g = 99.6f, sodiumPer100g = 0f),
            FoodItem(name = "芝麻酱", category = FoodCategory.OTHER, caloriesPer100g = 617f, proteinPer100g = 21f, carbsPer100g = 18.4f, fatPer100g = 53f, sodiumPer100g = 457f),
            FoodItem(name = "番茄酱", category = FoodCategory.OTHER, caloriesPer100g = 81f, proteinPer100g = 1f, carbsPer100g = 18f, fatPer100g = 0.2f, sodiumPer100g = 5f),
            FoodItem(name = "豆瓣酱", category = FoodCategory.OTHER, caloriesPer100g = 178f, proteinPer100g = 10.7f, carbsPer100g = 17.1f, fatPer100g = 8.5f, sodiumPer100g = 1800f),
            FoodItem(name = "蚝油", category = FoodCategory.OTHER, caloriesPer100g = 60f, proteinPer100g = 1f, carbsPer100g = 12f, fatPer100g = 0.2f, sodiumPer100g = 1800f),
            FoodItem(name = "沙拉酱", category = FoodCategory.OTHER, caloriesPer100g = 483f, proteinPer100g = 1.8f, carbsPer100g = 6f, fatPer100g = 48f, sodiumPer100g = 900f),
            FoodItem(name = "拿铁咖啡", category = FoodCategory.OTHER, caloriesPer100g = 47f, proteinPer100g = 3f, carbsPer100g = 3f, fatPer100g = 2.4f, sodiumPer100g = 42f)
        )
    }
}





