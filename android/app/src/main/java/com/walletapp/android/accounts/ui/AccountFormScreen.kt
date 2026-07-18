package com.walletapp.android.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountResponse
import com.walletapp.android.accounts.AccountType
import com.walletapp.android.accounts.AccountViewModel

@Composable
fun AccountFormScreen(
    existingAccount: AccountResponse? = null,
    onSaved: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf(existingAccount?.name ?: "") }
    var type by remember { mutableStateOf(existingAccount?.type ?: AccountType.CASH) }
    var currency by remember { mutableStateOf(existingAccount?.currency ?: "USD") }
    var initialBalance by remember { mutableStateOf(existingAccount?.initialBalance?.toString() ?: "0") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (existingAccount == null) "Nueva cuenta" else "Editar cuenta",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Tipo", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option.name) }
                )
            }
        }

        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it.uppercase() },
            label = { Text("Moneda (ej. USD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = initialBalance,
            onValueChange = { initialBalance = it },
            label = { Text("Saldo inicial") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                errorMessage = null
                val balance = initialBalance.toDoubleOrNull() ?: 0.0
                val onResult: (Result<Unit>) -> Unit = { result ->
                    result.onSuccess { onSaved() }
                        .onFailure { errorMessage = it.message ?: "Error al guardar la cuenta" }
                }
                if (existingAccount == null) {
                    viewModel.create(name, type, currency, balance, onResult)
                } else {
                    viewModel.update(existingAccount.id, name, type, currency, balance, onResult)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        if (existingAccount != null) {
            OutlinedButton(
                onClick = {
                    errorMessage = null
                    viewModel.delete(existingAccount.id) { result ->
                        result.onSuccess { onDeleted() }
                            .onFailure { errorMessage = it.message ?: "Error al eliminar la cuenta" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Eliminar cuenta")
            }
        }

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = onCancel) {
            Text("Cancelar")
        }
    }
}
