package com.example.connects.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 纸感手账配色（与预览稿 V2 一致）。
 *
 * 设计语言：米白纸底 + 墨色文字 + 深牛油果绿单一强调；陶土色只在「超限 / 提醒」时出现。
 * 所有界面颜色一律走 MaterialTheme.colorScheme，保证暗色模式有对应的映射。
 */

// —— 纸与墨 ——
val Paper = Color(0xFFF7F4EC)          // 米白纸底（页面背景）
val PaperDark = Color(0xFF191712)      // 暗色模式的深夜纸
val SurfaceWarm = Color(0xFFFFFDF8)    // 暖白面（卡片 / 弹窗）
val SurfaceDark = Color(0xFF211E16)
val Ink = Color(0xFF1F1D18)            // 墨（主文字）
val InkSoft = Color(0xFF6E6A5B)        // 次级文字
val InkFaint = Color(0xFF9C9784)       // 三级文字 / 占位
val InkDark = Color(0xFFEDE9DB)        // 暗色模式主文字
val InkDarkSoft = Color(0xFFA9A493)    // 暗色模式次级文字

// —— 线与点 ——
val Hairline = Color(0xFFE4DFD0)       // 发丝分隔线
val HairlineDark = Color(0xFF3B3829)
val DotLeader = Color(0xFFD5CDB4)     // 点线引导（菜单式）
val DotLeaderDark = Color(0xFF4A4532)

// —— 强调色 ——
val Green = Color(0xFF2F6B4F)          // 深牛油果绿
val GreenDeep = Color(0xFF24523C)
val GreenDark = Color(0xFF8FC7A9)      // 暗色模式的绿（亮面）
val GreenContainer = Color(0xFFE2EFE6)
val GreenContainerDark = Color(0xFF24402F)

// —— 语义色 ——
val Clay = Color(0xFFB4552D)           // 陶土：超限 / 上次调整
val ClayDark = Color(0xFFD08B5F)
val ClayContainer = Color(0xFFF3E3D6)
val ClayContainerDark = Color(0xFF42301F)
val ErrorRed = Color(0xFFB4362B)
val ErrorRedDark = Color(0xFFE5695C)
