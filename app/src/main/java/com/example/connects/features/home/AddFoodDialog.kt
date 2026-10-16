package com.example.connects.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.connects.core.util.formatAmount
import com.example.connects.ui.components.JournalUnderlineField
import com.example.connects.ui.components.NoteBlock
import com.example.connects.ui.components.SectionHead
import com.example.connects.ui.components.ledgerNumber
import com.example.connects.ui.theme.JournalInput
import com.example.connects.ui.theme.JournalStatement
import com.example.connects.ui.theme.JournalTitle
import kotlin.math.roundToInt

/** 弹窗表单里下划线输入的字号：比首页大数字小一号。 */
private val DialogFieldStyle = JournalInput.copy(fontSize = 22.sp, lineHeight = 28.sp)

/**
 * 自定义食物弹窗 —— 手账风全屏表单：下划线输入 + 分节标题 + 墨色保存。
 * 热量随三大营养素自动推算、同名食物自动挂靠既有条目的逻辑全部保留。
 */
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
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "添加食物",
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(22.dp))
                JournalUnderlineField(
                    label = "食物名称",
                    value = foodName,
                    onValueChange = { foodName = it },
                    keyboardType = KeyboardType.Text,
                    textStyle = DialogFieldStyle
                )

                if (matchedFood != null) {
                    NoteBlock(
                        ruleColor = MaterialTheme.colorScheme.primary,
                        tagColor = MaterialTheme.colorScheme.primary,
                        tag = "食物库里已有",
                        statement = {
                            Text(
                                text = "「${matchedFood.name}」",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        hint = "${formatAmount(matchedFood.caloriesPer100g)} kcal/100g · " +
                            "碳水 ${formatAmount(matchedFood.carbsPer100g)} g · " +
                            "蛋白质 ${formatAmount(matchedFood.proteinPer100g)} g · " +
                            "脂肪 ${formatAmount(matchedFood.fatPer100g)} g\n" +
                            "保存后直接记到它名下，不会新建重复食物"
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                JournalUnderlineField(
                    label = "食用数量",
                    value = amountGrams,
                    onValueChange = { amountGrams = it },
                    unit = "g",
                    textStyle = DialogFieldStyle
                )

                SectionHead(
                    title = "营养成分 · 每100克",
                    latin = "MACROS",
                    modifier = Modifier.padding(top = 28.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                JournalUnderlineField(
                    label = "碳水 · G/100G",
                    value = carbs,
                    onValueChange = { carbs = it },
                    textStyle = DialogFieldStyle
                )
                Spacer(modifier = Modifier.height(14.dp))
                JournalUnderlineField(
                    label = "蛋白质 · G/100G",
                    value = protein,
                    onValueChange = { protein = it },
                    textStyle = DialogFieldStyle
                )
                Spacer(modifier = Modifier.height(14.dp))
                JournalUnderlineField(
                    label = "脂肪 · G/100G",
                    value = fat,
                    onValueChange = { fat = it },
                    textStyle = DialogFieldStyle
                )
                Spacer(modifier = Modifier.height(14.dp))
                JournalUnderlineField(
                    label = "钠 · MG/100G",
                    value = sodium,
                    onValueChange = { sodium = it },
                    textStyle = DialogFieldStyle
                )
                Spacer(modifier = Modifier.height(14.dp))
                JournalUnderlineField(
                    label = "热量 · KCAL/100G",
                    value = calories,
                    onValueChange = {
                        calories = it
                        caloriesEditedManually = true
                    },
                    keyboardType = KeyboardType.Number,
                    supportingText = "根据三大营养素自动推算，也可以手动改",
                    textStyle = DialogFieldStyle
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
                        .padding(top = 30.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        enabled = foodName.isNotBlank() &&
                            amountValue > 0f &&
                            caloriesPer100g > 0f,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = if (matchedFood != null) "记到「${matchedFood.name}」" else "保存",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** 换算账目行：衬线大数字给出本次总热量，宏量小注跟在后面。 */
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

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Column {
            Text(
                text = "本次摄入",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "${amountGrams.roundToInt()} g",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 6.dp, bottom = 3.dp)
                )
                Text(
                    text = "≈ ${ledgerNumber((caloriesPer100g * ratio).roundToInt())}",
                    style = JournalStatement,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "kcal",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                )
            }
            Text(
                text = "碳水 ${formatAmount(carbsPer100g * ratio)} g · " +
                    "蛋白质 ${formatAmount(proteinPer100g * ratio)} g · " +
                    "脂肪 ${formatAmount(fatPer100g * ratio)} g · " +
                    "钠 ${formatAmount(sodiumPer100g * ratio)} mg",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp)
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
