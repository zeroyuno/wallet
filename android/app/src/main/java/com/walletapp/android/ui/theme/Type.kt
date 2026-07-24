package com.walletapp.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.walletapp.android.R

// Inter es una variable font (un solo archivo, eje wght) — cada peso se logra con
// FontVariation.Settings en vez de bundlear un .ttf estático por peso (research.md #2 de la
// feature 008). FontVariation.Settings es una API experimental de Compose todavía, de ahí el OptIn.
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.inter,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val InterFontFamily = FontFamily(
    interWeight(400, FontWeight.Normal),
    interWeight(500, FontWeight.Medium),
    interWeight(600, FontWeight.SemiBold),
    interWeight(700, FontWeight.Bold)
)

// Mapeo de los roles custom del sistema de diseño (data-model.md) a los 15 roles fijos de
// Typography de Material3 — no hay una correspondencia 1:1 perfecta, se elige el rol M3 más cercano
// en tamaño/uso para que todo el código existente que ya llama a MaterialTheme.typography.X herede
// el estilo nuevo automáticamente.
val WalletTypography = Typography(
    headlineLarge = TextStyle( // = headline-xl (32/700, montos grandes/saldos)
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em
    ),
    headlineMedium = TextStyle( // = numeric-display (28/700, montos en listas)
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em
    ),
    headlineSmall = TextStyle( // = headline-lg (24/600, títulos de pantalla)
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle( // usado hoy para nombres de fila (cuenta/movimiento/categoría)
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle( // = body-lg
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle( // = body-md
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle( // usado hoy para subtítulos/metadata (fecha, cuenta · categoría)
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle( // = label-lg (labels de filtro/chip)
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle( // = label-md
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

// numeric-display expuesto aparte para uso explícito en montos/saldos destacados (ej. balance total)
// que no coinciden con ningún rol de MaterialTheme.typography por tamaño/contexto de uso.
val NumericDisplayStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.02).em
)
