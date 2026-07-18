package com.walletapp.android.categories.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.walletapp.android.categories.CategoriesUiState
import com.walletapp.android.categories.CategoryResponse
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.categories.CategoryViewModel
import com.walletapp.android.categories.displayLabel

@Composable
fun CategoryFormScreen(
    existingCategory: CategoryResponse? = null,
    onSaved: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: CategoryViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf(existingCategory?.name ?: "") }
    var type by remember { mutableStateOf(existingCategory?.type ?: CategoryType.EXPENSE) }
    var parentCategoryId by remember { mutableStateOf(existingCategory?.parentCategoryId) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val state by viewModel.uiState.collectAsState()
    val candidateParents = (state as? CategoriesUiState.Success)?.categories.orEmpty()
        .filter { it.type == type && it.id != existingCategory?.id }

    // Si cambia el tipo y el padre elegido ya no es válido para ese tipo, se limpia.
    LaunchedEffect(type) {
        if (parentCategoryId != null && candidateParents.none { it.id == parentCategoryId }) {
            parentCategoryId = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (existingCategory == null) "Nueva categoría" else "Editar categoría",
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            CategoryType.entries.forEach { option ->
                FilterChip(
                    selected = type == option,
                    onClick = { type = option },
                    label = { Text(option.displayLabel()) }
                )
            }
        }

        if (candidateParents.isNotEmpty()) {
            Text(text = "Categoría padre (opcional)", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = parentCategoryId == null,
                        onClick = { parentCategoryId = null },
                        label = { Text("Ninguna") }
                    )
                }
                items(candidateParents, key = { it.id }) { candidate ->
                    FilterChip(
                        selected = parentCategoryId == candidate.id,
                        onClick = { parentCategoryId = candidate.id },
                        label = { Text(candidate.name) }
                    )
                }
            }
        }

        Button(
            onClick = {
                errorMessage = null
                val onResult: (Result<Unit>) -> Unit = { result ->
                    result.onSuccess { onSaved() }
                        .onFailure { errorMessage = it.message ?: "Error al guardar la categoría" }
                }
                if (existingCategory == null) {
                    viewModel.create(name, type, parentCategoryId, onResult)
                } else {
                    viewModel.update(existingCategory.id, name, type, parentCategoryId, onResult)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar")
        }

        if (existingCategory != null) {
            OutlinedButton(
                onClick = {
                    errorMessage = null
                    viewModel.delete(existingCategory.id) { result ->
                        result.onSuccess { onDeleted() }
                            .onFailure { errorMessage = it.message ?: "Error al eliminar la categoría" }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Eliminar categoría")
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
