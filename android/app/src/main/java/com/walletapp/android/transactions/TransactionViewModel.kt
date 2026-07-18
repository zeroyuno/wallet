package com.walletapp.android.transactions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walletapp.android.categories.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TransactionViewModel"

sealed interface TransactionsUiState {
    data object Loading : TransactionsUiState
    data class Success(val transactions: List<TransactionResponse>, val filter: TransactionFilter) : TransactionsUiState
    data class Error(val message: String) : TransactionsUiState
}

@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionsUiState>(TransactionsUiState.Loading)
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var currentFilter: TransactionFilter = TransactionFilter()

    init {
        refresh()
    }

    fun refresh() {
        val filter = currentFilter
        Log.d(TAG, "refresh() -> Loading (filter=$filter)")
        _uiState.value = TransactionsUiState.Loading
        viewModelScope.launch {
            transactionRepository.list(filter)
                .onSuccess {
                    Log.d(TAG, "refresh() -> Success (${it.size} movimientos)")
                    _uiState.value = TransactionsUiState.Success(it, filter)
                }
                .onFailure {
                    Log.e(TAG, "refresh() -> Error: ${it.message}")
                    _uiState.value = TransactionsUiState.Error(it.message ?: "Error al cargar movimientos")
                }
        }
    }

    fun applyFilter(filter: TransactionFilter) {
        currentFilter = filter
        refresh()
    }

    fun create(
        type: CategoryType,
        amount: Double,
        date: String,
        description: String?,
        accountId: String,
        categoryId: String?,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = transactionRepository.create(type, amount, date, description, accountId, categoryId)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "create() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }

    fun update(
        id: String,
        amount: Double,
        date: String,
        description: String?,
        categoryId: String?,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = transactionRepository.update(id, amount, date, description, categoryId)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "update() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }

    fun delete(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = transactionRepository.delete(id)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "delete() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }
}
