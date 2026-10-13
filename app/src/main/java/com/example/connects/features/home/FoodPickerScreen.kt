package com.example.connects.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.connects.domain.model.FoodCategory
import kotlin.math.roundToInt

private data class FoodCategoryTab(val key: String, val label: String)

private val foodCategoryTabs = listOf(
    FoodCategoryTab(FoodCategory.ALL, "全部"),
    FoodCategoryTab(FoodCategory.CUSTOM, "自定义"),
    FoodCategoryTab(FoodCategory.STAPLE, "主食"),
    FoodCategoryTab(FoodCategory.VEGETABLE, "蔬菜"),
    FoodCategoryTab(FoodCategory.MEAT_DAIRY, "肉/奶"),
    FoodCategoryTab(FoodCategory.FRUIT, "水果"),
    FoodCategoryTab(FoodCategory.SNACK, "零食")
)

@Composable
fun FoodPickerScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onCreateFoodClick: () -> Unit = {}
) {
    val uiState by viewModel.foodPickerUiState.collectAsState()
    var selectedFood by remember { mutableStateOf<HomeViewModel.QuickFoodItem?>(null) }
    var editingFood by remember { mutableStateOf<HomeViewModel.QuickFoodItem?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "添加食物 · ${uiState.mealType}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onCreateFoodClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("新增食物")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onFoodSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索常见食物") }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    foodCategoryTabs.forEach { tab ->
                        val selected = tab.key == uiState.selectedCategory
                        OutlinedButton(
                            onClick = { viewModel.onFoodCategoryChange(tab.key) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text(tab.label)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("加载中...")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.visibleFoods) { food ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedFood = food }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(food.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${food.caloriesPer100g.toInt()} kcal/100g",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    IconButton(onClick = { viewModel.toggleFavorite(food.id, food.isFavorite) }) {
                                        if (food.isFavorite) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = "取消收藏",
                                                tint = Color(0xFFFFC107)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = "收藏",
                                                tint = Color.LightGray
                                            )
                                        }
                                    }
                                    if (food.isCustom) {
                                        IconButton(onClick = { editingFood = food }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomFood(food.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.visibleFoods.isEmpty()) {
                            item {
                                Text("没有匹配的食物", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }

    selectedFood?.let { food ->
        PortionDialog(
            food = food,
            onDismiss = { selectedFood = null },
            onConfirm = { grams ->
                viewModel.addFoodEntryFromQuickItem(food, grams, uiState.mealType)
                selectedFood = null
                onBack()
            }
        )
    }

    editingFood?.let { food ->
        EditCustomFoodDialog(
            initial = food,
            onDismiss = { editingFood = null },
            onSave = { data ->
                viewModel.updateCustomFood(data)
                editingFood = null
            }
        )
    }
}

@Composable
private fun PortionDialog(
    food: HomeViewModel.QuickFoodItem,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var gramsText by remember { mutableStateOf("100") }
    val grams = gramsText.toFloatOrNull() ?: 0f

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("输入食用分量", fontWeight = FontWeight.Bold)
                Text(
                    text = "${food.name} · ${food.caloriesPer100g.roundToInt()} kcal/100g",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    singleLine = true,
                    label = { Text("克数(g)") },
                    // 允许小数，改吃 150.5 g 也能填
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (grams > 0f) {
                    Text(
                        text = "≈ ${(food.caloriesPer100g * grams / 100f).roundToInt()} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { if (grams > 0f) onConfirm(grams) },
                        enabled = grams > 0f
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCustomFoodDialog(
    initial: HomeViewModel.QuickFoodItem,
    onDismiss: () -> Unit,
    onSave: (HomeViewModel.EditableFoodData) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var calories by remember(initial.id) { mutableStateOf(initial.caloriesPer100g.toString()) }
    var protein by remember(initial.id) { mutableStateOf(initial.proteinPer100g.toString()) }
    var carbs by remember(initial.id) { mutableStateOf(initial.carbsPer100g.toString()) }
    var fat by remember(initial.id) { mutableStateOf(initial.fatPer100g.toString()) }
    var sodium by remember(initial.id) { mutableStateOf(initial.sodiumPer100g.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("编辑食物", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it },
                    label = { Text("热量/100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("蛋白质/100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("碳水/100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("脂肪/100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = sodium,
                    onValueChange = { sodium = it },
                    label = { Text("钠/100g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            onSave(
                                HomeViewModel.EditableFoodData(
                                    id = initial.id,
                                    name = name,
                                    category = initial.category,
                                    caloriesPer100g = calories.toFloatOrNull() ?: 0f,
                                    proteinPer100g = protein.toFloatOrNull() ?: 0f,
                                    carbsPer100g = carbs.toFloatOrNull() ?: 0f,
                                    fatPer100g = fat.toFloatOrNull() ?: 0f,
                                    sodiumPer100g = sodium.toFloatOrNull() ?: 0f
                                )
                            )
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
