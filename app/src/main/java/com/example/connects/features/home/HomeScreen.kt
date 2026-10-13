package com.example.connects.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connects.domain.model.MealType

@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerState by viewModel.foodPickerUiState.collectAsState()
    var showCustomAddDialog by remember { mutableStateOf(false) }
    var selectedMealType by remember { mutableStateOf(MealType.BREAKFAST.label) }
    var showFoodPicker by remember { mutableStateOf(false) }

    if (showCustomAddDialog) {
        AddFoodDialog(
            knownFoods = pickerState.allFoods,
            onDismiss = {
                showCustomAddDialog = false
            },
            onSave = { data ->
                viewModel.addCustomFoodAndEntry(data, selectedMealType)
                showCustomAddDialog = false
            }
        )
    }

    if (showFoodPicker) {
        FoodPickerScreen(
            viewModel = viewModel,
            onBack = { showFoodPicker = false },
            onCreateFoodClick = {
                showCustomAddDialog = true
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "今日汇总",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "剩余 ${uiState.remainingCalories} kcal",
                        fontSize = 18.sp,
                        color = if (uiState.remainingCalories >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        NutrientBar(label = "碳水", state = uiState.carbs)
                        NutrientBar(label = "蛋白质", state = uiState.protein)
                        NutrientBar(label = "脂肪", state = uiState.fat)
                        NutrientBar(label = "钠", state = uiState.sodium, unit = "mg")
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Text("餐次", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                }
            }

            item {
                val meals = uiState.mealSummaries
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    meals.forEach { meal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedMealType = meal.mealType
                                viewModel.prepareFoodPicker(meal.mealType)
                                showFoodPicker = true
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = meal.mealType,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "${meal.totalCalories} kcal · ${meal.count} 项",
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(60.dp)) }
        }
    }
}

@Composable
fun NutrientBar(
    label: String,
    state: HomeViewModel.NutrientState,
    modifier: Modifier = Modifier,
    unit: String = "g"
) {
    val barColor = if (state.isExceeded) Color(0xFFF44336) else Color(0xFF4CAF50)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${state.consumed.toInt()} / ${state.target.toInt()} $unit",
                fontSize = 12.sp,
                color = if (state.isExceeded) Color(0xFFF44336) else Color.Gray,
                fontWeight = if (state.isExceeded) FontWeight.Bold else FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { state.progress.coerceAtMost(1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round
        )
    }
}
