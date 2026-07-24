package com.walletapp.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Paleta "Midnight FinTech" (export de Stitch, midnight_fintech/DESIGN.md) — valores transcriptos tal
// cual, ya en convención de roles de Material3 (data-model.md de la feature 008).
private val Surface = Color(0xFF10131A)
private val SurfaceDim = Color(0xFF10131A)
private val SurfaceBright = Color(0xFF363940)
private val SurfaceContainerLowest = Color(0xFF0B0E14)
private val SurfaceContainerLow = Color(0xFF191C22)
private val SurfaceContainer = Color(0xFF1D2026)
private val SurfaceContainerHigh = Color(0xFF272A31)
private val SurfaceContainerHighest = Color(0xFF32353C)
private val OnSurface = Color(0xFFE1E2EB)
private val OnSurfaceVariant = Color(0xFFC0C7D4)
private val InverseSurface = Color(0xFFE1E2EB)
private val InverseOnSurface = Color(0xFF2E3037)
private val Outline = Color(0xFF8A919D)
private val OutlineVariant = Color(0xFF404752)

private val Primary = Color(0xFFA3C9FF)
private val OnPrimary = Color(0xFF00315C)
private val PrimaryContainer = Color(0xFF4DA1FF)
private val OnPrimaryContainer = Color(0xFF003665)
private val InversePrimary = Color(0xFF0060AB)
private val PrimaryFixed = Color(0xFFD3E3FF)
private val PrimaryFixedDim = Color(0xFFA3C9FF)
private val OnPrimaryFixed = Color(0xFF001C39)
private val OnPrimaryFixedVariant = Color(0xFF004882)

// Secundario = color de éxito/ingreso en las pantallas de movimientos (verde).
private val Secondary = Color(0xFF40E56C)
private val OnSecondary = Color(0xFF003912)
private val SecondaryContainer = Color(0xFF02C953)
private val OnSecondaryContainer = Color(0xFF004D1B)
private val SecondaryFixed = Color(0xFF69FF87)
private val SecondaryFixedDim = Color(0xFF3CE36A)
private val OnSecondaryFixed = Color(0xFF002108)
private val OnSecondaryFixedVariant = Color(0xFF00531E)

private val Tertiary = Color(0xFFFFB3AE)
private val OnTertiary = Color(0xFF68000C)
private val TertiaryContainer = Color(0xFFFF726D)
private val OnTertiaryContainer = Color(0xFF72000E)
private val TertiaryFixed = Color(0xFFFFDAD7)
private val TertiaryFixedDim = Color(0xFFFFB3AE)
private val OnTertiaryFixed = Color(0xFF410004)
private val OnTertiaryFixedVariant = Color(0xFF930015)

// Error = color de gasto en las pantallas de movimientos (rojo).
private val ErrorColor = Color(0xFFFFB4AB)
private val OnError = Color(0xFF690005)
private val ErrorContainer = Color(0xFF93000A)
private val OnErrorContainer = Color(0xFFFFDAD6)

private val Background = Color(0xFF10131A)
private val OnBackground = Color(0xFFE1E2EB)
private val SurfaceVariant = Color(0xFF32353C)

val WalletColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    inversePrimary = InversePrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Primary,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    error = ErrorColor,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = Outline,
    outlineVariant = OutlineVariant,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainerLowest = SurfaceContainerLowest,
    primaryFixed = PrimaryFixed,
    primaryFixedDim = PrimaryFixedDim,
    onPrimaryFixed = OnPrimaryFixed,
    onPrimaryFixedVariant = OnPrimaryFixedVariant,
    secondaryFixed = SecondaryFixed,
    secondaryFixedDim = SecondaryFixedDim,
    onSecondaryFixed = OnSecondaryFixed,
    onSecondaryFixedVariant = OnSecondaryFixedVariant,
    tertiaryFixed = TertiaryFixed,
    tertiaryFixedDim = TertiaryFixedDim,
    onTertiaryFixed = OnTertiaryFixed,
    onTertiaryFixedVariant = OnTertiaryFixedVariant
)
