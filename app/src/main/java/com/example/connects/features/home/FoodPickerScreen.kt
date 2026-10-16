package com.example.connects.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.connects.domain.model.FoodCategory
import com.example.connects.ui.components.DashedAddButton
import com.example.connects.ui.components.JournalUnderlineField
import com.example.connects.ui.components.hairline
import com.example.connects.ui.components.ledgerNumber
import com.example.connects.ui.theme.JournalInput
import com.example.connects.ui.theme.JournalStatement
import com.example.connects.ui.theme.JournalTitle
import com.example.connects.ui.theme.SerifFamily
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

/** 弹窗表单里下划线输入的字号：比首页大数字小一号。 */
private val DialogFieldStyle = JournalInput.copy(fontSize = 22.sp, lineHeight = 28.sp)

/**
 * 食物选择页 —— 手账风：衬线标题、分类做成左侧文字列表、
 * 食物是账目行 + 发丝线，整页没有卡片。
 */
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
        containerColor = MaterialTheme.colorScheme.background,
        // 外层 MainTabsScreen 的 Scaffold 已经加过状态栏 inset，这里不再叠一遍
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 18.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "添加食物 · ${uiState.mealType}",
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        bottomBar = {
            DashedAddButton(
                text = "＋ 新增自定义食物",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 14.dp),
                onClick = onCreateFoodClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 22.dp)
        ) {
            JournalUnderlineField(
                label = "搜索常见食物",
                value = uiState.searchQuery,
                onValueChange = viewModel::onFoodSearchQueryChange,
                keyboardType = KeyboardType.Text,
                textStyle = DialogFieldStyle
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    foodCategoryTabs.forEach { tab ->
                        val selected = tab.key == uiState.selectedCategory
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onFoodCategoryChange(tab.key) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.hairline
                                    )
                            )
                            Text(
                                text = tab.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                fontFamily = if (selected) SerifFamily else null,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载中...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (uiState.visibleFoods.isEmpty()) {
                            item {
                                Text(
                                    text = "没有匹配的食物",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 20.dp, start = 4.dp)
                                )
                            }
                        }
                        itemsIndexed(uiState.visibleFoods, key = { _, food -> food.id }) { index, food ->
                            FoodRow(
                                food = food,
                                showDivider = index < uiState.visibleFoods.lastIndex,
                                onClick = { selectedFood = food },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(food.id, food.isFavorite)
                                },
                                onEdit = if (food.isCustom) {
                                    { editingFood = food }
                                } else null,
                                onDelete = if (food.isCustom) {
                                    { viewModel.deleteCustomFood(food.id) }
                                } else null
                            )
                        }
                        item { Spacer(modifier = Modifier.height(20.dp)) }
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

/** 食物账目行：名称 + 每100g热量，可收藏/编辑/删除，行间 发丝线。 */
@Composable
private fun FoodRow(
    food: HomeViewModel.QuickFoodItem,
    showDivider: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${food.caloriesPer100g.toInt()} kcal/100g",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = if (food.isFavorite) "取消收藏" else "收藏",
                    tint = if (food.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.hairline)
            )
        }
    }
}

/** 分量弹窗：纸感卡片 + 下划线输入 + 衬线大数字热量换算。 */
@Composable
private fun PortionDialog(
    food: HomeViewModel.QuickFoodItem,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var gramsText by remember { mutableStateOf("100") }
    val grams = gramsText.toFloatOrNull() ?: 0f

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "输入食用分量",
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${food.name} · ${food.caloriesPer100g.roundToInt()} kcal/100g",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                JournalUnderlineField(
                    label = "克数 · G",
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    placeholder = "100",
                    modifier = Modifier.padding(top = 20.dp)
                )

                if (grams > 0f) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        Text(
                            text = "≈",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
                        )
                        Text(
                            text = ledgerNumber(
                                (food.caloriesPer100g * grams / 100f).roundToInt()
                            ),
                            style = JournalStatement,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "kcal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { if (grams > 0f) onConfirm(grams) },
                        enabled = grams > 0f,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/** 编辑自定义食物：纸感卡片 + 六个下划线输入 + 墨色保存。 */
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
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "编辑食物",
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(18.dp))
                JournalUnderlineField(
                    label = "名称",
                    value = name,
                    onValueChange = { name = it },
                    keyboardType = KeyboardType.Text,
                    textStyle = DialogFieldStyle
                )
                Spacer(modifier = Modifier.height(14.dp))
                JournalUnderlineField(
                    label = "热量 · KCAL/100G",
                    value = calories,
                    onValueChange = { calories = it },
                    keyboardType = KeyboardType.Number,
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
                    label = "碳水 · G/100G",
                    value = carbs,
                    onValueChange = { carbs = it },
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
