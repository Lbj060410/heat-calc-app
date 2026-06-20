package com.example.connects.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
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

@Composable
fun AddFoodDialog(
    onDismiss: () -> Unit,
    onSave: (AddFoodData) -> Unit,
    onScanClick: () -> Unit = {},
    initialData: ScannedNutritionData? = null
) {
    var foodName by remember { mutableStateOf("") }
    var amountGrams by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var sodium by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }

    LaunchedEffect(initialData) {
        if (initialData != null) {
            foodName = initialData.foodName
            amountGrams = initialData.amountGrams.toString()
            carbs = initialData.carbs.toString()
            protein = initialData.protein.toString()
            fat = initialData.fat.toString()
            fiber = initialData.fiber.toString()
            sodium = initialData.sodium.toString()
            calories = if (initialData.calories > 0f) initialData.calories.toString() else ""
        }
    }

    LaunchedEffect(carbs, protein, fat) {
        val c = carbs.toFloatOrNull() ?: 0f
        val p = protein.toFloatOrNull() ?: 0f
        val f = fat.toFloatOrNull() ?: 0f
        val calculatedCalories = (c * 4f + p * 4f + f * 9f).toInt()
        if (calculatedCalories > 0 && calories.isEmpty()) {
            calories = calculatedCalories.toString()
        }
    }

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

                OutlinedTextField(
                    value = amountGrams,
                    onValueChange = { amountGrams = it },
                    label = { Text("食用数量 (克)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "营养成分 (每100克)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("碳水化合物 (g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = { Text("g", modifier = Modifier.padding(end = 12.dp)) }
                )

                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("蛋白质 (g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = { Text("g", modifier = Modifier.padding(end = 12.dp)) }
                )

                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("脂肪 (g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = { Text("g", modifier = Modifier.padding(end = 12.dp)) }
                )

                OutlinedTextField(
                    value = fiber,
                    onValueChange = { fiber = it },
                    label = { Text("膳食纤维 (g)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = { Text("g", modifier = Modifier.padding(end = 12.dp)) }
                )

                OutlinedTextField(
                    value = sodium,
                    onValueChange = { sodium = it },
                    label = { Text("钠 (mg)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    trailingIcon = { Text("mg", modifier = Modifier.padding(end = 12.dp)) }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("热量 (kcal)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = { Text("kcal", modifier = Modifier.padding(end = 12.dp)) },
                    supportingText = { Text("会根据三大营养素自动估算") }
                )

                OutlinedButton(
                    onClick = onScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("重新扫描营养成分表", fontSize = 16.sp)
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
                                amountGrams = amountGrams.toFloatOrNull() ?: 0f,
                                carbs = carbs.toFloatOrNull() ?: 0f,
                                protein = protein.toFloatOrNull() ?: 0f,
                                fat = fat.toFloatOrNull() ?: 0f,
                                fiber = fiber.toFloatOrNull() ?: 0f,
                                sodium = sodium.toFloatOrNull() ?: 0f,
                                calories = calories.toFloatOrNull() ?: 0f
                            )
                            onSave(data)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = foodName.isNotBlank() && amountGrams.toFloatOrNull() != null
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

data class AddFoodData(
    val foodName: String,
    val amountGrams: Float,
    val carbs: Float,
    val protein: Float,
    val fat: Float,
    val fiber: Float,
    val sodium: Float,
    val calories: Float
)

data class ScannedNutritionData(
    val foodName: String = "",
    val amountGrams: Float = 100f,
    val carbs: Float = 0f,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val fiber: Float = 0f,
    val sodium: Float = 0f,
    val calories: Float = 0f,
    val confidence: Float = 0f,
    val warnings: List<String> = emptyList()
)
