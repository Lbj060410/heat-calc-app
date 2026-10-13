package com.example.connects.core.util

import java.util.Locale

/** 整数就不带小数位，免得界面上出现 "116.0" 这种。 */
fun formatAmount(value: Float): String =
    if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
