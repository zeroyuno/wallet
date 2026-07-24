package com.walletapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountResponse
import com.walletapp.android.accounts.ui.AccountFormScreen
import com.walletapp.android.accounts.ui.AccountListScreen
import com.walletapp.android.auth.AuthViewModel
import com.walletapp.android.auth.SessionCheckState
import com.walletapp.android.auth.ui.LoginScreen
import com.walletapp.android.auth.ui.RegisterScreen
import com.walletapp.android.categories.CategoryResponse
import com.walletapp.android.categories.ui.CategoryFormScreen
import com.walletapp.android.categories.ui.CategoryListScreen
import com.walletapp.android.navigation.BottomNavTab
import com.walletapp.android.navigation.toBottomNavTab
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.ui.TransactionFormScreen
import com.walletapp.android.transactions.ui.TransactionListScreen
import com.walletapp.android.ui.theme.WalletTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WalletTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WalletApp()
                }
            }
        }
    }
}

// internal (no private) para que navigation/BottomNavTab.kt pueda mapear Screen -> pestaña activa.
internal sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object AccountsList : Screen
    data class AccountForm(val account: AccountResponse? = null) : Screen
    data object CategoriesList : Screen
    data class CategoryForm(val category: CategoryResponse? = null) : Screen
    data object TransactionsList : Screen
    data class TransactionForm(val transaction: TransactionResponse? = null) : Screen
}

