package com.walletapp.android.transactions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.transactions.sync.TransactionSyncEngine
import com.walletapp.android.transactions.sync.TransactionSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "TransactionViewModel"

sealed interface SyncUiState {
    data object Idle : SyncUiState
    // total=0 significa "todavía no se sabe cuánto falta" (antes de recibir la primera página) — la
    // UI solo muestra la barra de progreso cuando total > 0 (research.md #8 de la feature 007).
    data class Syncing(val imported: Int = 0, val total: Int = 0) : SyncUiState
    data class Error(val message: String) : SyncUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val transactionSyncEngine: TransactionSyncEngine,
    private val transactionSyncScheduler: TransactionSyncScheduler
) : ViewModel() {

    private val _filter = MutableStateFlow(TransactionFilter())
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()

    // Feature 007, US1: la lista viene de Room (paginada), no de una llamada de red por apertura de
    // pantalla — abre al instante sin importar el volumen ya sincronizado (SC-001).
    val transactions: Flow<PagingData<TransactionResponse>> = _filter
        .flatMapLatest { transactionRepository.pagedList(it) }
        .cachedIn(viewModelScope)

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    init {
        sync()
    }

    fun applyFilter(filter: TransactionFilter) {
        _filter.value = filter
    }

    // Dispara el pull incremental (research.md #1) — se llama al abrir la pantalla y desde
    // "pull to refresh"; los resultados llegan a la UI vía el Flow de `transactions`, no acá.
    fun sync() {
        viewModelScope.launch {
            _syncState.value = SyncUiState.Syncing()
            runCatching {
                transactionSyncEngine.pull { imported, total ->
                    _syncState.value = SyncUiState.Syncing(imported, total)
                }
            }
                .onSuccess {
                    Log.d(TAG, "sync() -> OK")
                    _syncState.value = SyncUiState.Idle
                }
                .onFailure { error ->
                    Log.e(TAG, "sync() -> Error: ${error.message}")
                    _syncState.value = SyncUiState.Error(error.message ?: "Error al sincronizar")
                }
        }
    }

    // create/update/delete escriben primero en local (TransactionRepository) — la lista ya refleja el
    // cambio antes de que esta función siquiera termine (Paging 3 se refresca solo al detectar el
    // write en Room). El push al backend se dispara aparte, sin bloquear el resultado para la UI.
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
            result.onSuccess { push() }
            result.onFailure { error -> Log.e(TAG, "create() -> Error: ${error.message}") }
            onResult(result)
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
            result.onSuccess { push() }
            result.onFailure { error -> Log.e(TAG, "update() -> Error: ${error.message}") }
            onResult(result)
        }
    }

    fun delete(id: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = transactionRepository.delete(id)
            result.onSuccess { push() }
            result.onFailure { error -> Log.e(TAG, "delete() -> Error: ${error.message}") }
            onResult(result)
        }
    }

    // Encola el push en WorkManager (feature 007, US3) en vez de llamarlo directo — sobrevive al
    // cierre de la app y reintenta con backoff si falla (TransactionSyncWorker/TransactionSyncRunner).
    private fun push() {
        transactionSyncScheduler.triggerImmediate()
    }
}
