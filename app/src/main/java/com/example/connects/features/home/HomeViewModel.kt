package com.example.connects.features.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.connects.core.datastore.UserPreferences
import com.example.connects.domain.model.FoodCategory
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.FoodItem
import com.example.connects.domain.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val HOME_TAG = "HomeViewModel"
private const val PARSE_LOG_TEXT_LIMIT = 320

private fun homeLogSnippet(text: String, maxLen: Int = PARSE_LOG_TEXT_LIMIT): String {
    val compact = text
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.length <= maxLen) return compact
    return compact.take(maxLen) + "..."
}

private fun format2(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun normalizeMealType(raw: String): String {
    return when (raw) {
        MEAL_BREAKFAST, LEGACY_MEAL_BREAKFAST -> MEAL_BREAKFAST
        MEAL_LUNCH, LEGACY_MEAL_LUNCH -> MEAL_LUNCH
        MEAL_DINNER, LEGACY_MEAL_DINNER -> MEAL_DINNER
        MEAL_SNACK, LEGACY_MEAL_SNACK -> MEAL_SNACK
        else -> raw
    }
}

private const val MEAL_BREAKFAST = "早餐"
private const val MEAL_LUNCH = "午餐"
private const val MEAL_DINNER = "晚餐"
private const val MEAL_SNACK = "加餐"
private const val LEGACY_MEAL_BREAKFAST = "早餐"
private const val LEGACY_MEAL_LUNCH = "午餐"
private const val LEGACY_MEAL_DINNER = "晚餐"
private const val LEGACY_MEAL_SNACK = "加餐"

private val GENERIC_FOOD_NAME_KEYS = listOf(
    "营养", "成分", "项目", "顶目", "項目", "每100", "能量", "蛋白", "脂肪", "碳水",
    "膳食纤维", "营养素参考值", "nrv", "nutrition facts"
)
private data class NumericToken(
    val value: Float,
    val unit: String,
    val index: Int,
    val tokenId: Int
)

private data class ValueCursor(
    val tokens: List<NumericToken>,
    var pointer: Int = 0
) {
    fun nextByUnit(preferredUnits: Set<String>, hardMax: Float): NumericToken? {
        while (pointer < tokens.size) {
            val t = tokens[pointer++]
            if (t.value <= 0f) continue
            if (t.value > hardMax) continue
            if (preferredUnits.isNotEmpty() && t.unit.isNotEmpty() && t.unit !in preferredUnits) continue
            return t
        }
        return null
    }
}

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
        val mealSummaries: List<MealSummary> = listOf(
            MealSummary(MEAL_BREAKFAST, 0, 0),
            MealSummary(MEAL_LUNCH, 0, 0),
            MealSummary(MEAL_DINNER, 0, 0),
            MealSummary(MEAL_SNACK, 0, 0)
        ),
        val carbs: NutrientState = NutrientState(target = 250f),
        val protein: NutrientState = NutrientState(target = 60f),
        val fat: NutrientState = NutrientState(target = 70f),
        val fiber: NutrientState = NutrientState(target = 30f),
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
        val fiberPer100g: Float,
        val sodiumPer100g: Float
    )

    data class FoodPickerUiState(
        val mealType: String = MEAL_BREAKFAST,
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
        val fiberPer100g: Float = 0f,
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
        val normalizedMealType = normalizeMealType(mealType)
        _foodPickerUiState.value = _foodPickerUiState.value.copy(
            mealType = normalizedMealType,
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
            val todayEpochDay = currentEpochDay()
            val normalizedMealType = normalizeMealType(mealType)
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
                totalFiber = item.fiberPer100g * ratio,
                sodium = item.sodiumPer100g * ratio,
                mealType = normalizedMealType,
                dateEpochDay = todayEpochDay
            )
            foodRepository.addFoodEntry(entry)
            if (item.id > 0) {
                foodRepository.markFoodUsed(item.id)
            }

            if (item.isCustom) return@launch
            upsertCustomFoodTemplate(item)
        }
    }

    fun addCustomFoodAndEntry(data: AddFoodData, mealType: String) {
        val foodName = data.foodName.trim()
        if (foodName.isEmpty()) return
        if (data.amountGrams <= 0f) return

        viewModelScope.launch {
            val custom = FoodItem(
                name = decodeGarbledText(foodName),
                category = FoodCategory.OTHER,
                caloriesPer100g = if (data.calories > 0f) data.calories else data.carbs * 4f + data.protein * 4f + data.fat * 9f,
                proteinPer100g = data.protein,
                carbsPer100g = data.carbs,
                fatPer100g = data.fat,
                fiberPer100g = data.fiber,
                sodiumPer100g = data.sodium,
                isCustom = true
            )

            val customId = foodRepository.addCustomFoodItem(custom)
            val quick = custom.toQuickItem(customId)
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
                    fiberPer100g = data.fiberPer100g,
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

    fun parseNutritionLabel(scannedText: String, callback: (ScannedNutritionData) -> Unit) {
        Log.i(
            HOME_TAG,
            "parseNutritionLabel input len=${scannedText.length} snippet=\"${homeLogSnippet(scannedText)}\""
        )
        viewModelScope.launch {
            try {
                val parsedData = parseNutritionText(scannedText)
                Log.i(
                    HOME_TAG,
                    "parseNutritionLabel output food=${parsedData.foodName} kcal=${format2(parsedData.calories)} " +
                        "P=${format2(parsedData.protein)} C=${format2(parsedData.carbs)} F=${format2(parsedData.fat)} " +
                        "fiber=${format2(parsedData.fiber)} sodium=${format2(parsedData.sodium)} " +
                        "confidence=${format2(parsedData.confidence)} warnings=${parsedData.warnings.joinToString()}"
                )
                callback(parsedData)
            } catch (e: Exception) {
                Log.e(HOME_TAG, "parseNutritionLabel failed", e)
                callback(ScannedNutritionData())
            }
        }
    }

    private fun normalizeScannedNutritionText(text: String): String {
        var t = text
            .replace('：', ':')
            .replace('，', ' ')
            .replace('。', ' ')
            .replace('、', ' ')
            .replace('（', '(')
            .replace('）', ')')
            .replace(Regex("[|｜]+"), "\n")
            .replace(Regex("\\s+"), " ")
            .trim()

        t = t.replace(Regex("(?i)k[h]"), "kJ")
        t = t.replace(Regex("(?i)kca[1il]"), "kcal")
        t = t.replace(Regex("(?i)营养成分表|营养成份表|营养或分表"), "营养成分表")
        t = t.replace(Regex("(?i)营养成分|营养称以|营养称分"), "营养成分")
        t = t.replace(Regex("(?i)项[目自]"), "项目")
        t = t.replace(Regex("(?i)蛋[日白目]质"), "蛋白质")
        t = t.replace(Regex("(?i)脂[防肪]"), "脂肪")
        t = t.replace(Regex("(?i)碳水化[台合]物"), "碳水化合物")
        t = t.replace(Regex("(?i)膳食纤[维绳]"), "膳食纤维")
        t = t.replace(Regex("(?i)钠含[童量]"), "钠含量")
        t = t.replace(Regex("(?i)能[童量]"), "能量")
        t = t.replace(Regex("(?i)热[童量]"), "热量")
        t = t.replace(Regex("(?i)儿L"), "儿童")
        t = t.replace(Regex("(?i)m9"), "mg")
        t = t.replace(Regex("(\\d)q\\b"), "$1g")
        t = t.replace('α', 'a')

        t = t.replace('ğ', 'g')
        t = t.replace('Ğ', 'g')

        val valueUnitRegex = Regex("([0-9A-Za-z]{1,10})\\s*(mg|g|kJ|kj|kcal|ug|μg)", RegexOption.IGNORE_CASE)
        t = valueUnitRegex.replace(t) { mr ->
            val fixedValue = mr.groupValues[1]
                .replace('O', '0')
                .replace('o', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('S', '5')
                .replace('s', '5')
                .replace('B', '8')
                .replace('Z', '2')
            val rawUnit = mr.groupValues[2].lowercase()
            val unit = when (rawUnit) {
                "kj" -> "kJ"
                "μg" -> "ug"
                else -> rawUnit
            }
            "$fixedValue$unit"
        }

        return t
    }

    private fun findFoodName(lines: List<String>): String {
        for (line in lines) {
            if (line.contains("产品名称") || line.contains("食品名称") || line.contains("品名")) {
                val cleaned = line.replace(Regex("[^\\u4e00-\\u9fa5a-zA-Z0-9]"), "").trim()
                if (cleaned.length >= 2) return cleaned
            }
        }

        val candidate = lines.firstOrNull { line ->
            val trimmed = line.trim()
            if (trimmed.length < 2) return@firstOrNull false
            val lower = trimmed.lowercase()
            val generic = GENERIC_FOOD_NAME_KEYS.any { lower.contains(it) }
            val nutrientHit = listOf("能量", "蛋白", "脂肪", "碳水", "纤维", "钠").count { lower.contains(it) }
            !generic && nutrientHit < 2
        }?.trim().orEmpty()

        return if (candidate.length >= 2) candidate else "扫描食物"
    }

    private fun flattenWithOffsets(lines: List<String>): Pair<String, List<IntRange>> {
        val sb = StringBuilder()
        val ranges = mutableListOf<IntRange>()
        var start = 0
        lines.forEachIndexed { index, line ->
            sb.append(line)
            val end = sb.length
            ranges += (start until end)
            if (index != lines.lastIndex) {
                sb.append('\n')
                start = sb.length
            }
        }
        return sb.toString() to ranges
    }

    private fun extractNumericTokens(flatText: String): List<NumericToken> {
        val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*([a-zA-Zμ]*)")
        val result = mutableListOf<NumericToken>()
        var id = 0
        val allowedUnits = setOf("kcal", "kj", "mg", "g", "ug")

        regex.findAll(flatText).forEach { mr ->
            val value = mr.groupValues[1].toFloatOrNull() ?: return@forEach
            val rawUnit = mr.groupValues.getOrNull(2).orEmpty().lowercase()
            val normalizedUnit = when (rawUnit) {
                "kj" -> "kJ"
                "μg" -> "ug"
                else -> rawUnit
            }
            if (normalizedUnit.isNotEmpty() && normalizedUnit !in allowedUnits) return@forEach
            val nextIndex = mr.range.last + 1
            if (nextIndex < flatText.length && flatText[nextIndex] == '%') return@forEach
            result += NumericToken(
                value = value,
                unit = normalizedUnit,
                index = mr.range.first,
                tokenId = id++
            )
        }
        return result
    }

    private fun nearestNumericToken(
        flatText: String,
        tokens: List<NumericToken>,
        keywordRegex: Regex,
        expectedUnits: Set<String>,
        usedTokenIds: Set<Int>,
        preferTokenAfterKeyword: Boolean = true
    ): NumericToken? {
        var bestKeywordIndex = -1
        keywordRegex.findAll(flatText).forEach { mr ->
            if (mr.range.first > bestKeywordIndex) {
                bestKeywordIndex = mr.range.first
            }
        }
        if (bestKeywordIndex < 0) return null

        var bestToken: NumericToken? = null
        var bestDistance = Int.MAX_VALUE
        tokens.forEach { token ->
            if (token.tokenId in usedTokenIds) return@forEach
            if (expectedUnits.isNotEmpty() && token.unit !in expectedUnits) return@forEach
            val directionBias = if (preferTokenAfterKeyword && token.index < bestKeywordIndex) 120 else 0
            val distance = kotlin.math.abs(token.index - bestKeywordIndex) + directionBias
            if (distance < bestDistance) {
                bestDistance = distance
                bestToken = token
            }
        }
        return bestToken
    }

    private fun findKeywordIndex(flatText: String, keywordRegex: Regex): Int {
        var bestKeywordIndex = -1
        keywordRegex.findAll(flatText).forEach { mr ->
            if (mr.range.first > bestKeywordIndex) {
                bestKeywordIndex = mr.range.first
            }
        }
        return bestKeywordIndex
    }

    private fun sortedNutrientKeywords(): List<Pair<String, Regex>> {
        return listOf(
            "energy" to Regex("(能量|热量|energy)", RegexOption.IGNORE_CASE),
            "protein" to Regex("(蛋白质|蛋白|protein)", RegexOption.IGNORE_CASE),
            "fat" to Regex("(总脂肪|脂肪|fat)", RegexOption.IGNORE_CASE),
            "carbs" to Regex("(碳水化合物|碳水|carbohydrate|carbs)", RegexOption.IGNORE_CASE),
            "fiber" to Regex("(膳食纤维|纤维|fiber)", RegexOption.IGNORE_CASE),
            "sodium" to Regex("(钠含量|钠|sodium)", RegexOption.IGNORE_CASE)
        )
    }

    private fun extractCoreNutritionRange(flatText: String): IntRange? {
        if (flatText.isBlank()) return null
        val lower = flatText.lowercase()

        val startByEach100 = listOf("每100克", "每100g", "每100", "per 100")
            .map { marker -> lower.indexOf(marker.lowercase()) }
            .filter { it >= 0 }
            .minOrNull()

        val startByLabel = listOf("营养成分表", "nutrition facts")
            .map { marker -> lower.indexOf(marker.lowercase()) }
            .filter { it >= 0 }
            .minOrNull()

        val start = when {
            startByEach100 != null -> startByEach100
            startByLabel != null -> startByLabel
            else -> 0
        }

        val endMarkers = listOf("备注", "说明", "本产品", "配料", "贮存", "食用方法")
        val end = endMarkers
            .mapNotNull { marker ->
                val idx = lower.indexOf(marker.lowercase(), startIndex = start)
                if (idx >= 0) idx else null
            }
            .minOrNull() ?: flatText.length

        return if (start < end) start until end else null
    }

    private fun isMicronutrientContext(flatText: String, tokenIndex: Int): Boolean {
        val from = (tokenIndex - 14).coerceAtLeast(0)
        val prefix = flatText.substring(from, tokenIndex).lowercase()
        val microKeys = listOf(
            "维生素", "叶酸", "生物素", "烟酸", "钙", "铁", "锌", "镁", "钾", "磷", "胆碱", "铜", "锰"
        )
        return microKeys.any { prefix.contains(it) }
    }

    private fun parseNutritionText(text: String): ScannedNutritionData {
        val normalizedText = normalizeScannedNutritionText(text)
        val lines = normalizedText
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(normalizedText) }

        var foodName = ""
        var carbs = 0f
        var protein = 0f
        var fat = 0f
        var fiber = 0f
        var sodium = 0f
        var caloriesFromLabel = 0f
        var perServingFactor = 1f

        foodName = findFoodName(lines)
        Log.d(HOME_TAG, "parseNutritionText foodName=\"$foodName\"")

        for (line in lines) {
            val lower = line.lowercase()
            val servingRegex = Regex("(\\d+\\.?\\d*)\\s*(g|ml)")
            if (lower.contains("每份") || lower.contains("每一份") || lower.contains("per serving")) {
                val serving = servingRegex.find(lower)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                if (serving != null && serving > 0f) {
                    perServingFactor = 100f / serving
                    Log.d(HOME_TAG, "Detected per-serving basis=$serving, normalized factor=$perServingFactor")
                    break
                }
            }
        }

        val (flatText, _) = flattenWithOffsets(lines)
        val tokens = extractNumericTokens(flatText)
        Log.d(HOME_TAG, "parseNutritionText normalized=\"${homeLogSnippet(flatText)}\" tokenCount=${tokens.size}")

        val coreRange = extractCoreNutritionRange(flatText)
        val coreTokens = if (coreRange != null) {
            tokens.filter { it.index in coreRange }
        } else {
            tokens
        }
        Log.d(
            HOME_TAG,
            "parseNutritionText coreRange=${coreRange?.first ?: -1}..${coreRange?.last ?: -1} coreTokens=${coreTokens.map { "${it.value}${it.unit}" }}"
        )

        val energyToken = coreTokens.firstOrNull {
            it.unit.equals("kJ", ignoreCase = true) || it.unit.equals("kcal", ignoreCase = true)
        } ?: tokens.firstOrNull {
            it.unit.equals("kJ", ignoreCase = true) || it.unit.equals("kcal", ignoreCase = true)
        }
        if (energyToken != null) {
            caloriesFromLabel = if (energyToken.unit.equals("kJ", ignoreCase = true) || energyToken.value > 350f) {
                energyToken.value / 4.184f
            } else {
                energyToken.value
            }
            if (caloriesFromLabel <= 2000f) caloriesFromLabel *= perServingFactor
            Log.d(HOME_TAG, "Core energy=${format2(caloriesFromLabel)} from token=${energyToken.value}${energyToken.unit}")
        }

        val macroSearchStart = energyToken?.index ?: (coreRange?.first ?: 0)
        val macroCandidates = coreTokens
            .asSequence()
            .filter { it.index > macroSearchStart }
            .filter { it.value in 0.1f..100f }
            .filter { it.unit == "g" || it.unit.isEmpty() }
            .filterNot { isMicronutrientContext(flatText, it.index) }
            .toList()
        Log.d(HOME_TAG, "Core macroCandidates=${macroCandidates.map { "${it.value}${it.unit}" }}")

        fun toGram(token: NumericToken): Float {
            val value = if (token.unit == "mg") token.value / 1000f else token.value
            return if (value <= 100f) value * perServingFactor else value
        }

        if (macroCandidates.size >= 3) {
            protein = toGram(macroCandidates[0])
            fat = toGram(macroCandidates[1])
            carbs = toGram(macroCandidates[2])
            if (macroCandidates.size >= 4) {
                fiber = toGram(macroCandidates[3])
            }
            Log.d(
                HOME_TAG,
                "Core sequence assign P=${format2(protein)} F=${format2(fat)} C=${format2(carbs)} fiber=${format2(fiber)}"
            )
        }

        if (sodium <= 0f) {
            val sodiumStart = macroCandidates.getOrNull(3)?.index
                ?: macroCandidates.lastOrNull()?.index
                ?: macroSearchStart
            val sodiumToken = coreTokens.firstOrNull { token ->
                token.index > sodiumStart &&
                    (token.unit == "mg" || token.unit == "g") &&
                    token.value > 5f &&
                    token.value < 5000f &&
                    !isMicronutrientContext(flatText, token.index)
            }
            if (sodiumToken != null) {
                sodium = if (sodiumToken.unit == "g") sodiumToken.value * 1000f else sodiumToken.value
                if (sodium <= 10000f) sodium *= perServingFactor
                Log.d(HOME_TAG, "Core sodium=${format2(sodium)} from token=${sodiumToken.value}${sodiumToken.unit}")
            }
        }

        if (protein <= 0f || fat <= 0f || carbs <= 0f || fiber <= 0f || sodium <= 0f || caloriesFromLabel <= 0f) {
            val usedTokenIds = mutableSetOf<Int>()

            if (protein <= 0f) {
                val token = nearestNumericToken(
                    flatText = flatText,
                    tokens = tokens,
                    keywordRegex = Regex("(蛋白质|蛋白|protein)", RegexOption.IGNORE_CASE),
                    expectedUnits = setOf("g", "mg", ""),
                    usedTokenIds = usedTokenIds
                )
                if (token != null) {
                    protein = if (token.unit == "mg") token.value / 1000f else token.value
                    if (protein <= 100f) protein *= perServingFactor
                    usedTokenIds += token.tokenId
                    Log.d(HOME_TAG, "Fallback protein=${format2(protein)} from token=${token.value}${token.unit}")
                }
            }

            if (fat <= 0f) {
                val token = nearestNumericToken(
                    flatText = flatText,
                    tokens = tokens,
                    keywordRegex = Regex("(总脂肪|脂肪|fat)", RegexOption.IGNORE_CASE),
                    expectedUnits = setOf("g", "mg", ""),
                    usedTokenIds = usedTokenIds
                )
                if (token != null) {
                    fat = if (token.unit == "mg") token.value / 1000f else token.value
                    if (fat <= 100f) fat *= perServingFactor
                    usedTokenIds += token.tokenId
                    Log.d(HOME_TAG, "Fallback fat=${format2(fat)} from token=${token.value}${token.unit}")
                }
            }

            if (carbs <= 0f) {
                val token = nearestNumericToken(
                    flatText = flatText,
                    tokens = tokens,
                    keywordRegex = Regex("(碳水化合物|碳水|carbohydrate|carbs)", RegexOption.IGNORE_CASE),
                    expectedUnits = setOf("g", "mg", ""),
                    usedTokenIds = usedTokenIds
                )
                if (token != null) {
                    carbs = if (token.unit == "mg") token.value / 1000f else token.value
                    if (carbs <= 100f) carbs *= perServingFactor
                    usedTokenIds += token.tokenId
                    Log.d(HOME_TAG, "Fallback carbs=${format2(carbs)} from token=${token.value}${token.unit}")
                }
            }

            if (fiber <= 0f) {
                val token = nearestNumericToken(
                    flatText = flatText,
                    tokens = tokens,
                    keywordRegex = Regex("(膳食纤维|纤维|fiber)", RegexOption.IGNORE_CASE),
                    expectedUnits = setOf("g", "mg", ""),
                    usedTokenIds = usedTokenIds
                )
                if (token != null) {
                    fiber = if (token.unit == "mg") token.value / 1000f else token.value
                    if (fiber <= 100f) fiber *= perServingFactor
                    usedTokenIds += token.tokenId
                    Log.d(HOME_TAG, "Fallback fiber=${format2(fiber)} from token=${token.value}${token.unit}")
                }
            }

            if (sodium <= 0f) {
                val token = nearestNumericToken(
                    flatText = flatText,
                    tokens = tokens,
                    keywordRegex = Regex("(钠含量|钠|sodium)", RegexOption.IGNORE_CASE),
                    expectedUnits = setOf("mg", "g", ""),
                    usedTokenIds = usedTokenIds
                )
                if (token != null) {
                    sodium = if (token.unit == "g") token.value * 1000f else token.value
                    if (token.unit.isEmpty() && sodium in 1f..20f) sodium *= 100f
                    if (sodium <= 10000f) sodium *= perServingFactor
                    usedTokenIds += token.tokenId
                    Log.d(HOME_TAG, "Fallback sodium=${format2(sodium)} from token=${token.value}${token.unit}")
                }
            }

            if (caloriesFromLabel <= 0f) {
                val token = nearestNumericToken(
                    flatText = flatText,
                    tokens = tokens,
                    keywordRegex = Regex("(能量|热量|energy)", RegexOption.IGNORE_CASE),
                    expectedUnits = setOf("kJ", "kcal", ""),
                    usedTokenIds = usedTokenIds
                )
                if (token != null) {
                    caloriesFromLabel = if (token.unit.equals("kJ", ignoreCase = true) || token.value > 350f) {
                        token.value / 4.184f
                    } else {
                        token.value
                    }
                    if (caloriesFromLabel <= 2000f) caloriesFromLabel *= perServingFactor
                    usedTokenIds += token.tokenId
                    Log.d(HOME_TAG, "Fallback caloriesFromLabel=${format2(caloriesFromLabel)} from token=${token.value}${token.unit}")
                }
            }
        }

        val caloriesByMacro = carbs * 4f + protein * 4f + fat * 9f
        val calories = if (caloriesFromLabel > 0f) caloriesFromLabel else caloriesByMacro

        val warnings = mutableListOf<String>()
        var penalties = 0f

        val weightedBase =
            (if (carbs > 0f) 0.22f else 0f) +
            (if (protein > 0f) 0.22f else 0f) +
            (if (fat > 0f) 0.22f else 0f) +
            (if (calories > 0f) 0.20f else 0f) +
            (if (fiber > 0f) 0.07f else 0f) +
            (if (sodium > 0f) 0.07f else 0f)

        var caloriesDiffRatio = 0f
        var severeInconsistency = false

        if (caloriesFromLabel > 0f && caloriesByMacro > 0f) {
            caloriesDiffRatio = abs(caloriesFromLabel - caloriesByMacro) / caloriesFromLabel.coerceAtLeast(1f)
            if (caloriesDiffRatio > 0.35f) {
                warnings += "热量与三大营养素推算偏差较大，请确认"
                penalties += 0.12f
                severeInconsistency = true
            } else if (caloriesDiffRatio > 0.22f) {
                warnings += "热量与三大营养素有一定偏差，请确认"
                penalties += 0.06f
            }
        }
        if (sodium > 0f && sodium < 1f) {
            warnings += "钠可能是g单位，已按mg归一，请确认"
            penalties += 0.04f
        }
        if (carbs > 100f || protein > 100f || fat > 100f || fiber > 100f) {
            warnings += "每100g营养值超过100，可能存在单位或OCR误差"
            penalties += 0.12f
            severeInconsistency = true
        }
        if (foodName.length < 2) {
            warnings += "食品名称置信度偏低"
            penalties += 0.03f
        }

        val hasThreeMacros = carbs > 0f && protein > 0f && fat > 0f
        val floor = when {
            hasThreeMacros && !severeInconsistency && calories > 0f -> 0.70f
            hasThreeMacros && !severeInconsistency -> 0.64f
            else -> 0f
        }
        var confidence = weightedBase
        if (floor > 0f) {
            confidence = maxOf(confidence, floor)
        }
        confidence = (confidence - penalties).coerceIn(0f, 1f)
        if (floor > 0f && !severeInconsistency) {
            confidence = maxOf(confidence, floor - 0.08f)
        }

        Log.i(
            HOME_TAG,
            "parseNutritionText factor=${format2(perServingFactor)} kcalLabel=${format2(caloriesFromLabel)} kcalByMacro=${format2(caloriesByMacro)} diff=${format2(caloriesDiffRatio)} " +
                "weightedBase=${format2(weightedBase)} penalties=${format2(penalties)} floor=${format2(floor)} final=${format2(confidence)} warnings=${warnings.joinToString()}"
        )

        return ScannedNutritionData(
            foodName = foodName,
            amountGrams = 100f,
            carbs = carbs,
            protein = protein,
            fat = fat,
            fiber = fiber,
            sodium = sodium,
            calories = calories,
            confidence = confidence,
            warnings = warnings
        )
    }

    private fun extractNumber(text: String): Float {
        val regexWithUnit = Regex("(\\d+\\.?\\d*)\\s*[gmg]*")
        val matchWithUnit = regexWithUnit.find(text)
        if (matchWithUnit != null) {
            return matchWithUnit.groupValues[1].toFloatOrNull() ?: 0f
        }

        val regex = Regex("(\\d+\\.?\\d*)")
        return regex.find(text)?.value?.toFloatOrNull() ?: 0f
    }

    private fun extractNumberFromNutritionLine(line: String): Float {
        val cleanedLine = line.replace(Regex("第\\s*\\d+\\s*行[:：]"), "")
        return extractNumber(cleanedLine)
    }

    private fun normalizePer100gValue(line: String, lineType: String, perServingFactor: Float): Float {
        val cleaned = line.replace(Regex("第\\s*\\d+\\s*行[:：]"), "")
        val lower = cleaned.lowercase()
        val raw = extractNumber(cleaned)
        if (raw <= 0f) return 0f

        val normalizedUnitValue = when (lineType) {
            "mg" -> if (lower.contains("g") && !lower.contains("mg")) raw * 1000f else raw
            "kcal" -> if (lower.contains("kj")) raw / 4.184f else raw
            else -> if (lower.contains("mg")) raw / 1000f else raw
        }

        val isPer100 = lower.contains("每100") || lower.contains("100g") || lower.contains("per 100")
        return if (isPer100) normalizedUnitValue else normalizedUnitValue * perServingFactor
    }
    
    private fun observeDashboard() {
        val todayEpochDay = currentEpochDay()

        viewModelScope.launch {
            combine(
                userPreferences.dailyCaloricTarget,
                userPreferences.userWeight,
                foodRepository.getEntriesByDate(todayEpochDay)
            ) { targetCalories, userWeight, todayEntries ->
                val consumedCarbs = todayEntries.sumOf { it.totalCarbs.toDouble() }.toFloat()
                val consumedProtein = todayEntries.sumOf { it.totalProtein.toDouble() }.toFloat()
                val consumedFat = todayEntries.sumOf { it.totalFat.toDouble() }.toFloat()
                val consumedFiber = todayEntries.sumOf { it.totalFiber.toDouble() }.toFloat()
                val consumedSodium = todayEntries.sumOf { it.sodium.toDouble() }.toFloat()
                val consumedCalories = todayEntries.sumOf { it.totalCalories.toDouble() }.toInt()

                val targetCarbs = (targetCalories * 0.5f / 4f)
                val targetProtein = (targetCalories * 0.2f / 4f)
                val targetFat = (targetCalories * 0.3f / 9f)
                val targetFiber = 30f

                val mealSummaries = listOf(MEAL_BREAKFAST, MEAL_LUNCH, MEAL_DINNER, MEAL_SNACK).map { meal ->
                    val mealEntries = todayEntries.filter { normalizeMealType(it.mealType) == meal }
                    MealSummary(
                        mealType = meal,
                        totalCalories = mealEntries.sumOf { it.totalCalories.toInt() },
                        count = mealEntries.size
                    )
                }

                HomeUiState(
                    targetCalories = targetCalories,
                    consumedCalories = consumedCalories,
                    userWeight = userWeight,
                    todayEntries = todayEntries,
                    mealSummaries = mealSummaries,
                    carbs = NutrientState(consumed = consumedCarbs, target = targetCarbs),
                    protein = NutrientState(consumed = consumedProtein, target = targetProtein),
                    fat = NutrientState(consumed = consumedFat, target = targetFat),
                    fiber = NutrientState(consumed = consumedFiber, target = targetFiber),
                    sodium = NutrientState(consumed = consumedSodium, target = 2300f)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
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

    private suspend fun upsertCustomFoodTemplate(item: QuickFoodItem) {
        val normalizedName = decodeGarbledText(item.name)
        val existing = foodRepository.searchFoodItems(normalizedName).firstOrNull()
            ?.firstOrNull { decodeGarbledText(it.name) == normalizedName && it.isCustom }
        if (existing != null) return

        foodRepository.addCustomFoodItem(
            FoodItem(
                name = normalizedName,
                category = item.category,
                caloriesPer100g = item.caloriesPer100g,
                proteinPer100g = item.proteinPer100g,
                carbsPer100g = item.carbsPer100g,
                fatPer100g = item.fatPer100g,
                fiberPer100g = item.fiberPer100g,
                sodiumPer100g = item.sodiumPer100g,
                isCustom = true
            )
        )
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
            fiberPer100g = fiberPer100g,
            sodiumPer100g = sodiumPer100g
        )
    }

    private fun currentEpochDay(): Int {
        val calendar = Calendar.getInstance()
        return (calendar.timeInMillis / (1000 * 60 * 60 * 24)).toInt()
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

            FoodItem(name = "西兰花", category = FoodCategory.VEGETABLE, caloriesPer100g = 34f, proteinPer100g = 2.8f, carbsPer100g = 6.6f, fatPer100g = 0.4f, fiberPer100g = 2.6f),
            FoodItem(name = "菠菜", category = FoodCategory.VEGETABLE, caloriesPer100g = 24f, proteinPer100g = 2.6f, carbsPer100g = 3.6f, fatPer100g = 0.3f, fiberPer100g = 2.2f),
            FoodItem(name = "番茄", category = FoodCategory.VEGETABLE, caloriesPer100g = 19f, proteinPer100g = 0.9f, carbsPer100g = 4f, fatPer100g = 0.2f, fiberPer100g = 1.1f),

            FoodItem(name = "鸡胸肉", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 133f, proteinPer100g = 19.4f, carbsPer100g = 0f, fatPer100g = 5f),
            FoodItem(name = "鸡蛋", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 139f, proteinPer100g = 13.3f, carbsPer100g = 2.8f, fatPer100g = 8.8f),
            FoodItem(name = "纯牛奶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 62f, proteinPer100g = 3.2f, carbsPer100g = 4.8f, fatPer100g = 3.5f),
            FoodItem(name = "酸奶", category = FoodCategory.MEAT_DAIRY, caloriesPer100g = 72f, proteinPer100g = 2.5f, carbsPer100g = 9.3f, fatPer100g = 3.1f),

            FoodItem(name = "苹果", category = FoodCategory.FRUIT, caloriesPer100g = 53f, proteinPer100g = 0.3f, carbsPer100g = 13.5f, fatPer100g = 0.2f, fiberPer100g = 1.7f),
            FoodItem(name = "香蕉", category = FoodCategory.FRUIT, caloriesPer100g = 93f, proteinPer100g = 1.4f, carbsPer100g = 22f, fatPer100g = 0.2f, fiberPer100g = 1.2f),
            FoodItem(name = "橙子", category = FoodCategory.FRUIT, caloriesPer100g = 48f, proteinPer100g = 0.8f, carbsPer100g = 11.1f, fatPer100g = 0.2f, fiberPer100g = 0.6f),

            FoodItem(name = "饼干", category = FoodCategory.SNACK, caloriesPer100g = 433f, proteinPer100g = 6.6f, carbsPer100g = 71.7f, fatPer100g = 13.6f),
            FoodItem(name = "薯片", category = FoodCategory.SNACK, caloriesPer100g = 548f, proteinPer100g = 7.4f, carbsPer100g = 50.3f, fatPer100g = 35.3f),
            FoodItem(name = "黑巧克力", category = FoodCategory.SNACK, caloriesPer100g = 546f, proteinPer100g = 4.9f, carbsPer100g = 61f, fatPer100g = 31f)
        )
    }
}





