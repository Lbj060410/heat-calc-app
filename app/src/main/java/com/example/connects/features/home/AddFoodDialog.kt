package com.example.connects.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.connects.core.util.formatAmount
import kotlin.math.roundToInt

@Composable
fun AddFoodDialog(
    knownFoods: List<HomeViewModel.QuickFoodItem>,
    onDismiss: () -> Unit,
    onSave: (AddFoodData) -> Unit
) {
    var foodName by remember { mutableStateOf("") }
    var amountGrams by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var sodium by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    // 用户手动改过热量之后就不再自动覆盖他的输入
    var caloriesEditedManually by remember { mutableStateOf(false) }

    LaunchedEffect(carbs, protein, fat, caloriesEditedManually) {
        if (caloriesEditedManually) return@LaunchedEffect
        val c = carbs.toFloatOrNull() ?: 0f
        val p = protein.toFloatOrNull() ?: 0f
        val f = fat.toFloatOrNull() ?: 0f
        val calculatedCalories = c * 4f + p * 4f + f * 9f
        // 营养素每次变动都要跟着重算，否则热量会一直停在第一次推算出来的值上
        calories = if (calculatedCalories > 0f) calculatedCalories.roundToInt().toString() else ""
    }

    // 食物库里已有同名食物：保存时记到它名下，不再新建一条重复的
    val matchedFood = remember(foodName, knownFoods) {
        val key = foodName.trim()
        if (key.isEmpty()) {
            null
        } else {
            knownFoods.firstOrNull { it.name.equals(key, ignoreCase = true) }
        }
    }

    LaunchedEffect(matchedFood) {
        val food = matchedFood ?: return@LaunchedEffect
        // 只在营养栏还全空时自动带入，避免覆盖用户已经敲进去的数
        val untouched = carbs.isBlank() && protein.isBlank() && fat.isBlank() &&
            sodium.isBlank() && calories.isBlank()
        if (!untouched) return@LaunchedEffect
        carbs = formatAmount(food.carbsPer100g)
        protein = formatAmount(food.proteinPer100g)
        fat = formatAmount(food.fatPer100g)
        sodium = formatAmount(food.sodiumPer100g)
        calories = formatAmount(food.caloriesPer100g)
        // 已有食物的热量不一定等于 4/4/9 推算值，标记为手填以免被改掉
        caloriesEditedManually = true
    }

    val amountValue = amountGrams.toFloatOrNull() ?: 0f
    val caloriesPer100g = calories.toFloatOrNull() ?: 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "添加食物",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text("食物名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (matchedFood != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "食物库里已经有「${matchedFood.name}」",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${formatAmount(matchedFood.caloriesPer100g)} kcal/100g · " +
                                    "碳水 ${formatAmount(matchedFood.carbsPer100g)} g · " +
                                    "蛋白质 ${formatAmount(matchedFood.proteinPer100g)} g · " +
                                    "脂肪 ${formatAmount(matchedFood.fatPer100g)} g",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "保存后直接记到它名下，不会新建重复食物",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountGrams,
                    onValueChange = { amountGrams = it },
                    label = { Text("食用数量 (克)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "营养成分 (每100克)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("碳水 (g/100g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("蛋白质 (g/100g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("脂肪 (g/100g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = sodium,
                    onValueChange = { sodium = it },
                    label = { Text("钠 (mg/100g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = calories,
                    onValueChange = {
                        calories = it
                        caloriesEditedManually = true
                    },
                    label = { Text("热量 (kcal/100g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        Text("根据三大营养素自动推算，也可以手动改")
                    }
                )

                if (amountValue > 0f && caloriesPer100g > 0f) {
                    IntakePreview(
                        amountGrams = amountValue,
                        caloriesPer100g = caloriesPer100g,
                        carbsPer100g = carbs.toFloatOrNull() ?: 0f,
                        proteinPer100g = protein.toFloatOrNull() ?: 0f,
                        fatPer100g = fat.toFloatOrNull() ?: 0f,
                        sodiumPer100g = sodium.toFloatOrNull() ?: 0f
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            val data = AddFoodData(
                                foodName = foodName,
                                amountGrams = amountValue,
                                carbs = carbs.toFloatOrNull() ?: 0f,
                                protein = protein.toFloatOrNull() ?: 0f,
                                fat = fat.toFloatOrNull() ?: 0f,
                                sodium = sodium.toFloatOrNull() ?: 0f,
                                calories = caloriesPer100g
                            )
                            onSave(data)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = foodName.isNotBlank() &&
                            amountValue > 0f &&
                            caloriesPer100g > 0f
                    ) {
                        Text(if (matchedFood != null) "记到「${matchedFood.name}」" else "保存")
                    }
                }
            }
        }
    }
}

/** 把每100克的营养值按实际克数换算后展示，让用户在保存前就能核对总热量。 */
@Composable
private fun IntakePreview(
    amountGrams: Float,
    caloriesPer100g: Float,
    carbsPer100g: Float,
    proteinPer100g: Float,
    fatPer100g: Float,
    sodiumPer100g: Float
) {
    val ratio = amountGrams / 100f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("本次摄入", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "${amountGrams.roundToInt()} g ≈ ${(caloriesPer100g * ratio).roundToInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "碳水 ${formatAmount(carbsPer100g * ratio)} g · " +
                    "蛋白质 ${formatAmount(proteinPer100g * ratio)} g · " +
                    "脂肪 ${formatAmount(fatPer100g * ratio)} g · " +
                    "钠 ${formatAmount(sodiumPer100g * ratio)} mg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class AddFoodData(
    val foodName: String,
    val amountGrams: Float,
    val carbs: Float,
    val protein: Float,
    val fat: Float,
    val sodium: Float,
    val calories: Float
)
