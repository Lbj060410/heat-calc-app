package com.example.connects.features.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

@Composable
fun WeightTrendCard(
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
                TrendChip(selectedRange == WeightChartRange.DAY, "日") {
                    onRangeChange(WeightChartRange.DAY)
                }
                TrendChip(selectedRange == WeightChartRange.WEEK, "周") {
                    onRangeChange(WeightChartRange.WEEK)
                }
                TrendChip(selectedRange == WeightChartRange.MONTH, "月") {
                    onRangeChange(WeightChartRange.MONTH)
                }
                TrendChip(selectedRange == WeightChartRange.YEAR, "年") {
                    onRangeChange(WeightChartRange.YEAR)
                }
                TrendChip(selectedRange == WeightChartRange.ALL, "总") {
                    onRangeChange(WeightChartRange.ALL)
                }
            }

            TrendChart(
                records = records,
                selectedRange = selectedRange,
                firstRecordDate = firstRecordDate,
                referenceWeight = referenceWeight
            )

            if (records.isEmpty()) {
                Text(
                    text = "暂无历史记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrendChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun TrendChart(
    records: List<WeightRecord>,
    selectedRange: WeightChartRange,
    firstRecordDate: LocalDate?,
    referenceWeight: Float
) {
    val today = LocalDate.now()
    val buckets = buildBuckets(records, selectedRange, firstRecordDate, today)
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary

    when (selectedRange) {
        WeightChartRange.ALL -> TrendLineChart(
            points = buckets.mapNotNull { bucket ->
                bucket.value?.let { WeightTrendPoint(bucket.endDate, it) }
            },
            referenceWeight = referenceWeight,
            primaryColor = primaryColor
        )
        else -> TrendBarChart(
            buckets = buckets,
            referenceWeight = referenceWeight,
            textMeasurer = textMeasurer,
            primaryColor = primaryColor
        )
    }
}

@Composable
private fun TrendBarChart(
    buckets: List<WeightTrendBucket>,
    referenceWeight: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    primaryColor: Color
) {
    val scrollState = rememberScrollState()
    val bucketWidth = 86.dp
    val chartHeight = 280.dp
    val canvasWidth = max(1, buckets.size) * bucketWidth.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .height(chartHeight)
                .requiredWidth(canvasWidth.dp)
        ) {
            val topPadding = 18.dp.toPx()
            val bottomPadding = 44.dp.toPx()
            val chartBottom = size.height - bottomPadding
            val chartHeightPx = chartBottom - topPadding
            val maxValue = max(buckets.maxOfOrNull { it.value ?: referenceWeight } ?: referenceWeight, referenceWeight) + 2f
            val minValue = min(buckets.minOfOrNull { it.value ?: referenceWeight } ?: referenceWeight, referenceWeight) - 2f
            val safeRange = if (maxValue <= minValue) 1f else maxValue - minValue
            val barWidth = 36.dp.toPx()
            val centerPadding = 24.dp.toPx()

            buckets.forEachIndexed { index, bucket ->
                val centerX = centerPadding + index * bucketWidth.toPx()
                val value = bucket.value ?: referenceWeight
                val barHeight = ((value - minValue) / safeRange) * chartHeightPx
                val top = chartBottom - barHeight

                drawRect(
                    color = if (bucket.isCurrent) primaryColor else primaryColor.copy(alpha = 0.75f),
                    topLeft = Offset(centerX - barWidth / 2, top),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx()))
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = bucket.label,
                    style = TextStyle(fontSize = 10.sp, color = Color.Gray),
                    topLeft = Offset(centerX - 28.dp.toPx(), chartBottom + 6.dp.toPx())
                )

                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format("%.1f", value),
                    style = TextStyle(fontSize = 10.sp, color = Color.DarkGray),
                    topLeft = Offset(centerX - 18.dp.toPx(), top - 18.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun TrendLineChart(
    points: List<WeightTrendPoint>,
    referenceWeight: Float,
    primaryColor: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val today = LocalDate.now()
    val startDate = points.firstOrNull()?.date ?: today
    val endDate = points.lastOrNull()?.date ?: today
    val totalDays = max(1L, ChronoUnit.DAYS.between(startDate, endDate))
    val minValue = min(points.minOfOrNull { it.value } ?: referenceWeight, referenceWeight) - 2f
    val maxValue = max(points.maxOfOrNull { it.value } ?: referenceWeight, referenceWeight) + 2f
    val safeRange = if (maxValue <= minValue) 1f else maxValue - minValue

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        val chartLeft = 48.dp.toPx()
        val chartRight = size.width - 12.dp.toPx()
        val chartTop = 18.dp.toPx()
        val chartBottom = size.height - 34.dp.toPx()
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop
        drawLine(Color.Gray, Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), 2f)
        drawLine(Color.Gray, Offset(chartLeft, chartBottom), Offset(chartRight, chartBottom), 2f)

        for (i in 0..4) {
            val fraction = i / 4f
            val y = chartBottom - chartHeight * fraction
            val value = minValue + safeRange * fraction
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

        val xLabelDates = listOf(startDate, endDate).distinct()
        xLabelDates.forEach { date ->
            val x = chartLeft + (ChronoUnit.DAYS.between(startDate, date).toFloat() / totalDays.toFloat()) * chartWidth
            drawLine(
                color = Color.LightGray,
                start = Offset(x, chartBottom),
                end = Offset(x, chartBottom + 5.dp.toPx()),
                strokeWidth = 1f
            )
            drawText(
                textMeasurer = textMeasurer,
                text = date.format(DateTimeFormatter.ofPattern("MM/dd")),
                style = TextStyle(fontSize = 10.sp, color = Color.Gray),
                topLeft = Offset(x - 18.dp.toPx(), chartBottom + 8.dp.toPx())
            )
        }

        if (points.isNotEmpty()) {
            fun mapX(date: LocalDate): Float {
                val daysFromStart = ChronoUnit.DAYS.between(startDate, date).coerceIn(0, totalDays)
                return chartLeft + (daysFromStart.toFloat() / totalDays.toFloat()) * chartWidth
            }

            fun mapY(weight: Float): Float {
                val ratio = (weight - minValue) / safeRange
                return chartBottom - ratio * chartHeight
            }

            if (points.size == 1) {
                val point = points.first()
                drawCircle(primaryColor, 5.dp.toPx(), Offset(mapX(point.date), mapY(point.value)))
            } else {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = mapX(point.date)
                    val y = mapY(point.value)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
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
                points.forEach { point ->
                    val x = mapX(point.date)
                    val y = mapY(point.value)
                    drawCircle(primaryColor, 4.dp.toPx(), Offset(x, y))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(x, y))
                }
            }
        }
    }
}

