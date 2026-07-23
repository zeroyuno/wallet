package com.walletapp.android.transactions

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.transactions.local.SyncState
import com.walletapp.android.transactions.local.TransactionDao
import com.walletapp.android.transactions.local.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TransactionFilter(
    val accountId: String? = null,
    val categoryId: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null
)

private const val PAGE_SIZE = 50

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionApi: TransactionApi,
    private val transactionDao: TransactionDao
) {

    // Fuente de la UI (feature 007, US1): lee de Room paginado, nunca de la red directamente — la
    // pantalla de movimientos abre al instante sin importar el volumen ya sincronizado.
    fun pagedList(filter: TransactionFilter = TransactionFilter()): Flow<PagingData<TransactionResponse>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                transactionDao.pagingSource(filter.accountId, filter.categoryId, filter.dateFrom, filter.dateTo)
            }
        ).flow.map { pagingData -> pagingData.map { it.toResponse() } }

    // Escritura optimista local (feature 007, US2): se refleja al instante en Room — Paging 3
    // invalida y refresca la lista sola, sin esperar respuesta del backend. El envío real ocurre en
    // segundo plano (TransactionSyncEngine.push()).
    suspend fun create(
        type: CategoryType,
        amount: Double,
        date: String,
        description: String?,
        accountId: String,
        categoryId: String?
    ): Result<Unit> = runCatching {
        transactionDao.upsert(
            TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = type,
                amount = amount,
                date = date,
                description = description,
                accountId = accountId,
                categoryId = categoryId,
                updatedAt = Instant.now().toString(),
                syncState = SyncState.PENDING_CREATE
            )
        )
    }

    suspend fun update(
        id: String,
        amount: Double,
        date: String,
        description: String?,
        categoryId: String?
    ): Result<Unit> = runCatching {
        val existing = transactionDao.getById(id) ?: error("Movimiento no encontrado localmente: $id")
        // Si todavía no se envió la creación al backend, sigue siendo PENDING_CREATE (edge case de
        // spec.md: solo se termina enviando el estado final, no cada paso intermedio).
        val newState = if (existing.syncState == SyncState.PENDING_CREATE) SyncState.PENDING_CREATE
        else SyncState.PENDING_UPDATE
        transactionDao.upsert(
            existing.copy(
                amount = amount,
                date = date,
                description = description,
                categoryId = categoryId,
                updatedAt = Instant.now().toString(),
                syncState = newState
            )
        )
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        val existing = transactionDao.getById(id) ?: error("Movimiento no encontrado localmente: $id")
        if (existing.syncState == SyncState.PENDING_CREATE) {
            // Nunca llegó a existir en el backend — se borra local sin más, no hay nada que avisar.
            transactionDao.deleteById(id)
        } else {
            transactionDao.updateSyncState(id, SyncState.PENDING_DELETE)
        }
    }

    suspend fun getBalance(accountId: String): Result<BalanceResponse> =
        runCatching { transactionApi.getBalance(accountId) }
}

private fun TransactionEntity.toResponse(): TransactionResponse = TransactionResponse(
    id = id,
    type = type,
    amount = amount,
    date = date,
    description = description,
    accountId = accountId,
    categoryId = categoryId
)
