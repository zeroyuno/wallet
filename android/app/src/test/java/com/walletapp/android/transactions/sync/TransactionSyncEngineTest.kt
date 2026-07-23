package com.walletapp.android.transactions.sync

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.walletapp.android.categories.CategoryType
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private class FakeTransactionSyncApi : TransactionSyncApi {
    val responses = ArrayDeque<TransactionSyncResponse>()
    var error: Throwable? = null
    val requestedSinceValues = mutableListOf<String?>()

    override suspend fun sync(since: String?, limit: Int?): TransactionSyncResponse {
        requestedSinceValues.add(since)
        error?.let { throw it }
        return responses.removeFirst()
    }
}

private class FakeTransactionApi : TransactionApi {
    var createError: Throwable? = null
    var updateError: Throwable? = null
    var deleteError: Throwable? = null
    val deletedIds = mutableListOf<String>()

    override suspend fun create(request: TransactionRequest): TransactionResponse {
        createError?.let { throw it }
        return TransactionResponse(request.id ?: "generated", request.type, request.amount, request.date,
            request.description, request.accountId, request.categoryId)
    }

    override suspend fun get(id: String): TransactionResponse = throw UnsupportedOperationException()

    override suspend fun update(id: String, request: TransactionUpdateRequest): TransactionResponse {
        updateError?.let { throw it }
        return TransactionResponse(id, CategoryType.EXPENSE, request.amount, request.date, request.description,
            "acc1", request.categoryId)
    }

    override suspend fun delete(id: String) {
        deleteError?.let { throw it }
        deletedIds.add(id)
    }

    override suspend fun getBalance(accountId: String): BalanceResponse = BalanceResponse(accountId, 0.0)
}

private class FakeSyncCursorStore : SyncCursorStore {
    private var cursor: String? = null
    var saveCount = 0

    override fun getCursor(): String? = cursor
    override fun saveCursor(cursor: String) {
        this.cursor = cursor
        saveCount++
    }
    override fun clear() {
        cursor = null
    }
}

private class FakeTransactionDao : TransactionDao {
    val entities = mutableListOf<TransactionEntity>()

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

private fun syncItem(id: String) =
    TransactionSyncItemResponse(id, CategoryType.EXPENSE, 10.0, "2026-07-18", null, "acc1", null,
        "2026-07-18T00:00:00Z")

private fun pendingEntity(id: String, state: SyncState) =
    TransactionEntity(id, CategoryType.EXPENSE, 10.0, "2026-07-18", null, "acc1", null,
        "2026-07-18T00:00:00Z", state)

private fun httpException(code: Int): HttpException {
    val body = "{}".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(code, body))
}

class TransactionSyncEngineTest {

    @Test
    fun `pull applies upserts and deletes across multiple pages until hasMore is false`() = runTest {
        val syncApi = FakeTransactionSyncApi().apply {
            responses.add(TransactionSyncResponse(listOf(syncItem("t1")), emptyList(), "cursor-1", true))
            responses.add(TransactionSyncResponse(listOf(syncItem("t2")), listOf("t1"), "cursor-2", false))
        }
        val dao = FakeTransactionDao()
        val cursorStore = FakeSyncCursorStore()
        val engine = TransactionSyncEngine(syncApi, FakeTransactionApi(), dao, cursorStore)

        engine.pull()

        // t1 se creó en la primera página y se borró en la segunda; t2 sigue.
        assertEquals(listOf("t2"), dao.entities.map { it.id })
        assertEquals("cursor-2", cursorStore.getCursor())
        assertEquals(listOf(null, "cursor-1"), syncApi.requestedSinceValues)
    }

    @Test
    fun `pull preserves the previous cursor when a page fails midway`() = runTest {
        val syncApi = FakeTransactionSyncApi().apply {
            responses.add(TransactionSyncResponse(listOf(syncItem("t1")), emptyList(), "cursor-1", false))
        }
        val dao = FakeTransactionDao()
        val cursorStore = FakeSyncCursorStore()
        val engine = TransactionSyncEngine(syncApi, FakeTransactionApi(), dao, cursorStore)
        engine.pull()
        assertEquals("cursor-1", cursorStore.getCursor())
        assertEquals(1, cursorStore.saveCount)

        syncApi.error = RuntimeException("network down")
        assertTrue(runCatching { engine.pull() }.isFailure)

        assertEquals("cursor-1", cursorStore.getCursor())
        assertEquals(1, cursorStore.saveCount)
    }