private fun buildBuckets(
    records: List<WeightRecord>,
    range: WeightChartRange,
    firstRecordDate: LocalDate?,
    today: LocalDate
): List<WeightTrendBucket> {
    val boundedRecords = records.filter { !it.date.isAfter(today) }
    return when (range) {
        WeightChartRange.DAY -> buildDayBuckets(boundedRecords, today)
        WeightChartRange.WEEK -> buildWeekBuckets(boundedRecords, today)
        WeightChartRange.MONTH -> buildMonthBuckets(boundedRecords, today)
        WeightChartRange.YEAR -> buildYearBuckets(boundedRecords, today)
        WeightChartRange.ALL -> buildAllBuckets(boundedRecords, firstRecordDate, today)
    }
}

private fun buildDayBuckets(
    records: List<WeightRecord>,
    today: LocalDate
): List<WeightTrendBucket> {
    val start = today.minusDays(29)
    val byDate = records.groupBy { it.date }.mapValues { (_, value) -> value.lastOrNull()?.weight }
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { date ->
            WeightTrendBucket(
                startDate = date,
                endDate = date,
                label = date.format(DateTimeFormatter.ofPattern("MM/dd")),
                value = byDate[date],
                isCurrent = date == today
            )
        }
        .toList()
}

private fun buildWeekBuckets(
    records: List<WeightRecord>,
    today: LocalDate
): List<WeightTrendBucket> {
    val currentWeekStart = today.with(java.time.DayOfWeek.MONDAY)
    val firstDate = records.minOfOrNull { it.date } ?: currentWeekStart
    val startWeek = firstDate.with(java.time.DayOfWeek.MONDAY)
    val buckets = mutableListOf<WeightTrendBucket>()
    var cursor = startWeek
    while (!cursor.isAfter(currentWeekStart)) {
        val end = minOf(cursor.plusDays(6), today)
        val value = records.filter { !it.date.isBefore(cursor) && !it.date.isAfter(end) }.lastOrNull()?.weight
        val label = if (cursor == currentWeekStart) {
            "本周"
        } else {
            "${cursor.format(DateTimeFormatter.ofPattern("MM/dd"))}—${end.format(DateTimeFormatter.ofPattern("MM/dd"))}"
        }
        buckets += WeightTrendBucket(
            startDate = cursor,
            endDate = end,
            label = label,
            value = value,
            isCurrent = cursor == currentWeekStart
        )
        cursor = cursor.plusWeeks(1)
    }
    return buckets
}

