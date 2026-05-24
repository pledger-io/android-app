package com.pledgerio.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PledgerGreen,
    onPrimary = Color.White,
    primaryContainer = PledgerGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = PledgerBlue,
    onSecondary = Color.White,
    secondaryContainer = PledgerNavyLight,
    onSecondaryContainer = TextPrimary,
    tertiary = PledgerNavyLight,
    onTertiary = TextPrimary,
    background = PledgerNavyDark,
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
    primary = PledgerNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8F5),
    onPrimaryContainer = PledgerNavy,
    secondary = PledgerBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EDF8),
    onSecondaryContainer = PledgerNavy,
    tertiary = PledgerGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFC5D0DE),
    error = ExpenseRed,
    onError = Color.White,
)

@Composable
fun PledgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) {
                HeaderGradientTopDark.toArgb()
            } else {
                HeaderGradientTopLight.toArgb()
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalPledgerDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PledgerTypography,
            shapes = PledgerShapes,
            content = content,
        )
    }
}
