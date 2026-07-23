package com.walletapp.android.transactions.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface TransactionDao {

    // Filtros nullable: sin filtrar por ese criterio si es null (mismo criterio que
    // TransactionRepository.findAllByUserId en el backend). Oculta las filas PENDING_DELETE — ya
    // desaparecieron para el usuario aunque el borrado no haya llegado todavía al backend.
    @Query(
        """
        SELECT * FROM transactions
        WHERE syncState != 'PENDING_DELETE'
          AND (:accountId IS NULL OR accountId = :accountId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:dateFrom IS NULL OR date >= :dateFrom)
          AND (:dateTo IS NULL OR date <= :dateTo)
        ORDER BY date DESC, id DESC
        """
    )
    fun pagingSource(
        accountId: String?,
        categoryId: String?,
        dateFrom: String?,
        dateTo: String?
    ): PagingSource<Int, TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Upsert
    suspend fun upsert(entity: TransactionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM transactions WHERE syncState != 'SYNCED'")
    suspend fun getPending(): List<TransactionEntity>

    @Query("UPDATE transactions SET syncState = :state WHERE id = :id")
    suspend fun updateSyncState(id: String, state: SyncState)

    // Aplica una página del feed de sincronización de forma atómica (research.md #1 de la feature
    // 007) — si algo falla a mitad, no queda una página parcialmente aplicada con el cursor ya avanzado.
    @Transaction
    suspend fun applySyncPage(upserts: List<TransactionEntity>, deletedIds: List<String>) {
        if (upserts.isNotEmpty()) upsertAll(upserts)
        if (deletedIds.isNotEmpty()) deleteByIds(deletedIds)
    }
}
