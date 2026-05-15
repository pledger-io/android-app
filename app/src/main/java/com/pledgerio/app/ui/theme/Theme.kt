package com.pledgerio.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DeepNavy,
    primaryContainer = EmeraldGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = EmeraldGreen,
    onSecondary = DeepNavy,
    secondaryContainer = Card,
    onSecondaryContainer = TextPrimary,
    tertiary = WarningAmber,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = ExpenseRed,
    onError = TextPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldGreen,
    onPrimary = LightBackground,
    primaryContainer = EmeraldGreenDark,
    onPrimaryContainer = LightTextPrimary,
    secondary = EmeraldGreen,
    onSecondary = LightBackground,
    secondaryContainer = LightCard,
    onSecondaryContainer = LightTextPrimary,
    tertiary = WarningAmber,
    onTertiary = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = LightTextSecondary,
    error = ExpenseRed,
    onError = LightBackground,
)

@Composable
fun PledgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PledgerTypography,
        content = content,
    )
}
