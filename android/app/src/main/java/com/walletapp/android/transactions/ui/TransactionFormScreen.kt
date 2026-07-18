package com.walletapp.android.transactions.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountViewModel
import com.walletapp.android.accounts.AccountsUiState
import com.walletapp.android.categories.CategoriesUiState
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.categories.CategoryViewModel
import com.walletapp.android.categories.displayLabel
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.TransactionViewModel

@Composable
fun TransactionFormScreen(
    existingTransaction: TransactionResponse? = null,
    onSaved: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onCancel: () -> Unit = {},
    onNavigateToCreateAccount: () -> Unit = {},
    viewModel: TransactionViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val isEditing = existingTransaction != null

    var type by remember { mutableStateOf(existingTransaction?.type ?: CategoryType.EXPENSE) }
    var amount by remember { mutableStateOf(existingTransaction?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(existingTransaction?.date) }
    var accountId by remember { mutableStateOf(existingTransaction?.accountId) }
    var categoryId by remember { mutableStateOf(existingTransaction?.categoryId) }
    var description by remember { mutableStateOf(existingTransaction?.description ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val accountsState by accountViewModel.uiState.collectAsState()
    val categoriesState by categoryViewModel.uiState.collectAsState()
    val accounts = (accountsState as? AccountsUiState.Success)?.accounts.orEmpty().map { it.account }
    val candidateCategories = (categoriesState as? CategoriesUiState.Success)?.categories.orEmpty()
        .filter { it.type == type }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (!isEditing) "Nuevo movimiento" else "Editar movimiento",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (accounts.isEmpty() && !isEditing) {
            Text(
                text = "Todavía no tenés ninguna cuenta. Creá una cuenta primero para poder registrar movimientos.",
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onNavigateToCreateAccount, modifier = Modifier.fillMaxWidth()) {
                Text("Crear cuenta")
            }
            TextButton(onClick = onCancel) { Text("Volver") }
            return@Column
        }

        Text(text = "Tipo", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            CategoryType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { if (!isEditing) type = option },
                    enabled = !isEditing,
                    label = { Text(option.displayLabel()) }
                )
            }
        }

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Monto") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Fecha", style = MaterialTheme.typography.labelLarge)
        DatePickerField(label = "Elegir fecha", date = date, onDateSelected = { date = it })

        Text(text = "Cuenta", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts, key = { it.id }) { account ->
                FilterChip(
                    selected = accountId == account.id,
                    onClick = { if (!isEditing) accountId = account.id },
                    enabled = !isEditing,
                    label = { Text(account.name) }
                )
            }
        }

        if (candidateCategories.isNotEmpty()) {
            Text(text = "Categoría (opcional)", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = { categoryId = null },
                        label = { Text("Ninguna") }
                    )
                }
                items(candidateCategories, key = { it.id }) { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                errorMessage = null
                val amountValue = amount.toDoubleOrNull()
                val selectedAccountId = accountId
                val selectedDate = date
                when {
                    amountValue == null || amountValue <= 0 -> errorMessage = "El monto debe ser un número mayor que cero"
                    selectedDate.isNullOrBlank() -> errorMessage = "Elegí una fecha"
                    selectedAccountId.isNullOrBlank() -> errorMessage = "Elegí una cuenta"
                    else -> {
                        val onResult: (Result<Unit>) -> Unit = { result ->
                            result.onSuccess { onSaved() }
                                .onFailure { errorMessage = it.message ?: "Error al guardar el movimiento" }
                        }
                        if (!isEditing) {
                            viewModel.create(type, amountValue, selectedDate, description.ifBlank { null }, selectedAccountId, categoryId, onResult)
                        } else {
                            viewModel.update(existingTransaction!!.id, amountValue, selectedDate, description.ifBlank { null }, categoryId, onResult)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        if (isEditing) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Eliminar movimiento")
            }
        }

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
        }

        TextButton(onClick = onCancel) {
            Text("Cancelar")
        }
    }

    if (showDeleteConfirm && existingTransaction != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar movimiento") },
            text = { Text("¿Seguro que querés eliminar este movimiento? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    errorMessage = null
                    viewModel.delete(existingTransaction.id) { result ->
                        result.onSuccess { onDeleted() }
                            .onFailure { errorMessage = it.message ?: "Error al eliminar el movimiento" }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
