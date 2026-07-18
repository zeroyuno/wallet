package com.walletapp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
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
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.ui.TransactionFormScreen
import com.walletapp.android.transactions.ui.TransactionListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold { innerPadding ->
                    Surface(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        WalletApp()
                    }
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Login : Screen
    data object Register : Screen
    data object Home : Screen
    data object AccountsList : Screen
    data class AccountForm(val account: AccountResponse? = null) : Screen
    data object CategoriesList : Screen
    data class CategoryForm(val category: CategoryResponse? = null) : Screen
    data object TransactionsList : Screen
    data class TransactionForm(val transaction: TransactionResponse? = null) : Screen
}

@Composable
private fun WalletApp(authViewModel: AuthViewModel = hiltViewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
    val sessionState by authViewModel.sessionState.collectAsState()

    // Al abrir la app se valida contra el backend si el token guardado sigue siendo válido
    // (no expiró, no fue revocado desde otro dispositivo/logout) antes de decidir la pantalla
    // inicial — así una sesión iniciada previamente no vuelve a pedir login (spec 001, US3).
    LaunchedEffect(Unit) { authViewModel.checkSession() }
    LaunchedEffect(sessionState) {
        if (sessionState == SessionCheckState.LoggedIn && screen == Screen.Login) {
            screen = Screen.Home
        }
    }

    // Login y Home no tienen "atrás" dentro de la app — ahí el botón atrás del sistema
    // hace lo de siempre (fondo/salir). El resto de las pantallas sí tienen un padre lógico.
    BackHandler(enabled = screen != Screen.Login && screen != Screen.Home) {
        screen = when (val current = screen) {
            Screen.Login, Screen.Home -> screen
            Screen.Register -> Screen.Login
            Screen.AccountsList -> Screen.Home
            is Screen.AccountForm -> Screen.AccountsList
            Screen.CategoriesList -> Screen.Home
            is Screen.CategoryForm -> Screen.CategoriesList
            Screen.TransactionsList -> Screen.Home
            is Screen.TransactionForm -> Screen.TransactionsList
        }
    }

    if (sessionState == SessionCheckState.Checking) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when (val current = screen) {
        Screen.Login -> LoginScreen(
            onLoggedIn = { screen = Screen.Home },
            onNavigateToRegister = { screen = Screen.Register }
        )
        Screen.Register -> RegisterScreen(
            onRegistered = { screen = Screen.Login }
        )
        Screen.Home -> HomeScreen(
            onLoggedOut = { screen = Screen.Login },
            onOpenAccounts = { screen = Screen.AccountsList },
            onOpenCategories = { screen = Screen.CategoriesList },
            onOpenTransactions = { screen = Screen.TransactionsList }
        )
        Screen.AccountsList -> AccountListScreen(
            onAddAccount = { screen = Screen.AccountForm() },
            onEditAccount = { screen = Screen.AccountForm(it) }
        )
        is Screen.AccountForm -> AccountFormScreen(
            existingAccount = current.account,
            onSaved = { screen = Screen.AccountsList },
            onDeleted = { screen = Screen.AccountsList },
            onCancel = { screen = Screen.AccountsList }
        )
        Screen.CategoriesList -> CategoryListScreen(
            onAddCategory = { screen = Screen.CategoryForm() },
            onEditCategory = { screen = Screen.CategoryForm(it) }
        )
        is Screen.CategoryForm -> CategoryFormScreen(
            existingCategory = current.category,
            onSaved = { screen = Screen.CategoriesList },
            onDeleted = { screen = Screen.CategoriesList },
            onCancel = { screen = Screen.CategoriesList }
        )
        Screen.TransactionsList -> TransactionListScreen(
            onAddTransaction = { screen = Screen.TransactionForm() },
            onEditTransaction = { screen = Screen.TransactionForm(it) }
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

@Composable
private fun HomeScreen(
    onLoggedOut: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTransactions: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Wallet", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = onOpenTransactions, modifier = Modifier.fillMaxWidth()) {
            Text("Mis movimientos")
        }
        Button(onClick = onOpenAccounts, modifier = Modifier.fillMaxWidth()) {
            Text("Mis cuentas")
        }
        Button(onClick = onOpenCategories, modifier = Modifier.fillMaxWidth()) {
            Text("Mis categorías")
        }
        OutlinedButton(
            onClick = {
                viewModel.logout()
                onLoggedOut()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}
