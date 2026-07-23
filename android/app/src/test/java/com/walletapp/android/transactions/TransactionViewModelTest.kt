package com.walletapp.android.transactions

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.testing.asSnapshot
import com.walletapp.android.categories.CategoryType
import com.walletapp.android.transactions.local.SyncCursorStore
import com.walletapp.android.transactions.local.SyncState
import com.walletapp.android.transactions.local.TransactionDao
import com.walletapp.android.transactions.local.TransactionEntity
import com.walletapp.android.transactions.sync.TransactionSyncApi
import com.walletapp.android.transactions.sync.TransactionSyncEngine
import com.walletapp.android.transactions.sync.TransactionSyncItemResponse
import com.walletapp.android.transactions.sync.TransactionSyncResponse
import com.walletapp.android.transactions.sync.TransactionSyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTransactionApi : TransactionApi {
    var createError: Throwable? = null
    var updateError: Throwable? = null
    var deleteError: Throwable? = null
    var balance: Double = 0.0
    val createdRequests = mutableListOf<TransactionRequest>()
    private var nextId = 1

    override suspend fun create(request: TransactionRequest): TransactionResponse {
        createError?.let { throw it }
        createdRequests.add(request)
        return TransactionResponse(
            id = request.id ?: "generated-${nextId++}",
            type = request.type,
            amount = request.amount,
            date = request.date,
            description = request.description,
            accountId = request.accountId,
            categoryId = request.categoryId
        )
    }

    override suspend fun get(id: String): TransactionResponse = throw UnsupportedOperationException()

    override suspend fun update(id: String, request: TransactionUpdateRequest): TransactionResponse {
        updateError?.let { throw it }
        return TransactionResponse(id, CategoryType.EXPENSE, request.amount, request.date, request.description,
            "acc1", request.categoryId)
    }

    override suspend fun delete(id: String) {
        deleteError?.let { throw it }
    }

    override suspend fun getBalance(accountId: String): BalanceResponse = BalanceResponse(accountId, balance)
}

private class FakeTransactionSyncApi : TransactionSyncApi {
    var response = TransactionSyncResponse(emptyList(), emptyList(), "cursor-0", false)
    var error: Throwable? = null

    override suspend fun sync(since: String?, limit: Int?): TransactionSyncResponse {
        error?.let { throw it }
        return response
    }
}

private class FakeSyncCursorStore : SyncCursorStore {
    private var cursor: String? = null
    override fun getCursor(): String? = cursor
    override fun saveCursor(cursor: String) {
        this.cursor = cursor
    }
    override fun clear() {
        cursor = null
    }
}

private class FakeTransactionSyncScheduler : TransactionSyncScheduler {
    var periodicScheduled = false
    var immediateTriggerCount = 0
    override fun schedulePeriodic() {
        periodicScheduled = true
    }
    override fun triggerImmediate() {
        immediateTriggerCount++
    }
}

// Fake en memoria del DAO de Room: implementa un PagingSource real (sin paginar de verdad, una sola
// página con todo lo que matchea el filtro) para poder usar asSnapshot() sobre el Flow del ViewModel.
private class FakeTransactionDao : TransactionDao {
    val entities = mutableListOf<TransactionEntity>()

