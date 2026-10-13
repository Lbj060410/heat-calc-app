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
                    combine(
                        userPreferences.dailyCaloricTarget,
                        userPreferences.userWeight,
                        userPreferences.macroTargets,
                        foodRepository.getEntriesByDate(todayEpochDay)
                    ) { targetCalories, userWeight, macroTargets, todayEntries ->
                        buildDashboard(targetCalories, userWeight, macroTargets, todayEntries)
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
        if (existing.isNotEmpty()) return existing
        val presets = presetFoods()
        presets.forEach { foodRepository.addCustomFoodItem(it.copy(isCustom = false)) }
        return foodRepository.getAllFoodItems().firstOrNull().orEmpty()
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

    private fun presetFoods(): List<FoodItem> {
        return listOf(
            FoodItem(name = "米饭", category = FoodCategory.STAPLE, caloriesPer100g = 116f, proteinPer100g = 2.6f, carbsPer100g = 25.9f, fatPer100g = 0.3f),
            FoodItem(name = "馒头", category = FoodCategory.STAPLE, caloriesPer100g = 223f, proteinPer100g = 7f, carbsPer100g = 47f, fatPer100g = 1.1f),
            FoodItem(name = "面条", category = FoodCategory.STAPLE, caloriesPer100g = 138f, proteinPer100g = 4.5f, carbsPer100g = 27f, fatPer100g = 1.2f),
            FoodItem(name = "燕麦片", category = FoodCategory.STAPLE, caloriesPer100g = 367f, proteinPer100g = 15f, carbsPer100g = 61f, fatPer100g = 7f),

            FoodItem(name = "西兰花", category = FoodCategory.VEGETABLE, caloriesPer100g = 34f, proteinPer100g = 2.8f, carbsPer100g = 6.6f, fatPer100g = 0.4f),
            FoodItem(name = "菠菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.6f, carbsPer100g = 3.6f, fatPer100g = 0.3f),
            FoodItem(name = "番茄", category = FoodCategory.VEGETABLE, caloriesPer100g = 19f, proteinPer100g = 0.9f, carbsPer100g = 4f, fatPer100g = 0.2f),

            FoodItem(name = "鸡胸肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 133f, proteinPer100g = 19.4f, carbsPer100g = 0f, fatPer100g = 5f),
            FoodItem(name = "鸡蛋", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 139f, proteinPer100g = 13.3f, carbsPer100g = 2.8f, fatPer100g = 8.8f),
            FoodItem(name = "纯牛奶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 62f, proteinPer100g = 3.2f, carbsPer100g = 4.8f, fatPer100g = 3.5f),
            FoodItem(name = "酸奶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 72f, proteinPer100g = 2.5f, carbsPer100g = 9.3f, fatPer100g = 3.1f),

            FoodItem(name = "苹果", category = FoodCategory.FRUIT, caloriesPer100g = 53f, proteinPer100g = 0.3f, carbsPer100g = 13.5f, fatPer100g = 0.2f),
            FoodItem(name = "香蕉", category = FoodCategory.FRUIT, caloriesPer100g = 93f, proteinPer100g = 1.4f, carbsPer100g = 22f, fatPer100g = 0.2f),
            FoodItem(name = "橙子", category = FoodCategory.FRUIT, caloriesPer100g = 48f, proteinPer100g = 0.8f, carbsPer100g = 11.1f, fatPer100g = 0.2f),

            FoodItem(name = "饼干", category = FoodCategory.SNACK, caloriesPer100g = 433f, proteinPer100g = 6.6f, carbsPer100g = 71.7f, fatPer100g = 13.6f),
            FoodItem(name = "薯片", category = FoodCategory.SNACK, caloriesPer100g = 548f, proteinPer100g = 7.4f, carbsPer100g = 50.3f, fatPer100g = 35.3f),
            FoodItem(name = "黑巧克力", category = FoodCategory.SNACK, caloriesPer100g = 546f, proteinPer100g = 4.9f, carbsPer100g = 61f, fatPer100g = 31f)
        )
    }
}





