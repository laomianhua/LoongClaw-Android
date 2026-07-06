package com.littlehelper.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
    background = AppBackground,
    surface = CardBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

/** 与主界面 AppColors 一致的浅色语义色，避免设置页 surface 与 onSurface 错位。 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5E5EA),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFFC7C7CC),
)

@Composable
fun LittleHelperTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
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
        typography = Typography
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = 1f)
        ) {
            content()
        }
    }
}
