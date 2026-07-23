package com.walletapp.android.transactions.sync

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.work.ListenableWorker
import com.walletapp.android.transactions.BalanceResponse
import com.walletapp.android.transactions.TransactionApi
import com.walletapp.android.transactions.TransactionRequest
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.TransactionUpdateRequest
import com.walletapp.android.transactions.local.SyncCursorStore
import com.walletapp.android.transactions.local.SyncState
import com.walletapp.android.transactions.local.TransactionDao
import com.walletapp.android.transactions.local.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

// Nombres prefijados con "Runner" para no chocar con los fakes homónimos de TransactionSyncEngineTest
// (mismo paquete — Kotlin no permite dos clases top-level con el mismo nombre por archivo, aunque
// ambas sean private).
private class RunnerFakeTransactionSyncApi : TransactionSyncApi {
    var error: Throwable? = null
    override suspend fun sync(since: String?, limit: Int?): TransactionSyncResponse {
        error?.let { throw it }
        return TransactionSyncResponse(emptyList(), emptyList(), "cursor-0", false, 0)
    }
}

private class RunnerFakeTransactionApi : TransactionApi {
    override suspend fun create(request: TransactionRequest): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun get(id: String): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun update(id: String, request: TransactionUpdateRequest): TransactionResponse =
        throw UnsupportedOperationException()
    override suspend fun delete(id: String) = Unit
    override suspend fun getBalance(accountId: String): BalanceResponse = BalanceResponse(accountId, 0.0)
}

private class RunnerFakeSyncCursorStore : SyncCursorStore {
    private var cursor: String? = null
    override fun getCursor(): String? = cursor
    override fun saveCursor(cursor: String) {
        this.cursor = cursor
    }
    override fun clear() {
        cursor = null
    }
}

private class RunnerFakeTransactionDao : TransactionDao {
    private val entities = mutableListOf<TransactionEntity>()

    override fun pagingSource(accountId: String?, categoryId: String?, dateFrom: String?, dateTo: String?):
            PagingSource<Int, TransactionEntity> = object : PagingSource<Int, TransactionEntity>() {
        override fun getRefreshKey(state: PagingState<Int, TransactionEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionEntity> =
            LoadResult.Page(entities.toList(), null, null)
    }

    override suspend fun getById(id: String): TransactionEntity? = entities.find { it.id == id }
    override suspend fun upsert(entity: TransactionEntity) {
        entities.removeAll { it.id == entity.id }
        entities.add(entity)
    }
    override suspend fun upsertAll(entities: List<TransactionEntity>) {
        entities.forEach { upsert(it) }
    }
    override suspend fun deleteById(id: String) {
        entities.removeAll { it.id == id }
    }
    override suspend fun deleteByIds(ids: List<String>) {
        entities.removeAll { it.id in ids }
    }
    override suspend fun getPending(): List<TransactionEntity> = entities.filter { it.syncState != SyncState.SYNCED }
    override suspend fun updateSyncState(id: String, state: SyncState) {
        val index = entities.indexOfFirst { it.id == id }
        if (index >= 0) entities[index] = entities[index].copy(syncState = state)
    }
}

class TransactionSyncRunnerTest {

    @Test
    fun `run returns success when pull and push both succeed`() = runTest {
        val syncApi = RunnerFakeTransactionSyncApi()
        val engine = TransactionSyncEngine(syncApi, RunnerFakeTransactionApi(), RunnerFakeTransactionDao(),
            RunnerFakeSyncCursorStore())
        val runner = TransactionSyncRunner(engine)

        val result = runner.run()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `run returns retry when the pull fails`() = runTest {
        val syncApi = RunnerFakeTransactionSyncApi().apply { error = RuntimeException("network down") }
        val engine = TransactionSyncEngine(syncApi, RunnerFakeTransactionApi(), RunnerFakeTransactionDao(),
            RunnerFakeSyncCursorStore())
        val runner = TransactionSyncRunner(engine)

        val result = runner.run()

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
