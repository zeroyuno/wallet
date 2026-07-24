package com.walletapp.android.accounts.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountResponse
import com.walletapp.android.accounts.AccountType
import com.walletapp.android.accounts.AccountViewModel
import com.walletapp.android.accounts.AccountWithBalance
import com.walletapp.android.accounts.AccountsUiState
import com.walletapp.android.accounts.displayLabel
import com.walletapp.android.accounts.emoji
import java.util.Locale

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
    var selectedType by remember { mutableStateOf<AccountType?>(null) }

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
                        val filtered = if (selectedType == null) {
                            current.accounts
                        } else {
                            current.accounts.filter { it.account.type == selectedType }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = selectedType == null,
                                onClick = { selectedType = null },
                                label = { Text("Todas") }
                            )
                            AccountType.entries.forEach { option ->
                                FilterChip(
                                    selected = selectedType == option,
                                    onClick = { selectedType = option },
                                    label = { Text(option.displayLabel()) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (filtered.isEmpty()) {
                            Text(text = "No hay cuentas de este tipo.")
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered, key = { it.account.id }) { entry ->
                                    AccountRow(entry = entry, onClick = { onEditAccount(entry.account) })
                                }
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
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterVertically)
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
                    fontWeight = FontWeight.Bold,
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$${String.format(Locale.getDefault(), "%,.2f", entry.balance)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = account.currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