    @Test
    fun `pull with nothing to sync leaves the cursor untouched`() = runTest {
        val syncApi = FakeTransactionSyncApi().apply {
            responses.add(TransactionSyncResponse(emptyList(), emptyList(), "cursor-0", false))
        }
        val dao = FakeTransactionDao()
        val cursorStore = FakeSyncCursorStore()
        val engine = TransactionSyncEngine(syncApi, FakeTransactionApi(), dao, cursorStore)

        engine.pull()

        assertTrue(dao.entities.isEmpty())
        assertEquals("cursor-0", cursorStore.getCursor())
        assertNull(syncApi.requestedSinceValues.first())
    }

    @Test
    fun `push marks a pending create as synced once it succeeds`() = runTest {
        val dao = FakeTransactionDao().apply { entities.add(pendingEntity("t1", SyncState.PENDING_CREATE)) }
        val api = FakeTransactionApi()
        val engine = TransactionSyncEngine(FakeTransactionSyncApi(), api, dao, FakeSyncCursorStore())

        engine.push()

        assertEquals(SyncState.SYNCED, dao.entities.single().syncState)
    }

    // research.md #3: un 409 sobre una creación pendiente significa que ya se sincronizó antes.
    @Test
    fun `push treats a 409 on a pending create as already synced`() = runTest {
        val dao = FakeTransactionDao().apply { entities.add(pendingEntity("t1", SyncState.PENDING_CREATE)) }
        val api = FakeTransactionApi().apply { createError = httpException(409) }
        val engine = TransactionSyncEngine(FakeTransactionSyncApi(), api, dao, FakeSyncCursorStore())

        engine.push()

        assertEquals(SyncState.SYNCED, dao.entities.single().syncState)
    }

    @Test
    fun `push leaves a pending create untouched when there is no connection`() = runTest {
        val dao = FakeTransactionDao().apply { entities.add(pendingEntity("t1", SyncState.PENDING_CREATE)) }
        val api = FakeTransactionApi().apply { createError = IOException("no network") }
        val engine = TransactionSyncEngine(FakeTransactionSyncApi(), api, dao, FakeSyncCursorStore())

        engine.push()

        assertEquals(SyncState.PENDING_CREATE, dao.entities.single().syncState)
    }

    @Test
    fun `push removes a pending delete once the backend confirms it`() = runTest {
        val dao = FakeTransactionDao().apply { entities.add(pendingEntity("t1", SyncState.PENDING_DELETE)) }
        val api = FakeTransactionApi()
        val engine = TransactionSyncEngine(FakeTransactionSyncApi(), api, dao, FakeSyncCursorStore())

        engine.push()

        assertTrue(dao.entities.isEmpty())
        assertEquals(listOf("t1"), api.deletedIds)
    }

    @Test
    fun `push removes a pending delete when the backend already had it gone`() = runTest {
        val dao = FakeTransactionDao().apply { entities.add(pendingEntity("t1", SyncState.PENDING_DELETE)) }
        val api = FakeTransactionApi().apply { deleteError = httpException(404) }
        val engine = TransactionSyncEngine(FakeTransactionSyncApi(), api, dao, FakeSyncCursorStore())

        engine.push()

        assertTrue(dao.entities.isEmpty())
    }

    @Test
    fun `push marks a pending update as synced once it succeeds`() = runTest {
        val dao = FakeTransactionDao().apply { entities.add(pendingEntity("t1", SyncState.PENDING_UPDATE)) }
        val api = FakeTransactionApi()
        val engine = TransactionSyncEngine(FakeTransactionSyncApi(), api, dao, FakeSyncCursorStore())

        engine.push()

        assertEquals(SyncState.SYNCED, dao.entities.single().syncState)
    }
}