@Composable
private fun WalletApp(authViewModel: AuthViewModel = hiltViewModel()) {
    // Feature 008: se quita Screen.Home (menú manual) — la barra de navegación inferior cubre esas
    // 3 secciones, y la sesión aterriza directo en Cuentas (research.md #5).
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
    val sessionState by authViewModel.sessionState.collectAsState()

    // Al abrir la app se valida contra el backend si el token guardado sigue siendo válido
    // (no expiró, no fue revocado desde otro dispositivo/logout) antes de decidir la pantalla
    // inicial — así una sesión iniciada previamente no vuelve a pedir login (spec 001, US3).
    LaunchedEffect(Unit) { authViewModel.checkSession() }
    LaunchedEffect(sessionState) {
        if (sessionState == SessionCheckState.LoggedIn && screen == Screen.Login) {
            screen = Screen.AccountsList
        }
    }

    val activeTab = screen.toBottomNavTab()

    // Login y las 3 secciones principales no tienen "atrás" dentro de la app (son destinos de nivel
    // superior de la barra de navegación — atrás minimiza la app, mismo criterio estándar de una
    // bottom nav bar). Solo los formularios y Registro tienen un padre lógico al que volver.
    // OJO: esto NO puede derivarse de `activeTab == null`, porque toBottomNavTab() mapea los
    // formularios a la pestaña de su lista padre (para que la barra siga resaltada mientras se
    // edita) — usar esa condición deshabilitaba el BackHandler justo en los formularios y el back
    // del sistema terminaba cerrando la app en lugar de volver a la lista.
    val hasBackDestination = when (screen) {
        Screen.Login, Screen.AccountsList, Screen.TransactionsList, Screen.CategoriesList -> false
        Screen.Register, is Screen.AccountForm, is Screen.CategoryForm, is Screen.TransactionForm -> true
    }
    BackHandler(enabled = hasBackDestination) {
        screen = when (val current = screen) {
            Screen.Login, Screen.AccountsList, Screen.TransactionsList, Screen.CategoriesList -> screen
            Screen.Register -> Screen.Login
            is Screen.AccountForm -> Screen.AccountsList
            is Screen.CategoryForm -> Screen.CategoriesList
            is Screen.TransactionForm -> Screen.TransactionsList
        }
    }

    if (sessionState == SessionCheckState.Checking) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Sin Scaffold acá: cada pantalla principal ya tiene el suyo propio (topBar/FAB/bottomBar).
    // Envolver todo en OTRO Scaffold más generaba Scaffolds anidados — cada uno reservaba su propio
    // padding de barras de sistema y el resultado eran listas visualmente mucho más cortas de lo que
    // debían ser (bug reportado tras probar en dispositivo).
    val bottomBar: @Composable () -> Unit = {
        if (activeTab != null) {
            WalletBottomNavigationBar(
                activeTab = activeTab,
                onSelectTab = { tab ->
                    screen = when (tab) {
                        BottomNavTab.Accounts -> Screen.AccountsList
                        BottomNavTab.Transactions -> Screen.TransactionsList
                        BottomNavTab.Categories -> Screen.CategoriesList
                    }
                }
            )
        }
    }
    val onLogout: () -> Unit = {
        authViewModel.logout()
        screen = Screen.Login
    }

    when (val current = screen) {
        Screen.Login -> LoginScreen(
            onLoggedIn = { screen = Screen.AccountsList },
            onNavigateToRegister = { screen = Screen.Register }
        )
        Screen.Register -> RegisterScreen(
            onRegistered = { screen = Screen.Login }
        )
        Screen.AccountsList -> AccountListScreen(
            onAddAccount = { screen = Screen.AccountForm() },
            onEditAccount = { screen = Screen.AccountForm(it) },
            onLogout = onLogout,
            bottomBar = bottomBar
        )
        is Screen.AccountForm -> AccountFormScreen(
            existingAccount = current.account,
            onSaved = { screen = Screen.AccountsList },
            onDeleted = { screen = Screen.AccountsList },
            onCancel = { screen = Screen.AccountsList }
        )
        Screen.CategoriesList -> CategoryListScreen(
            onAddCategory = { screen = Screen.CategoryForm() },
            onEditCategory = { screen = Screen.CategoryForm(it) },
            onLogout = onLogout,
            bottomBar = bottomBar
        )
        is Screen.CategoryForm -> CategoryFormScreen(
            existingCategory = current.category,
            onSaved = { screen = Screen.CategoriesList },
            onDeleted = { screen = Screen.CategoriesList },
            onCancel = { screen = Screen.CategoriesList }
        )
        Screen.TransactionsList -> TransactionListScreen(
            onAddTransaction = { screen = Screen.TransactionForm() },
            onEditTransaction = { screen = Screen.TransactionForm(it) },
            onLogout = onLogout,
            bottomBar = bottomBar
        )
        is Screen.TransactionForm -> TransactionFormScreen(
            existingTransaction = current.transaction,
            onSaved = { screen = Screen.TransactionsList },
            onDeleted = { screen = Screen.TransactionsList },
            onCancel = { screen = Screen.TransactionsList },
            onNavigateToCreateAccount = { screen = Screen.AccountForm() }
        )
    }
}

// material-icons-core solo trae ~48 íconos (verificado durante la implementación) — no hay uno de
// "categorías"/grilla, así que esa pestaña usa un símbolo de texto en vez de material-icons-extended
// (research.md #4 de la feature 008: se prioriza no sumar esa dependencia pesada).
@Composable
private fun WalletBottomNavigationBar(activeTab: BottomNavTab, onSelectTab: (BottomNavTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = activeTab == BottomNavTab.Accounts,
            onClick = { onSelectTab(BottomNavTab.Accounts) },
            icon = { Icon(Icons.Default.AccountBox, contentDescription = null) },
            label = { Text("Cuentas") }
        )
        NavigationBarItem(
            selected = activeTab == BottomNavTab.Transactions,
            onClick = { onSelectTab(BottomNavTab.Transactions) },
            icon = { Icon(Icons.Default.List, contentDescription = null) },
            label = { Text("Movimientos") }
        )
        NavigationBarItem(
            selected = activeTab == BottomNavTab.Categories,
            onClick = { onSelectTab(BottomNavTab.Categories) },
            icon = { Text("▦", style = MaterialTheme.typography.titleMedium) },
            label = { Text("Categorías") }
        )
    }
}
