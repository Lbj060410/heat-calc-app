package com.example.connects.features.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connects.core.util.formatAmount
import com.example.connects.domain.model.FoodEntry
import com.example.connects.domain.model.MealType
import com.example.connects.ui.components.hairline
import com.example.connects.ui.theme.JournalTitle

/**
 * 某一餐的记录详情。从日志页的餐次行点进来，列出这一餐吃了什么，可以逐条删除。
 *
 * 数据直接来自 DiaryViewModel 当日分组里的对应那一组（不是新查询），所以这里删掉一条，
 * Room 的 Flow 会重新发出，日志页的餐次小计和当日总摄入会跟着一起更新。
 *
 * 版式沿用批准稿：整页无卡片，账目行 + 发丝分隔线。
 */
@Composable
fun MealDetailScreen(
    mealType: MealType,
    entries: List<FoodEntry>,
    totalCalories: Int,
    onBack: () -> Unit,
    onDelete: (FoodEntry) -> Unit
) {
    // 待确认删除的记录，非空时弹出二次确认框
    var entryToDelete by remember { mutableStateOf<FoodEntry?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 外层 MainTabsScreen 的 Scaffold 已经加过状态栏 inset，这里不再叠一遍
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 18.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = mealType.label,
                    style = JournalTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$totalCalories kcal · ${entries.size} 项",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "这顿还没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                    FoodEntryRow(
                        entry = entry,
                        showDivider = index < entries.lastIndex,
                        onDelete = { entryToDelete = entry }
                    )
                }
            }
        }
    }

    entryToDelete?.let { entry ->
        DeleteEntryDialog(
            entry = entry,
            onConfirm = {
                onDelete(entry)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null }
        )
    }
}

/** 账目行：食物名 + 克数，右侧热量 + 删除，行间发丝线。 */
@Composable
private fun FoodEntryRow(
    entry: FoodEntry,
    showDivider: Boolean,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 餐次已经在标题栏上了，这里不再重复
                Text(
                    text = entry.foodName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatAmount(entry.amountGrams)} g",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = "${entry.totalCalories.toInt()} kcal",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除${entry.foodName}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.hairline)
            )
        }
    }
}

@Composable
private fun DeleteEntryDialog(
    entry: FoodEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这条记录？") },
        text = {
            Text(
                text = "${entry.foodName} · ${formatAmount(entry.amountGrams)} g · " +
                    "${entry.totalCalories.toInt()} kcal\n删除后无法恢复。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
