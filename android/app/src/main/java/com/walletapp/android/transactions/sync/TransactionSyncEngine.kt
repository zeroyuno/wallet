package com.walletapp.android.transactions.sync

import android.util.Log
import com.walletapp.android.transactions.TransactionApi
import com.walletapp.android.transactions.TransactionRequest
import com.walletapp.android.transactions.TransactionUpdateRequest
import com.walletapp.android.transactions.local.SyncCursorStore
import com.walletapp.android.transactions.local.SyncState
import com.walletapp.android.transactions.local.TransactionDao
import com.walletapp.android.transactions.local.TransactionEntity
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransactionSyncEngine"
private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404

@Singleton
class TransactionSyncEngine @Inject constructor(
    private val transactionSyncApi: TransactionSyncApi,
    private val transactionApi: TransactionApi,
    private val transactionDao: TransactionDao,
    private val syncCursorStore: SyncCursorStore
) {

    // Pull incremental (research.md #1, #7 de la feature 007): pide páginas hasta hasMore=false,
    // aplicándolas de a una a Room, y recién guarda el cursor de cada página una vez aplicada con
    // éxito — si una llamada o una escritura local falla a mitad, el cursor anterior queda intacto y
    // la próxima corrida retoma desde ahí (upserts/deletes son idempotentes, así que reaplicar una
    // página ya aplicada parcialmente no causa problemas).
    suspend fun pull() {
        var cursor = syncCursorStore.getCursor()
        var hasMore = true
        while (hasMore) {
            val response = transactionSyncApi.sync(since = cursor, limit = null)
            val upsertEntities = response.upserts.map { it.toEntity() }
            transactionDao.applySyncPage(upsertEntities, response.deletedIds)
            cursor = response.nextSince
            syncCursorStore.saveCursor(cursor)
            hasMore = response.hasMore
        }
    }

    // Push de pendientes (research.md "Push", #3 de la feature 007): cada fila se procesa aparte —
    // si una falla (sin conexión, error del servidor) queda pendiente para el próximo intento sin
    // bloquear al resto. Un fallo de conectividad real ya lo hace fallar pull() antes de llegar acá
    // (el worker que envuelve ambos, feature 007 US3, reintenta el ciclo completo en ese caso).
    suspend fun push() {
        for (entity in transactionDao.getPending()) {
            when (entity.syncState) {
                SyncState.PENDING_CREATE -> pushCreate(entity)
                SyncState.PENDING_UPDATE -> pushUpdate(entity)
                SyncState.PENDING_DELETE -> pushDelete(entity)
                SyncState.SYNCED -> Unit
            }
        }
    }

    private suspend fun pushCreate(entity: TransactionEntity) {
        try {
            transactionApi.create(
                TransactionRequest(
                    id = entity.id,
                    type = entity.type,
                    amount = entity.amount,
                    date = entity.date,
                    description = entity.description,
                    accountId = entity.accountId,
                    categoryId = entity.categoryId
                )
            )
            transactionDao.updateSyncState(entity.id, SyncState.SYNCED)
        } catch (e: HttpException) {
            if (e.code() == HTTP_CONFLICT) {
                // Ya se creó en un intento anterior cuya confirmación se perdió — no es un error real
                // (research.md #3): el id lo generó el cliente, así que un 409 significa "ya existe
                // con este mismo id", que es exactamente lo que se esperaba lograr.
                transactionDao.updateSyncState(entity.id, SyncState.SYNCED)
            } else {
                Log.w(TAG, "pushCreate(${entity.id}) -> ${e.code()}, queda pendiente")
            }
        } catch (e: IOException) {
            Log.w(TAG, "pushCreate(${entity.id}) -> sin conexión, queda pendiente")
        }
    }

    private suspend fun pushUpdate(entity: TransactionEntity) {
        try {
            transactionApi.update(
                entity.id,
                TransactionUpdateRequest(entity.amount, entity.date, entity.description, entity.categoryId)
            )
            transactionDao.updateSyncState(entity.id, SyncState.SYNCED)
        } catch (e: HttpException) {
            Log.w(TAG, "pushUpdate(${entity.id}) -> ${e.code()}, queda pendiente")
        } catch (e: IOException) {
            Log.w(TAG, "pushUpdate(${entity.id}) -> sin conexión, queda pendiente")
        }
    }

    private suspend fun pushDelete(entity: TransactionEntity) {
        try {
            transactionApi.delete(entity.id)
            transactionDao.deleteById(entity.id)
        } catch (e: HttpException) {
            if (e.code() == HTTP_NOT_FOUND) {
                // Ya no existe en el backend (un intento anterior sí llegó a borrarlo) — limpiar local.
                transactionDao.deleteById(entity.id)
            } else {
                Log.w(TAG, "pushDelete(${entity.id}) -> ${e.code()}, queda pendiente")
            }
        } catch (e: IOException) {
            Log.w(TAG, "pushDelete(${entity.id}) -> sin conexión, queda pendiente")
        }
    }
}

private fun TransactionSyncItemResponse.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type,
    amount = amount,
    date = date,
    description = description,
    accountId = accountId,
    categoryId = categoryId,
    updatedAt = updatedAt,
    syncState = SyncState.SYNCED
)
