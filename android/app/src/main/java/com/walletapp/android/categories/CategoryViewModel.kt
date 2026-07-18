package com.walletapp.android.categories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "CategoryViewModel"

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Success(val categories: List<CategoryResponse>) : CategoriesUiState
    data class Error(val message: String) : CategoriesUiState
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        Log.d(TAG, "refresh() -> Loading")
        _uiState.value = CategoriesUiState.Loading
        viewModelScope.launch {
            categoryRepository.list()
                .onSuccess {
                    Log.d(TAG, "refresh() -> Success (${it.size} categorías)")
                    _uiState.value = CategoriesUiState.Success(it)
                }
                .onFailure {
                    Log.e(TAG, "refresh() -> Error: ${it.message}")
                    _uiState.value = CategoriesUiState.Error(it.message ?: "Error al cargar categorías")
                }
        }
    }

    fun create(name: String, type: CategoryType, parentCategoryId: String?, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = categoryRepository.create(name, type, parentCategoryId)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "create() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }

    fun update(id: String, name: String, type: CategoryType, parentCategoryId: String?, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = categoryRepository.update(id, name, type, parentCategoryId)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "update() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }

    fun delete(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = categoryRepository.delete(id)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "delete() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }
}
