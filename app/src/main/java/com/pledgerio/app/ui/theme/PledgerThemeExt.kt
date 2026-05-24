package com.pledgerio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colors that stay consistent across light/dark Material schemes.
 * Prefer these over direct [EmeraldGreen] / [PledgerGreen] imports in UI code.
 */
object PledgerThemeExt {
    /** Brand accent (checkmark green) — maps to [androidx.compose.material3.ColorScheme.tertiary]. */
    val brandAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.tertiary

    /** Primary actions on dark headers (navy in light theme). */
    val actionPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    val income: Color
        @Composable
        @ReadOnlyComposable
        get() = IncomeGreen

    val expense: Color
        @Composable
        @ReadOnlyComposable
        get() = ExpenseRed
}
