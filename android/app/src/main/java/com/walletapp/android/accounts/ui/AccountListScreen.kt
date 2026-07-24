package com.walletapp.android.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountResponse
import com.walletapp.android.accounts.AccountType
import com.walletapp.android.accounts.AccountViewModel
import com.walletapp.android.accounts.AccountWithBalance
import com.walletapp.android.accounts.AccountsUiState
import com.walletapp.android.accounts.displayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    onAddAccount: () -> Unit = {},
    onEditAccount: (AccountResponse) -> Unit = {},
    onLogout: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // El saldo puede quedar desactualizado si se registró/editó/eliminó un movimiento desde la
    // pantalla de transacciones — esa pantalla usa su propia instancia de TransactionViewModel y no
    // tiene forma de avisarle a este AccountViewModel (que Compose retiene entre navegaciones) que
    // debe refrescar. Se refresca cada vez que se vuelve a entrar a esta pantalla en su lugar.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cuentas") },
                actions = {
                    // No hay pantalla de configuración todavía — el ícono cumple la función de
                    // cerrar sesión, que antes vivía en el menú Home ya eliminado (feature 008).
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAccount) {
                Icon(Icons.Default.Add, contentDescription = "Agregar cuenta")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            when (val current = state) {
                is AccountsUiState.Loading -> CircularProgressIndicator()
                is AccountsUiState.Error -> Text(text = current.message, color = MaterialTheme.colorScheme.error)
                is AccountsUiState.Success -> {
                    if (current.accounts.isEmpty()) {
                        Text(text = "Todavía no tenés cuentas. Tocá + para crear la primera.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(current.accounts, key = { it.account.id }) { entry ->
                                AccountRow(entry = entry, onClick = { onEditAccount(entry.account) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(entry: AccountWithBalance, onClick: () -> Unit) {
    val account = entry.account
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Text(text = account.type.emoji(), style = MaterialTheme.typography.titleMedium)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.type.displayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${entry.balance} ${account.currency}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
    }
}

// Sin material-icons-extended (research.md #4 de la feature 008) — un emoji por tipo de cuenta
// cumple el mismo rol visual que un ícono vectorial para esta escala de app.
private fun AccountType.emoji(): String = when (this) {
    AccountType.CASH -> "💵"
    AccountType.BANK -> "🏦"
    AccountType.CREDIT_CARD -> "💳"
    AccountType.OTHER -> "📦"
}