private fun buildMonthBuckets(
    records: List<WeightRecord>,
    today: LocalDate
): List<WeightTrendBucket> {
    val currentMonth = YearMonth.from(today)
    val firstDate = records.minOfOrNull { it.date } ?: today
    val startMonth = YearMonth.from(firstDate)
    val buckets = mutableListOf<WeightTrendBucket>()
    var cursor = startMonth
    while (!cursor.isAfter(currentMonth)) {
        val start = cursor.atDay(1)
        val end = minOf(cursor.atEndOfMonth(), today)
        val value = records.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.lastOrNull()?.weight
        val label = if (cursor == currentMonth) "本月" else "${cursor.monthValue}月"
        buckets += WeightTrendBucket(
            startDate = start,
            endDate = end,
            label = label,
            value = value,
            isCurrent = cursor == currentMonth
        )
        cursor = cursor.plusMonths(1)
    }
    return buckets
}

private fun buildYearBuckets(
    records: List<WeightRecord>,
    today: LocalDate
): List<WeightTrendBucket> {
    val currentYear = today.year
    val firstDate = records.minOfOrNull { it.date } ?: today
    val buckets = mutableListOf<WeightTrendBucket>()
    for (year in firstDate.year..currentYear) {
        val start = LocalDate.of(year, 1, 1)
        val end = minOf(LocalDate.of(year, 12, 31), today)
        val value = records.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.lastOrNull()?.weight
        val label = if (year == currentYear) "今年" else "${year}年"
        buckets += WeightTrendBucket(
            startDate = start,
            endDate = end,
            label = label,
            value = value,
            isCurrent = year == currentYear
        )
    }
    return buckets
}

private fun buildAllBuckets(
    records: List<WeightRecord>,
    firstRecordDate: LocalDate?,
    today: LocalDate
): List<WeightTrendBucket> {
    val start = firstRecordDate ?: return emptyList()
    val buckets = mutableListOf<WeightTrendBucket>()
    var cursor = start
    var lastWeight: Float? = null
    while (!cursor.isAfter(today)) {
        val dayRecord = records.lastOrNull { it.date == cursor }
        if (dayRecord != null) {
            lastWeight = dayRecord.weight
        }
        buckets += WeightTrendBucket(
            startDate = cursor,
            endDate = cursor,
            label = cursor.format(DateTimeFormatter.ofPattern("MM/dd")),
            value = lastWeight,
            isCurrent = cursor == today
        )
        cursor = cursor.plusDays(1)
    }
    return buckets
}
