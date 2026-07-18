package com.walletapp.android.accounts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walletapp.android.transactions.BalanceResponse
import com.walletapp.android.transactions.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AccountViewModel"

data class AccountWithBalance(val account: AccountResponse, val balance: Double)

sealed interface AccountsUiState {
    data object Loading : AccountsUiState
    data class Success(val accounts: List<AccountWithBalance>) : AccountsUiState
    data class Error(val message: String) : AccountsUiState
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository
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
                .onSuccess { accounts ->
                    Log.d(TAG, "refresh() -> Success (${accounts.size} cuentas), resolviendo saldos")
                    val withBalances = accounts.map { account ->
                        async {
                            val balance = transactionRepository.getBalance(account.id)
                                .getOrDefault(BalanceResponse(account.id, account.initialBalance))
                                .balance
                            AccountWithBalance(account, balance)
                        }
                    }.awaitAll()
                    _uiState.value = AccountsUiState.Success(withBalances)
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