    override fun pagingSource(accountId: String?, categoryId: String?, dateFrom: String?, dateTo: String?):
            PagingSource<Int, TransactionEntity> = object : PagingSource<Int, TransactionEntity>() {
        override fun getRefreshKey(state: PagingState<Int, TransactionEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionEntity> {
            val filtered = entities.filter { e ->
                e.syncState != SyncState.PENDING_DELETE &&
                    (accountId == null || e.accountId == accountId) &&
                    (categoryId == null || e.categoryId == categoryId) &&
                    (dateFrom == null || e.date >= dateFrom) &&
                    (dateTo == null || e.date <= dateTo)
            }.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
            return LoadResult.Page(data = filtered, prevKey = null, nextKey = null)
        }
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

    override suspend fun getPending(): List<TransactionEntity> =
        entities.filter { it.syncState != SyncState.SYNCED }

    override suspend fun updateSyncState(id: String, state: SyncState) {
        val index = entities.indexOfFirst { it.id == id }
        if (index >= 0) entities[index] = entities[index].copy(syncState = state)
    }
}

private fun syncItem(id: String, accountId: String = "acc1", amount: Double = 50.0, date: String = "2026-07-18") =
    TransactionSyncItemResponse(id, CategoryType.EXPENSE, amount, date, null, accountId, null, "2026-07-18T00:00:00Z")

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(
        api: FakeTransactionApi = FakeTransactionApi(),
        syncApi: FakeTransactionSyncApi = FakeTransactionSyncApi(),
        dao: FakeTransactionDao = FakeTransactionDao(),
        cursorStore: FakeSyncCursorStore = FakeSyncCursorStore(),
        scheduler: FakeTransactionSyncScheduler = FakeTransactionSyncScheduler()
    ): TransactionViewModel {
        val engine = TransactionSyncEngine(syncApi, api, dao, cursorStore)
        return TransactionViewModel(TransactionRepository(api, dao), engine, scheduler)
    }

    @Test
    fun `initial sync populates the paged list`() = runTest {
        val syncApi = FakeTransactionSyncApi().apply {
            response = TransactionSyncResponse(listOf(syncItem("t1")), emptyList(), "cursor-1", false)
        }
        val viewModel = newViewModel(syncApi = syncApi)

        val snapshot = viewModel.transactions.asSnapshot()

        assertEquals(1, snapshot.size)
        assertEquals("t1", snapshot.first().id)
        assertEquals(SyncUiState.Idle, viewModel.syncState.value)
    }

    @Test
    fun `sync failure sets Error state`() = runTest {
        val syncApi = FakeTransactionSyncApi().apply { error = RuntimeException("network down") }
        val viewModel = newViewModel(syncApi = syncApi)

        assertTrue(viewModel.syncState.value is SyncUiState.Error)
    }

    // US2: la creación se refleja al instante en local, sin esperar al backend.
    @Test
    fun `create reflects immediately in the paged list`() = runTest {
        val viewModel = newViewModel()
        var result: Result<Unit>? = null

        viewModel.create(CategoryType.EXPENSE, 30.0, "2026-07-18", null, "acc1", null) { result = it }

        assertTrue(result!!.isSuccess)
        val snapshot = viewModel.transactions.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals(30.0, snapshot.first().amount, 0.0)
    }

    // US2: la escritura local nunca depende de la red — queda PENDING_CREATE hasta que el worker de
    // sincronización (US3) la envíe en segundo plano; acá solo nos importa que se encoló ese trabajo.
    @Test
    fun `create reports success and leaves the transaction pending until the worker syncs it`() = runTest {
        val dao = FakeTransactionDao()
        val viewModel = newViewModel(dao = dao)
        var result: Result<Unit>? = null

        viewModel.create(CategoryType.EXPENSE, 30.0, "2026-07-18", null, "acc1", null) { result = it }

        assertTrue(result!!.isSuccess)
        assertEquals(SyncState.PENDING_CREATE, dao.entities.single().syncState)
    }

    // US3: cada escritura local dispara un trabajo inmediato en WorkManager en vez de sincronizar
    // directo — así sobrevive al cierre de la app y reintenta con backoff si falla.
    @Test
    fun `create triggers an immediate sync work request`() = runTest {
        val scheduler = FakeTransactionSyncScheduler()
        val viewModel = newViewModel(scheduler = scheduler)
        var result: Result<Unit>? = null

        viewModel.create(CategoryType.EXPENSE, 30.0, "2026-07-18", null, "acc1", null) { result = it }

        assertTrue(result!!.isSuccess)
        assertEquals(1, scheduler.immediateTriggerCount)
    }

    @Test
    fun `update reflects immediately and reports success`() = runTest {
        val dao = FakeTransactionDao().apply {
            entities.add(TransactionEntity("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null,
                "2026-07-18T00:00:00Z", SyncState.SYNCED))
        }
        val viewModel = newViewModel(dao = dao)
        var result: Result<Unit>? = null

        viewModel.update("t1", 80.0, "2026-07-18", null, null) { result = it }

        assertTrue(result!!.isSuccess)
        assertEquals(80.0, dao.entities.single().amount, 0.0)
        assertEquals(SyncState.PENDING_UPDATE, dao.entities.single().syncState)
    }

    @Test
    fun `delete removes the transaction from the paged list immediately`() = runTest {
        val dao = FakeTransactionDao().apply {
            entities.add(TransactionEntity("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null,
                "2026-07-18T00:00:00Z", SyncState.SYNCED))
        }
        val viewModel = newViewModel(dao = dao)
        var result: Result<Unit>? = null

        viewModel.delete("t1") { result = it }

        assertTrue(result!!.isSuccess)
        val snapshot = viewModel.transactions.asSnapshot()
        assertTrue(snapshot.isEmpty())
    }

    // Edge case de spec.md: borrar algo que todavía no se envió como creación no debe dejar nada
    // pendiente — nunca llegó a existir en el backend.
    @Test
    fun `deleting a not-yet-synced creation removes it without leaving anything pending`() = runTest {
        val dao = FakeTransactionDao()
        val viewModel = newViewModel(dao = dao)
        var createResult: Result<Unit>? = null
        viewModel.create(CategoryType.EXPENSE, 30.0, "2026-07-18", null, "acc1", null) { createResult = it }
        assertTrue(createResult!!.isSuccess)
        val createdId = dao.entities.single().id

        var deleteResult: Result<Unit>? = null
        viewModel.delete(createdId) { deleteResult = it }

        assertTrue(deleteResult!!.isSuccess)
        assertTrue(dao.entities.isEmpty())
    }

    @Test
    fun `applyFilter scopes the paged list to the selected account`() = runTest {
        val dao = FakeTransactionDao().apply {
            entities.add(TransactionEntity("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null,
                "2026-07-18T00:00:00Z", SyncState.SYNCED))
            entities.add(TransactionEntity("t2", CategoryType.INCOME, 20.0, "2026-07-18", null, "acc2", null,
                "2026-07-18T00:00:00Z", SyncState.SYNCED))
        }
        val viewModel = newViewModel(dao = dao)

        viewModel.applyFilter(TransactionFilter(accountId = "acc1"))

        val snapshot = viewModel.transactions.asSnapshot()
        assertEquals(1, snapshot.size)
        assertEquals("acc1", snapshot.first().accountId)
        assertEquals("acc1", viewModel.filter.value.accountId)
    }
}
