package com.example.connects.features.home

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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

private const val MEAL_BREAKFAST = "早餐"
private const val MEAL_LUNCH = "午餐"
private const val MEAL_DINNER = "晚餐"
private const val MEAL_SNACK = "加餐"

@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCustomAddDialog by remember { mutableStateOf(false) }
    var scannedNutritionData by remember { mutableStateOf<ScannedNutritionData?>(null) }
    var scannedRowCount by remember { mutableStateOf(0) }
    var scannedColCount by remember { mutableStateOf(0) }
    var scannedTableJson by remember { mutableStateOf<String?>(null) }
    var pendingParsedData by remember { mutableStateOf<ScannedNutritionData?>(null) }
    var showScanConfirmDialog by remember { mutableStateOf(false) }
    var selectedMealType by remember { mutableStateOf(MEAL_BREAKFAST) }
    var showFoodPicker by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scannedText = result.data?.getStringExtra("scanned_text")
            scannedRowCount = result.data?.getIntExtra("scanned_rows", 0) ?: 0
            scannedColCount = result.data?.getIntExtra("scanned_cols", 0) ?: 0
            scannedTableJson = result.data?.getStringExtra("scanned_table_json")

            if (!scannedText.isNullOrEmpty()) {
                viewModel.parseNutritionLabel(scannedText) { parsedData ->
                    if (parsedData.confidence < 0.55f || parsedData.warnings.isNotEmpty()) {
                        pendingParsedData = parsedData
                        showScanConfirmDialog = true
                    } else {
                        scannedNutritionData = parsedData
                        showCustomAddDialog = true
                    }
                }
                if (scannedRowCount > 0 && scannedColCount > 0) {
                    Toast.makeText(
                        context,
                        "识别到表格：${scannedRowCount}行 ${scannedColCount}列",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (!scannedTableJson.isNullOrEmpty()) {
                    Toast.makeText(context, "已捕获表格结构", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "扫描失败，未识别到文本", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showCustomAddDialog) {
        AddFoodDialog(
            onDismiss = {
                showCustomAddDialog = false
                scannedNutritionData = null
                pendingParsedData = null
                showScanConfirmDialog = false
                scannedRowCount = 0
                scannedColCount = 0
                scannedTableJson = null
            },
            onSave = { data ->
                viewModel.addCustomFoodAndEntry(data, selectedMealType)
                showCustomAddDialog = false
                scannedNutritionData = null
                pendingParsedData = null
                showScanConfirmDialog = false
                scannedRowCount = 0
                scannedColCount = 0
                scannedTableJson = null
            },
            onScanClick = {
                val intent = Intent(context, CameraScanActivity::class.java)
                cameraLauncher.launch(intent)
            },
            initialData = scannedNutritionData
        )
    }

    if (showScanConfirmDialog && pendingParsedData != null) {
        AlertDialog(
            onDismissRequest = {
                showScanConfirmDialog = false
                pendingParsedData = null
            },
            title = { Text("识别结果请确认") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("置信度：${(pendingParsedData!!.confidence * 100).toInt()}%")
                    pendingParsedData!!.warnings.forEach { warn ->
                        Text("• $warn")
                    }
                    Text("是否继续并进入编辑页面？")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scannedNutritionData = pendingParsedData
                        pendingParsedData = null
                        showScanConfirmDialog = false
                        showCustomAddDialog = true
                    }
                ) { Text("继续") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingParsedData = null
                        showScanConfirmDialog = false
                    }
                ) { Text("取消") }
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
                        text = "Today's Summary",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.remainingCalories} kcal left",
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
                        NutrientBar(label = "Carbs", state = uiState.carbs)
                        NutrientBar(label = "Protein", state = uiState.protein)
                        NutrientBar(label = "Fat", state = uiState.fat)
                        NutrientBar(label = "Fiber", state = uiState.fiber)
                        NutrientBar(label = "Sodium", state = uiState.sodium, unit = "mg")
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Text("Meals", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
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
                                    text = "${meal.totalCalories} kcal · ${meal.count} items",
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
