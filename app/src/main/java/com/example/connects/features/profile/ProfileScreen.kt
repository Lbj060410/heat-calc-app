package com.example.connects.features.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

private val genderOptions = listOf(
    "male" to "男",
    "female" to "女"
)

private val goalOptions = listOf(
    "lose" to "减脂",
    "gain" to "增肌",
    "maintain" to "维持"
)

private val activityOptions = listOf(
    "sedentary" to "久坐",
    "light" to "轻度",
    "moderate" to "中度",
    "active" to "活跃",
    "very_active" to "高强度"
)

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showDetailPage by rememberSaveable { mutableStateOf(false) }
    var showWeightRecordPage by rememberSaveable { mutableStateOf(false) }

    if (showWeightRecordPage) {
        WeightRecordEntryPage(
            onBack = { showWeightRecordPage = false }
        )
        return
    }

    if (showDetailPage) {
        ProfileDetailPage(
            uiState = uiState,
            onBack = { showDetailPage = false },
            onStartEditing = viewModel::startEditing,
            onCancelEditing = viewModel::cancelEditing,
            onRangeChange = viewModel::selectChartRange,
            onSave = { name, gender, height, weight, age, goal, activity ->
                viewModel.updateUserProfile(
                    name = name,
                    gender = gender,
                    height = height,
                    currentWeight = weight,
                    age = age,
                    goal = goal,
                    activityLevel = activity
                )
            }
        )
    } else {
        ProfileLandingPage(
            uiState = uiState,
            onOpenDetails = { showDetailPage = true },
            onOpenWeightRecord = { showWeightRecordPage = true },
            onRangeChange = viewModel::selectChartRange
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProfileLandingPage(
    uiState: ProfileUiState,
    onOpenDetails: () -> Unit,
    onOpenWeightRecord: () -> Unit,
    onRangeChange: (WeightChartRange) -> Unit
) {
    Scaffold(

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading && uiState.userProfile == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            val profile = uiState.userProfile
            if (profile == null) {
                Text("个人资料为空")
                return@Column
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDetails),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Avatar(initial = profile.name.firstOrNull()?.uppercaseChar()?.toString() ?: "U")

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "点击查看和编辑基本信息与目标",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前目标", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${profile.dailyCaloricTarget} kcal/day",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onOpenWeightRecord,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("记录体重")
            }

            // 体重趋势图表（已从详情页移至此处）
            WeightTrendCard(
                records = uiState.weightHistory,
                selectedRange = uiState.selectedRange,
                firstRecordDate = uiState.firstWeightRecordDate,
                referenceWeight = profile.weightKg,
                onRangeChange = onRangeChange
            )
        }
    }
}

