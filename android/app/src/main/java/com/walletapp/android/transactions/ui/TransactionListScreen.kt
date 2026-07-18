package com.walletapp.android.transactions.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.accounts.AccountViewModel
import com.walletapp.android.accounts.AccountsUiState
import com.walletapp.android.categories.CategoriesUiState
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.categories.CategoryViewModel
import com.walletapp.android.categories.displayLabel
import com.walletapp.android.transactions.TransactionFilter
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.TransactionViewModel
import com.walletapp.android.transactions.TransactionsUiState

@Composable
fun TransactionListScreen(
    onAddTransaction: () -> Unit = {},
    onEditTransaction: (TransactionResponse) -> Unit = {},
    viewModel: TransactionViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val accountsState by accountViewModel.uiState.collectAsState()
    val categoriesState by categoryViewModel.uiState.collectAsState()
    val accounts = (accountsState as? AccountsUiState.Success)?.accounts.orEmpty().map { it.account }
    val categories = (categoriesState as? CategoriesUiState.Success)?.categories.orEmpty()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "Agregar movimiento")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            Text(text = "Mis movimientos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            val currentFilter = (state as? TransactionsUiState.Success)?.filter ?: TransactionFilter()

            if (accounts.isNotEmpty()) {
                Text(text = "Filtrar por cuenta", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = currentFilter.accountId == null,
                        onClick = { viewModel.applyFilter(currentFilter.copy(accountId = null)) },
                        label = { Text("Todas") }
                    )
                    accounts.forEach { account ->
                        FilterChip(
                            selected = currentFilter.accountId == account.id,
                            onClick = { viewModel.applyFilter(currentFilter.copy(accountId = account.id)) },
                            label = { Text(account.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (categories.isNotEmpty()) {
                Text(text = "Filtrar por categoría", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(
                        selected = currentFilter.categoryId == null,
                        onClick = { viewModel.applyFilter(currentFilter.copy(categoryId = null)) },
                        label = { Text("Todas") }
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = currentFilter.categoryId == category.id,
                            onClick = { viewModel.applyFilter(currentFilter.copy(categoryId = category.id)) },
                            label = { Text(category.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(text = "Filtrar por fecha", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DatePickerField(
                    label = "Desde",
                    date = currentFilter.dateFrom,
                    onDateSelected = { viewModel.applyFilter(currentFilter.copy(dateFrom = it)) }
                )
                DatePickerField(
                    label = "Hasta",
                    date = currentFilter.dateTo,
                    onDateSelected = { viewModel.applyFilter(currentFilter.copy(dateTo = it)) }
                )
                if (currentFilter.dateFrom != null || currentFilter.dateTo != null) {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.applyFilter(currentFilter.copy(dateFrom = null, dateTo = null)) },
                        label = { Text("Quitar") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (val current = state) {
                is TransactionsUiState.Loading -> CircularProgressIndicator()
                is TransactionsUiState.Error -> Text(text = current.message, color = MaterialTheme.colorScheme.error)
                is TransactionsUiState.Success -> {
                    if (current.transactions.isEmpty()) {
                        Text(text = "Todavía no tenés movimientos. Tocá + para registrar el primero.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(current.transactions, key = { it.id }) { transaction ->
                                val category = categories.find { it.id == transaction.categoryId }
                                TransactionRow(
                                    transaction = transaction,
                                    accountName = accounts.find { it.id == transaction.accountId }?.name,
                                    categoryName = category?.name,
                                    onClick = { onEditTransaction(transaction) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: TransactionResponse,
    accountName: String?,
    categoryName: String?,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = transaction.description?.takeIf { it.isNotBlank() } ?: transaction.type.displayLabel(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(accountName, categoryName, transaction.date).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val isIncome = transaction.type == CategoryType.INCOME
            Text(
                text = "${if (isIncome) "+" else "-"}${transaction.amount}",
                style = MaterialTheme.typography.titleMedium,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
