package com.example.connects.features.weight

import java.time.LocalDate

enum class WeightChartRange {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL
}

data class WeightRecord(
    val date: LocalDate,
    val weight: Float
)

data class WeightTrendBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val label: String,
    val value: Float?,
    val isCurrent: Boolean
)

data class WeightTrendPoint(
    val date: LocalDate,
    val value: Float
)
