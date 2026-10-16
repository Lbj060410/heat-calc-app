package com.example.connects.features.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.connects.domain.model.MealType
import com.example.connects.ui.components.DottedLeader
import com.example.connects.ui.components.SectionHead
import com.example.connects.ui.components.formatClockTime
import com.example.connects.ui.components.formatDateLine
import com.example.connects.ui.components.hairline
import com.example.connects.ui.components.ledgerNumber
import com.example.connects.ui.theme.JournalBig
import com.example.connects.ui.theme.JournalMeal
import com.example.connects.ui.theme.JournalTitle
import com.example.connects.ui.theme.SerifFamily
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日志 —— 一周横条版。
 *
 * 月份缩成一条：每天直接看到摄入量，选中当天即在下方给出账目汇总（大衬线数字 +
 * 与目标的差值 + 宏量小注）和四餐明细；无记录的日子留白，不硬凑数字。
 */
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val weekDays by viewModel.weekDays.collectAsState()
    val targetCalories by viewModel.targetCalories.collectAsState()
    var openedMeal by remember { mutableStateOf<MealType?>(null) }

    // 系统返回键：从餐次详情回到日志，而不是直接退出应用。
    BackHandler(enabled = openedMeal != null) {
        openedMeal = null
    }

    // 点某餐进入详情页。沿用首页 FoodPickerScreen 的做法：在同一个 Compose 树里整屏替换，
    // 不新开导航路由，DiaryViewModel 实例也保持不变，返回时周条和选中日期都还在。
    val meal = openedMeal
    if (meal != null) {
        val group = uiState.groups.firstOrNull { it.mealType == meal }
        MealDetailScreen(
            mealType = meal,
            entries = group?.entries.orEmpty(),
            totalCalories = group?.totalCalories ?: 0,
            onBack = { openedMeal = null },
            onDelete = { viewModel.deleteEntry(it) }
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
                .padding(horizontal = 22.dp)
        ) {
            Text(
                text = "日志",
                style = JournalTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "翻看任意一天的饮食与热量。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )

            WeekStripCard(
                weekDays = weekDays,
                selectedDate = uiState.selectedDate,
                onSelectDate = viewModel::selectDate,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek
            )

            DaySummary(
                selectedDate = uiState.selectedDate,
                totalCalories = uiState.totalCalories,
                targetCalories = targetCalories,
                groups = uiState.groups
            )

            uiState.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SectionHead(title = "当日餐次", latin = "MEALS", modifier = Modifier.padding(top = 24.dp))
            uiState.groups.forEachIndexed { index, group ->
                DiaryMealRow(
                    group = group,
                    showDivider = index > 0,
                    onClick = { openedMeal = group.mealType }
                )
            }
            if (uiState.isEmpty) {
                Text(
                    text = "这一天还没有记录。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/** 周条：‹ 月份 · 日期区间 › + 7 个日cell（日号 / 星期圈 / 当日千卡）。 */
@Composable
private fun WeekStripCard(
    weekDays: List<WeekDayUi>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit
) {
    val today = remember { LocalDate.now() }
    val rangeLabel = remember(weekDays) {
        if (weekDays.isEmpty()) {
            ""
        } else {
            val first = weekDays.first().date
            val last = weekDays.last().date
            val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
            "${first.format(monthFormatter)} · ${first.dayOfMonth}–${last.dayOfMonth}日"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPreviousWeek)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
            Text(
                text = rangeLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SerifFamily,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "›",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNextWeek)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            weekDays.forEach { day ->
                val isSelected = day.date == selectedDate
                val isToday = day.date == today
                val dimmed = day.isFuture

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.background
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { onSelectDate(day.date) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${day.date.dayOfMonth}",
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = when {
                            isToday -> MaterialTheme.colorScheme.primary
                            dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else androidx.compose.ui.graphics.Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = weekDayChar(day.date),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontFamily = if (isSelected) SerifFamily else null,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    Text(
                        text = if (day.isFuture) "—" else ledgerNumber(day.kcal),
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            day.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

private fun weekDayChar(date: LocalDate): String {
    val names = listOf("日", "一", "二", "三", "四", "五", "六")
    return names[date.dayOfWeek.value % 7]
}

/** 当日账目汇总：大衬线数字 + 与目标差值 + 宏量小注。 */
@Composable
private fun DaySummary(
    selectedDate: LocalDate,
    totalCalories: Int,
    targetCalories: Int,
    groups: List<DiaryMealGroup>
) {
    val allEntries = remember(groups) { groups.flatMap { it.entries } }
    val delta = targetCalories - totalCalories
    val macroNote = remember(allEntries) {
        val carbs = allEntries.sumOf { it.totalCarbs.toDouble() }
        val protein = allEntries.sumOf { it.totalProtein.toDouble() }
        val fat = allEntries.sumOf { it.totalFat.toDouble() }
        val sodium = allEntries.sumOf { it.sodium.toDouble() }
        buildString {
            append("碳水 ${String.format("%.0f", carbs)} g")
            append(" · 蛋白质 ${String.format("%.0f", protein)} g")
            append(" · 脂肪 ${String.format("%.0f", fat)} g")
            append(" · 钠 ${String.format("%,.0f", sodium)} mg")
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(
            text = formatDateLine(selectedDate),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = ledgerNumber(totalCalories),
                style = JournalBig,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "千卡 · 总摄入",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 9.dp, bottom = 8.dp)
            )
        }
        Text(
            text = if (delta >= 0) "较目标低 ${ledgerNumber(delta)} 千卡" else "较目标超出 ${ledgerNumber(-delta)} 千卡",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (delta >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = macroNote,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** 菜单式餐次行：餐名 + 时间与项数 + 点线 + 热量。 */
@Composable
private fun DiaryMealRow(
    group: DiaryMealGroup,
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
                text = group.mealType.label,
                style = JournalMeal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (group.entries.isNotEmpty()) {
                Text(
                    text = "${formatClockTime(group.entries.maxOf { it.createdAt })} · ${group.entries.size} 项",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            DottedLeader(modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            if (group.entries.isEmpty()) {
                Text(
                    text = "未记录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = ledgerNumber(group.totalCalories),
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
    }
}
