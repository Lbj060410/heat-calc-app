package com.example.connects.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 手账主题。
 *
 * 关键映射（界面统一走 colorScheme，不直接引用静态色值）：
 * - background     = 纸底（米白 / 深夜纸）
 * - surface        = 暖白面（卡片 / 弹窗）
 * - primary        = 深牛油果绿（强调、选中、正向数值）
 * - tertiary       = 陶土（超限、调整依据）
 * - outline        = 折角箭头等弱前景
 * - outlineVariant = 点线引导
 *
 * dynamicColor 默认关闭：取壁纸上色会毁掉纸感配色。
 */

private val LightColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = Paper,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenDeep,
    secondary = GreenDeep,
    onSecondary = Paper,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = GreenDeep,
    tertiary = Clay,
    onTertiary = Paper,
    tertiaryContainer = ClayContainer,
    onTertiaryContainer = Color(0xFF7C3E1E),
    background = Paper,
    onBackground = Ink,
    surface = SurfaceWarm,
    onSurface = Ink,
    surfaceVariant = SurfaceWarm,
    onSurfaceVariant = InkSoft,
    surfaceTint = Green,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = GreenContainer,
    outline = Color(0xFFB4AD97),
    outlineVariant = DotLeader,
    error = ErrorRed,
    onError = Paper,
    errorContainer = Color(0xFFF6E0DC),
    onErrorContainer = Color(0xFF7C231A)
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF10241B),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = Color(0xFFCFE8D8),
    secondary = GreenDark,
    onSecondary = Color(0xFF10241B),
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = Color(0xFFCFE8D8),
    tertiary = ClayDark,
    onTertiary = Color(0xFF2E1B0D),
    tertiaryContainer = ClayContainerDark,
    onTertiaryContainer = Color(0xFFEBD3BE),
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = InkDarkSoft,
    surfaceTint = GreenDark,
    inverseSurface = InkDark,
    inverseOnSurface = PaperDark,
    inversePrimary = GreenContainerDark,
    outline = Color(0xFF6B6551),
    outlineVariant = DotLeaderDark,
    error = ErrorRedDark,
    onError = PaperDark,
    errorContainer = Color(0xFF5C231C),
    onErrorContainer = Color(0xFFF6D5D0)
)

@Composable
fun ConnectsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
