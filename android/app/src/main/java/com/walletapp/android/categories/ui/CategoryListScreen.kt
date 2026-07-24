package com.walletapp.android.categories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.categories.CategoriesUiState
import com.walletapp.android.categories.CategoryResponse
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.categories.CategoryViewModel
import com.walletapp.android.categories.displayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    onAddCategory: () -> Unit = {},
    onEditCategory: (CategoryResponse) -> Unit = {},
    onLogout: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorías") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCategory) {
                Icon(Icons.Default.Add, contentDescription = "Agregar categoría")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            when (val current = state) {
                is CategoriesUiState.Loading -> CircularProgressIndicator()
                is CategoriesUiState.Error -> Text(text = current.message, color = MaterialTheme.colorScheme.error)
                is CategoriesUiState.Success -> {
                    if (current.categories.isEmpty()) {
                        Text(text = "Todavía no tenés categorías. Tocá + para crear la primera.")
                    } else {
                        val byId = current.categories.associateBy { it.id }
                        val expenses = current.categories.filter { it.type == CategoryType.EXPENSE }
                        val incomes = current.categories.filter { it.type == CategoryType.INCOME }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categorySection(title = "Gastos", categories = expenses, byId = byId, onEditCategory = onEditCategory)
                            categorySection(title = "Ingresos", categories = incomes, byId = byId, onEditCategory = onEditCategory)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categorySection(
    title: String,
    categories: List<CategoryResponse>,
    byId: Map<String, CategoryResponse>,
    onEditCategory: (CategoryResponse) -> Unit
) {
    if (categories.isEmpty()) return
    item {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    items(categories, key = { it.id }) { category ->
        CategoryRow(
            category = category,
            parentName = category.parentCategoryId?.let { byId[it]?.name },
            onClick = { onEditCategory(category) }
        )
    }
}

@Composable
private fun CategoryRow(category: CategoryResponse, parentName: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (parentName != null) {
                    Text(
                        text = "↳ $parentName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(text = category.type.displayLabel(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
