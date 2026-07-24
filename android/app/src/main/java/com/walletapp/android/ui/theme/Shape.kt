package com.walletapp.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radios del sistema de diseño Midnight FinTech (data-model.md de la feature 008).
val WalletShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Forma "pill" (radio completo) para botones/chips — no es parte de Shapes de Material3 (que solo
// tiene 5 tamaños fijos), se expone aparte para uso explícito donde el diseño pide un pill exacto.
val PillShape = RoundedCornerShape(percent = 50)
