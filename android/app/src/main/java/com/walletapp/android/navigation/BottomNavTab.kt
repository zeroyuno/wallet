package com.walletapp.android.navigation

import com.walletapp.android.Screen

// Las 3 secciones principales de la barra de navegación inferior (feature 008, data-model.md).
enum class BottomNavTab {
    Accounts,
    Transactions,
    Categories
}

// Deriva la pestaña activa a partir del estado de navegación ya existente en MainActivity — sin
// ViewModel propio (research.md #3 de la feature 008). null = ocultar la barra (pantallas
// secundarias: login/registro/formularios).
internal fun Screen.toBottomNavTab(): BottomNavTab? = when (this) {
    Screen.AccountsList, is Screen.AccountForm -> BottomNavTab.Accounts
    Screen.TransactionsList, is Screen.TransactionForm -> BottomNavTab.Transactions
    Screen.CategoriesList, is Screen.CategoryForm -> BottomNavTab.Categories
    Screen.Login, Screen.Register -> null
}
