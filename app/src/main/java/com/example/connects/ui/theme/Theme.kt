package com.example.connects.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenDark,
    onPrimaryContainer = Color.White,
    
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = SecondaryBlueDark,
    onSecondaryContainer = Color.White,
    
    tertiary = AccentOrange,
    onTertiary = Color.White,
    tertiaryContainer = AccentOrangeDark,
    onTertiaryContainer = Color.White,
    
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Color(0xFF334155),  // Slate 700
    onSurfaceVariant = Color(0xFFE2E8F0),  // Slate 200
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = PrimaryGreenLight,
    onPrimaryContainer = Color(0xFF064E3B),
    
    secondary = SecondaryBlue,
    onSecondary = Color.White,
    secondaryContainer = SecondaryBlueLight,
    onSecondaryContainer = Color(0xFF1E3A8A),
    
    tertiary = AccentOrange,
    onTertiary = Color.White,
    tertiaryContainer = AccentOrangeLight,
    onTertiaryContainer = Color(0xFF7C2D12),
    
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Color(0xFFF1F5F9),  // Slate 100
    onSurfaceVariant = Color(0xFF475569),  // Slate 600
    
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun ConnectsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}