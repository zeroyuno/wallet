package com.walletapp.android.accounts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AccountViewModel"

sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data class Success(val accounts: List<AccountResponse>) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountsUiState>(AccountsUiState.Loading)
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        Log.d(TAG, "refresh() -> Loading")
        _uiState.value = AccountsUiState.Loading
        viewModelScope.launch {
            accountRepository.list()
                .onSuccess {
                    Log.d(TAG, "refresh() -> Success (${it.size} cuentas)")
                    _uiState.value = AccountsUiState.Success(it)
                }
                .onFailure {
                    Log.e(TAG, "refresh() -> Error: ${it.message}")
                    _uiState.value = AccountsUiState.Error(it.message ?: "Error al cargar cuentas")
                }
        }
    }

    fun create(name: String, type: AccountType, currency: String, initialBalance: Double, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = accountRepository.create(name, type, currency, initialBalance)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "create() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }

    fun update(id: String, name: String, type: AccountType, currency: String, initialBalance: Double, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = accountRepository.update(id, name, type, currency, initialBalance)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "update() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }

    fun delete(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = accountRepository.delete(id)
            result.onSuccess { refresh() }
            result.onFailure { error -> Log.e(TAG, "delete() -> Error: ${error.message}") }
            onResult(result.map {})
        }
    }
}
