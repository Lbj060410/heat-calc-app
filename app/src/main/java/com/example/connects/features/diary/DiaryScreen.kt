package com.example.connects.features.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.RowScope
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp)
            ) {
                Text(
                    text = "饮食日记",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "记录每一天的饮食",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 日历组件
            item {
                CalendarPicker(
                    selectedDate = uiState.selectedDate,
                    onDateSelected = { viewModel.selectDate(it) }
                )
            }
            
            // 日期标题和总热量
            item {
                DateHeader(
                    date = uiState.selectedDate,
                    totalCalories = uiState.totalCalories,
                    formattedDate = viewModel.getFormattedDate(uiState.selectedDate),
                    dayOfWeek = viewModel.getDayOfWeek(uiState.selectedDate)
                )
            }
            
            // 食物列表
            if (uiState.foodEntries.isEmpty()) {
                item {
                    EmptyState()
                }
            } else {
                items(uiState.foodEntries) { entry ->
                    FoodEntryCard(entry = entry)
                }
            }
            
            // 底部间距
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CalendarPicker(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 月份导航
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth = currentMonth.minusMonths(1)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上个月")
                }
                
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                IconButton(onClick = {
                    currentMonth = currentMonth.plusMonths(1)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下个月")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 星期标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { dayName ->
                    Text(
                        text = dayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 日期网格
            val firstDayOfMonth = currentMonth.atDay(1)
            val lastDayOfMonth = currentMonth.atEndOfMonth()
            val daysInMonth = lastDayOfMonth.dayOfMonth
            
            // 计算起始偏移（周日为0）
            val startOffset = (firstDayOfMonth.dayOfWeek.value % 7)
            
            var currentDay = 1
            for (week in 0..5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (dayOfWeek in 0..6) {
                        val dayIndex = week * 7 + dayOfWeek
                        
                        if (dayIndex < startOffset || currentDay > daysInMonth) {
                            // 空白占位
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            val date = currentMonth.atDay(currentDay)
                            val isSelected = date == selectedDate
                            val isToday = date == LocalDate.now()
                            
                            DayCell(
                                day = currentDay,
                                isSelected = isSelected,
                                isToday = isToday,
                                onClick = { onDateSelected(date) }
                            )
                            
                            currentDay++
                        }
                    }
                }
                
                if (currentDay > daysInMonth) break
            }
        }
    }
}

@Composable
fun RowScope.DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    
    val textColor = when {
        isSelected -> Color.White
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun DateHeader(
    date: LocalDate,
    totalCalories: Int,
    formattedDate: String,
    dayOfWeek: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = dayOfWeek,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "总热量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "$totalCalories kcal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun FoodEntryCard(entry: com.example.connects.domain.model.FoodEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.foodName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.amountGrams.toInt()}g · ${entry.mealType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "+${entry.totalCalories.toInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "这天还没有饮食记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "点击主页的 + 按钮添加食物",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
