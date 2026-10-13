package com.example.connects.core.util

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * 本地时区的「今天」（epochDay）。
 *
 * 不要用 `System.currentTimeMillis() / 86_400_000` 表示今天：那是 UTC 日界，
 * 在 UTC+8 的 00:00–08:00 之间算出来的是前一天，会让首页「今日摄入」和日志页对不上。
 */
fun todayEpochDay(): Int = LocalDate.now().toEpochDay().toInt()

/**
 * 本地时区的「今天」，并且每跨过一次本地零点就重新发出一次新值。
 *
 * 「今日摄入」这类查询需要跨天自动刷新：如果只在 ViewModel 构造时取一次日期，
 * 应用常驻（或后台一段时间后回到前台）过了零点，页面还停在昨天的数据上。
 */
fun todayEpochDayFlow(): Flow<Int> = flow {
    while (true) {
        emit(todayEpochDay())
        val now = ZonedDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L))
    }
}.distinctUntilChanged()