@Composable
private fun WeightRecordEntryPage(
    onBack: () -> Unit
) {
    val viewModel: com.example.connects.features.weight.WeightViewModel = hiltViewModel()
    com.example.connects.features.weight.WeightRecordScreen(
        onBack = onBack,
        viewModel = viewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDetailPage(
    uiState: ProfileUiState,
    onBack: () -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onRangeChange: (WeightChartRange) -> Unit,
    onSave: (String, String, Float, Float, Int, String, String) -> Unit
) {
    val profile = uiState.userProfile

    var name by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("male") }
    var age by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("maintain") }
    var activity by rememberSaveable { mutableStateOf("moderate") }

    LaunchedEffect(profile, uiState.isEditing) {
        if (profile != null && !uiState.isEditing) {
            name = profile.name
            gender = profile.gender
            age = profile.age.toString()
            height = profile.heightCm.toString()
            weight = profile.weightKg.toString()
            goal = profile.goal
            activity = profile.activityLevel
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        IconButton(onClick = onStartEditing) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("个人资料为空")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Avatar(initial = (name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("用户名", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (uiState.isEditing) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 注意：WeightTrendCard 已从详情页移除（现在只在首页显示）

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("基本信息与健康目标", fontWeight = FontWeight.Bold)

                    OptionDropdownField(
                        label = "性别",
                        options = genderOptions,
                        value = gender,
                        enabled = uiState.isEditing,
                        onValueSelected = { gender = it }
                    )

                    OutlinedTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("年龄") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("身高 (cm)") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("体重 (kg)") },
                        enabled = uiState.isEditing,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OptionDropdownField(
                        label = "目标",
                        options = goalOptions,
                        value = goal,
                        enabled = uiState.isEditing,
                        onValueSelected = { goal = it }
                    )

                    OptionDropdownField(
                        label = "活动水平",
                        options = activityOptions,
                        value = activity,
                        enabled = uiState.isEditing,
                        onValueSelected = { activity = it }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("每日热量目标", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${profile.dailyCaloricTarget} kcal",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (uiState.isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCancelEditing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            onSave(
                                name,
                                gender,
                                height.toFloatOrNull() ?: profile.heightCm,
                                weight.toFloatOrNull() ?: profile.weightKg,
                                age.toIntOrNull() ?: profile.age,
                                goal,
                                activity
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }

            uiState.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Avatar(initial: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdownField(
    label: String,
    options: List<Pair<String, String>>,
    value: String,
    enabled: Boolean,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.firstOrNull { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (rawValue, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onValueSelected(rawValue)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun WeightTrendCard(
    records: List<WeightRecord>,
    selectedRange: WeightChartRange,
    firstRecordDate: LocalDate?,
    referenceWeight: Float,
    onRangeChange: (WeightChartRange) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("体重趋势", fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedRange == WeightChartRange.MONTH,
                    onClick = { onRangeChange(WeightChartRange.MONTH) },
                    label = { Text("1月") }
                )
                FilterChip(
                    selected = selectedRange == WeightChartRange.YEAR,
                    onClick = { onRangeChange(WeightChartRange.YEAR) },
                    label = { Text("1年") }
                )
                FilterChip(
                    selected = selectedRange == WeightChartRange.ALL,
                    onClick = { onRangeChange(WeightChartRange.ALL) },
                    label = {
                        val allLabel = if (firstRecordDate != null) {
                            "自 ${firstRecordDate.year} 年起"
                        } else {
                            "全部"
                        }
                        Text(allLabel)
                    }
                )
            }

            WeightTrendChart(
                records = records,
                selectedRange = selectedRange,
                firstRecordDate = firstRecordDate,
                referenceWeight = referenceWeight
            )

            if (records.isEmpty()) {
                Text(
                    text = "暂无历史记录，后续记录将显示在坐标轴上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeightTrendChart(
    records: List<WeightRecord>,
    selectedRange: WeightChartRange,
    firstRecordDate: LocalDate?,
    referenceWeight: Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val today = LocalDate.now()

    val windowStart = when (selectedRange) {
        WeightChartRange.MONTH -> today.minusMonths(1)
        WeightChartRange.YEAR -> today.minusYears(1)
        WeightChartRange.ALL -> firstRecordDate ?: today.minusMonths(1)
    }
    val windowEnd = today

    val visibleRecords = records.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(windowEnd) }

    val minWeightRaw = visibleRecords.minOfOrNull { it.weight } ?: (referenceWeight - 5f)
    val maxWeightRaw = visibleRecords.maxOfOrNull { it.weight } ?: (referenceWeight + 5f)
    val minWeight = floor((minWeightRaw - 1f).toDouble()).toFloat()
    val maxWeight = ceil((maxWeightRaw + 1f).toDouble()).toFloat()
    val safeMaxWeight = if (maxWeight <= minWeight) minWeight + 1f else maxWeight

    val totalDays = ChronoUnit.DAYS.between(windowStart, windowEnd).coerceAtLeast(1)

    val xLabelFormatter = when (selectedRange) {
        WeightChartRange.MONTH -> DateTimeFormatter.ofPattern("MM月dd日")
        WeightChartRange.YEAR -> DateTimeFormatter.ofPattern("yyyy年MM月")
        WeightChartRange.ALL -> DateTimeFormatter.ofPattern("yyyy年")
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        val chartLeft = 52.dp.toPx()
        val chartRight = size.width - 12.dp.toPx()
        val chartTop = 16.dp.toPx()
        val chartBottom = size.height - 34.dp.toPx()

        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        drawLine(
            color = Color.Gray,
            start = Offset(chartLeft, chartTop),
            end = Offset(chartLeft, chartBottom),
            strokeWidth = 2f
        )
        drawLine(
            color = Color.Gray,
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 2f
        )

        val yTicks = 4
        for (i in 0..yTicks) {
            val fraction = i / yTicks.toFloat()
            val y = chartBottom - chartHeight * fraction
            val value = minWeight + (safeMaxWeight - minWeight) * fraction

            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = 1f
            )

            drawText(
                textMeasurer = textMeasurer,
                text = String.format("%.1f", value),
                style = TextStyle(fontSize = 10.sp, color = Color.Gray),
                topLeft = Offset(4f, y - 10.dp.toPx())
            )
        }

        val midDate = windowStart.plusDays(totalDays / 2)
        val xLabelDates = listOf(windowStart, midDate, windowEnd)

        xLabelDates.forEach { date ->
            val daysFromStart = ChronoUnit.DAYS.between(windowStart, date).coerceAtLeast(0)
            val x = chartLeft + (daysFromStart.toFloat() / totalDays.toFloat()) * chartWidth

            drawLine(
                color = Color.LightGray,
                start = Offset(x, chartBottom),
                end = Offset(x, chartBottom + 5.dp.toPx()),
                strokeWidth = 1f
            )

            drawText(
                textMeasurer = textMeasurer,
                text = date.format(xLabelFormatter),
                style = TextStyle(fontSize = 10.sp, color = Color.Gray),
                topLeft = Offset(x - 18.dp.toPx(), chartBottom + 8.dp.toPx())
            )
        }

        if (visibleRecords.isNotEmpty()) {
            fun mapX(date: LocalDate): Float {
                val daysFromStart = ChronoUnit.DAYS.between(windowStart, date)
                    .coerceIn(0, totalDays)
                return chartLeft + (daysFromStart.toFloat() / totalDays.toFloat()) * chartWidth
            }

            fun mapY(weight: Float): Float {
                val ratio = (weight - minWeight) / (safeMaxWeight - minWeight)
                return chartBottom - ratio * chartHeight
            }

            if (visibleRecords.size == 1) {
                val point = visibleRecords.first()
                drawCircle(
                    color = primaryColor,
                    radius = 5.dp.toPx(),
                    center = Offset(mapX(point.date), mapY(point.weight))
                )
            } else {
                val path = Path()
                visibleRecords.forEachIndexed { index, record ->
                    val x = mapX(record.date)
                    val y = mapY(record.weight)
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                visibleRecords.forEach { record ->
                    val x = mapX(record.date)
                    val y = mapY(record.weight)
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
