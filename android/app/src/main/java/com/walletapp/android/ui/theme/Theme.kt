package com.walletapp.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// El sistema de diseño Midnight FinTech es dark-mode-first — no hay variante clara en esta ronda
// (spec.md, Assumptions de la feature 008).
@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WalletColorScheme,
        typography = WalletTypography,
        shapes = WalletShapes,
        content = content
    )
}
