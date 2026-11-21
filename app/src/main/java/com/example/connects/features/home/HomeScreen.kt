package com.example.connects.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connects.domain.model.MealType
import com.example.connects.ui.components.DashedAddButton
import com.example.connects.ui.components.DottedLeader
import com.example.connects.ui.components.RulerMeter
import com.example.connects.ui.components.SectionHead
import com.example.connects.ui.components.UserAvatar
import com.example.connects.ui.components.formatClockTime
import com.example.connects.ui.components.formatDateLine
import com.example.connects.ui.components.hairline
import com.example.connects.ui.components.ledgerAmount
import com.example.connects.ui.components.ledgerMilligrams
import com.example.connects.ui.components.ledgerNumber
import com.example.connects.ui.theme.JournalHero
import com.example.connects.ui.theme.JournalMeal
import com.example.connects.ui.theme.JournalTitle
import com.example.connects.ui.theme.SerifFamily
import java.time.LocalDate

/**
 * 首页 —— 账目式信息版。
 *
 * 一个主角：剩余热量做成特大衬线数字，下面一条刻度尺显示今日进度；
 * 碳水/蛋白/脂肪/钠是点线账目；餐次像菜单一样点线引到热量，
 * 每顿下面一行写今天到底吃了什么。整页没有卡片，只有发丝分隔线。
 */
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

    // 系统返回键：先关掉食物选择页，而不是直接退出应用。
    // 上层弹窗（分量 / 自定义食物）是 Dialog，自带返回关闭，不经过这里。
    BackHandler(enabled = showFoodPicker) {
        showFoodPicker = false
    }

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
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 外层 MainTabsScreen 的 Scaffold 已经加过状态栏 inset，这里不再叠一遍
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            HomeHeader(
                dateLine = formatDateLine(remember { LocalDate.now() }),
                userName = uiState.userName,
                avatarPath = uiState.avatarPath
            )

            HeroRemaining(
                remaining = uiState.remainingCalories,
                consumed = uiState.consumedCalories,
                target = uiState.targetCalories
            )

            MacroLedger(uiState = uiState)

            SectionHead(title = "今日餐次", latin = "MEALS", modifier = Modifier.padding(top = 26.dp))

            val entriesByMeal = remember(uiState.todayEntries) {
                MealType.ordered.associateWith { meal ->
                    uiState.todayEntries.filter { entry ->
                        MealType.labelOf(entry.mealType) == meal.label
                    }
                }
            }
            MealType.ordered.forEachIndexed { index, meal ->
                val entries = entriesByMeal[meal].orEmpty()
                MealRow(
                    meal = meal,
                    entries = entries,
                    showDivider = index > 0,
                    onClick = {
                        selectedMealType = meal.label
                        viewModel.prepareFoodPicker(meal.label)
                        showFoodPicker = true
                    }
                )
            }

            DashedAddButton(
                text = "＋ 添加食物",
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    selectedMealType = MealType.SNACK.label
                    viewModel.prepareFoodPicker(MealType.SNACK.label)
                    showFoodPicker = true
                }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** 页眉：日期行 + 特大「今日」+ 头像。 */
@Composable
private fun HomeHeader(dateLine: String, userName: String, avatarPath: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLine,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "今日",
                style = JournalTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        UserAvatar(
            avatarPath = avatarPath,
            initial = userName.firstOrNull()?.toString() ?: "记"
        )
    }
}

/** 剩余热量：特大衬线数字 + 刻度尺 + 已摄入/目标注脚。 */
@Composable
private fun HeroRemaining(remaining: Int, consumed: Int, target: Int) {
    val exceeded = remaining < 0
    val numberColor = if (exceeded) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            text = if (exceeded) "今日已超支" else "今日还可摄入",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = ledgerNumber(remaining),
                style = JournalHero,
                color = numberColor
            )
            Column(modifier = Modifier.padding(start = 10.dp, bottom = 12.dp)) {
                Text(
                    text = "kcal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SerifFamily,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "REMAINING",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        RulerMeter(
            progress = if (target > 0) consumed.toFloat() / target else 0f,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "已摄入 ${ledgerNumber(consumed)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "目标 ${ledgerNumber(target)} kcal",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 碳水/蛋白质/脂肪/钠：点线账目，右端百分比，超限变陶土色。 */
@Composable
private fun MacroLedger(uiState: HomeViewModel.HomeUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        MacroRow(label = "碳水", value = ledgerAmount(uiState.carbs.consumed, uiState.carbs.target), pct = uiState.carbs)
        MacroRow(label = "蛋白质", value = ledgerAmount(uiState.protein.consumed, uiState.protein.target), pct = uiState.protein)
        MacroRow(label = "脂肪", value = ledgerAmount(uiState.fat.consumed, uiState.fat.target), pct = uiState.fat)
        MacroRow(
            label = "钠",
            value = ledgerMilligrams(uiState.sodium.consumed, uiState.sodium.target),
            pct = uiState.sodium
        )
    }
}

@Composable
private fun MacroRow(label: String, value: String, pct: HomeViewModel.NutrientState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        DottedLeader(modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(
            text = value,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${(pct.progress * 100).toInt()}%",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (pct.isExceeded) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(start = 10.dp)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.hairline)
    )
}

/** 菜单式餐次行：餐名 + 点线 + 热量；下面一行食物明细。 */
@Composable
private fun MealRow(
    meal: MealType,
    entries: List<com.example.connects.domain.model.FoodEntry>,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 13.dp, bottom = 12.dp)
    ) {
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.hairline)
            )
            Spacer(modifier = Modifier.padding(top = 12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = meal.label,
                style = JournalMeal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entries.isNotEmpty()) {
                Text(
                    text = formatClockTime(entries.maxOf { it.createdAt }),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            DottedLeader(modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            if (entries.isEmpty()) {
                Text(
                    text = "未记录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = ledgerNumber(entries.sumOf { it.totalCalories.toInt() }),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "kcal",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
        if (entries.isNotEmpty()) {
            FoodItemsLine(entries)
        }
    }
}

/** 一行写清这顿吃了什么：「燕麦牛奶碗 · 白煮蛋 ×2 · 美式咖啡」。 */
@Composable
private fun FoodItemsLine(entries: List<com.example.connects.domain.model.FoodEntry>) {
    val summary = remember(entries) {
        val names = entries.map { it.foodName }.distinct()
        val shown = names.take(3)
        val suffix = if (names.size > 3) " 等 ${names.size} 项" else ""
        (shown.joinToString(" · ") + suffix).ifBlank { "—" }
    }
    Text(
        text = summary,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 5.dp)
    )
}
