package com.example.connects.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 手账排版体系。
 *
 * 正文与控件用系统默认无衬线（Typography），保持可读性；
 * 数字与标题用衬线（FontFamily.Serif，中文对应 Noto Serif），
 * 「大数字层级」是这套设计的核心表达。
 */

val SerifFamily = FontFamily.Serif

/** 首页剩余热量：特大衬线数字。 */
val JournalHero = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Black,
    fontSize = 84.sp,
    lineHeight = 88.sp,
    letterSpacing = (-0.5).sp
)

/** 日志当日汇总等次级大数字。 */
val JournalBig = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Black,
    fontSize = 52.sp,
    lineHeight = 56.sp,
    letterSpacing = (-0.3).sp
)

/** 页面大标题（今日 / 日志）。 */
val JournalTitle = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Black,
    fontSize = 27.sp,
    lineHeight = 34.sp
)

/** 区块标题（今日餐次 / 快捷入口）。 */
val JournalSection = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp
)

/** 菜单里的餐次名。 */
val JournalMeal = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 22.sp
)

/** 印章头像里的单字。 */
val JournalSeal = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 24.sp
)

/** 「我的」页状态陈述句 / 目标数字。 */
val JournalStatement = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Black,
    fontSize = 21.sp,
    lineHeight = 28.sp
)

/** 弹窗输入框里的大数字。 */
val JournalInput = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = FontWeight.Black,
    fontSize = 32.sp,
    lineHeight = 38.sp
)

/** Material 组件（表单 / 按钮 / 正文）保持无衬线。 */
val Typography = Typography()
