package com.walletapp.android.categories.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.categories.CategoriesUiState
import com.walletapp.android.categories.CategoryResponse
import com.walletapp.android.categories.CategoryViewModel
import com.walletapp.android.categories.displayLabel

@Composable
fun CategoryListScreen(
    onAddCategory: () -> Unit = {},
    onEditCategory: (CategoryResponse) -> Unit = {},
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCategory) {
                Icon(Icons.Default.Add, contentDescription = "Agregar categoría")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            Text(text = "Mis categorías", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            when (val current = state) {
                is CategoriesUiState.Loading -> CircularProgressIndicator()
                is CategoriesUiState.Error -> Text(text = current.message, color = MaterialTheme.colorScheme.error)
                is CategoriesUiState.Success -> {
                    if (current.categories.isEmpty()) {
                        Text(text = "Todavía no tenés categorías. Tocá + para crear la primera.")
                    } else {
                        val byId = current.categories.associateBy { it.id }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(current.categories, key = { it.id }) { category ->
                                CategoryRow(
                                    category = category,
                                    parentName = category.parentCategoryId?.let { byId[it]?.name },
                                    onClick = { onEditCategory(category) }
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
private fun CategoryRow(category: CategoryResponse, parentName: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                if (parentName != null) {
                    Text(
                        text = "↳ $parentName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(text = category.type.displayLabel(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
