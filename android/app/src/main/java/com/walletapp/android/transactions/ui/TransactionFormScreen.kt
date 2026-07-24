package com.walletapp.android.transactions.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountViewModel
import com.walletapp.android.accounts.AccountsUiState
import com.walletapp.android.accounts.displayLabel
import com.walletapp.android.accounts.emoji
import com.walletapp.android.categories.CategoriesUiState
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.categories.CategoryViewModel
import com.walletapp.android.categories.displayLabel
import com.walletapp.android.categories.emoji
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
    var accountMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }

    val accountsState by accountViewModel.uiState.collectAsState()
    val categoriesState by categoryViewModel.uiState.collectAsState()
    val accounts = (accountsState as? AccountsUiState.Success)?.accounts.orEmpty().map { it.account }
    val candidateCategories = (categoriesState as? CategoriesUiState.Success)?.categories.orEmpty()
        .filter { it.type == type }
    val selectedAccount = accounts.find { it.id == accountId }
    val selectedCategory = candidateCategories.find { it.id == categoryId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryType.entries.forEach { option ->
                val selected = type == option
                Surface(
                    onClick = { if (!isEditing) type = option },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = option.displayLabel(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CANTIDAD TOTAL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = amount,
                    onValueChange = { new -> if (isValidAmountInput(new)) amount = new },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.widthIn(min = 80.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (amount.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        Text(text = "Fecha", style = MaterialTheme.typography.labelLarge)
        DatePickerField(
            label = "Elegir fecha",
            date = date,
            onDateSelected = { date = it },
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Cuenta de origen", style = MaterialTheme.typography.labelLarge)
        Box {
            Surface(
                onClick = { if (!isEditing) accountMenuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = selectedAccount?.type?.emoji() ?: "💼", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedAccount?.name ?: "Elegí una cuenta",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedAccount != null) {
                            Text(
                                text = selectedAccount.type.displayLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            accountId = account.id
                            accountMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (candidateCategories.isNotEmpty()) {
            Text(text = "Categoría (opcional)", style = MaterialTheme.typography.labelLarge)
            Box {
                Surface(
                    onClick = { categoryMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = selectedCategory?.emoji() ?: "🚫", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = selectedCategory?.name ?: "Ninguna",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Ninguna") },
                        onClick = {
                            categoryId = null
                            categoryMenuExpanded = false
                        }
                    )
                    candidateCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                categoryId = category.id
                                categoryMenuExpanded = false
                            }
                        )
                    }
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

// El monto se persiste en NUMERIC(28,9) — hasta 19 dígitos enteros y 9 decimales. Se filtra en
// cada tecleo en vez de validar recién al guardar, para que sea imposible escribir un valor que
// el backend después rechace.
private fun isValidAmountInput(value: String): Boolean {
    if (value.isEmpty()) return true
    if (!value.matches(Regex("^\\d*\\.?\\d*$"))) return false
    val parts = value.split(".")
    val integerPart = parts[0]
    val decimalPart = parts.getOrNull(1) ?: ""
    return integerPart.length <= 19 && decimalPart.length <= 9
}
