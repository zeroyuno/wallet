package com.walletapp.android.transactions

import com.walletapp.android.categories.CategoryType
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
    val transactions = mutableListOf<TransactionResponse>()
    var listError: Throwable? = null
    var createError: Throwable? = null
    var updateError: Throwable? = null
    var deleteError: Throwable? = null
    var balance: Double = 0.0
    private var nextId = 1

    override suspend fun list(accountId: String?, categoryId: String?, dateFrom: String?, dateTo: String?): List<TransactionResponse> {
        listError?.let { throw it }
        return transactions.filter { t ->
            (accountId == null || t.accountId == accountId) &&
                (categoryId == null || t.categoryId == categoryId) &&
                (dateFrom == null || t.date >= dateFrom) &&
                (dateTo == null || t.date <= dateTo)
        }
    }

    override suspend fun create(request: TransactionRequest): TransactionResponse {
        createError?.let { throw it }
        val response = TransactionResponse(
            id = request.id ?: "generated-${nextId++}",
            type = request.type,
            amount = request.amount,
            date = request.date,
            description = request.description,
            accountId = request.accountId,
            categoryId = request.categoryId
        )
        transactions.add(response)
        return response
    }

    override suspend fun get(id: String): TransactionResponse = transactions.first { it.id == id }

    override suspend fun update(id: String, request: TransactionUpdateRequest): TransactionResponse {
        updateError?.let { throw it }
        val index = transactions.indexOfFirst { it.id == id }
        val updated = transactions[index].copy(
            amount = request.amount,
            date = request.date,
            description = request.description,
            categoryId = request.categoryId
        )
        transactions[index] = updated
        return updated
    }

    override suspend fun delete(id: String) {
        deleteError?.let { throw it }
        transactions.removeAll { it.id == id }
    }

    override suspend fun getBalance(accountId: String): BalanceResponse {
        return BalanceResponse(accountId, balance)
    }
}

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

    private fun newViewModel(api: FakeTransactionApi): TransactionViewModel =
        TransactionViewModel(TransactionRepository(api))

    @Test
    fun `refresh loads transactions into Success state`() = runTest {
        val api = FakeTransactionApi().apply {
            transactions.add(TransactionResponse("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null))
        }
        val viewModel = newViewModel(api)

        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertEquals(1, state.transactions.size)
    }

    @Test
    fun `refresh failure sets Error state`() = runTest {
        val api = FakeTransactionApi().apply { listError = RuntimeException("network down") }
        val viewModel = newViewModel(api)

        assertTrue(viewModel.uiState.value is TransactionsUiState.Error)
    }

    @Test
    fun `create success adds transaction and reports success`() = runTest {
        val api = FakeTransactionApi()
        val viewModel = newViewModel(api)
        var result: Result<Unit>? = null

        viewModel.create(CategoryType.EXPENSE, 30.0, "2026-07-18", null, "acc1", null) { result = it }

        assertTrue(result!!.isSuccess)
        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertEquals(1, state.transactions.size)
    }

    @Test
    fun `create failure reports failure without adding transaction`() = runTest {
        val api = FakeTransactionApi().apply { createError = RuntimeException("400") }
        val viewModel = newViewModel(api)
        var result: Result<Unit>? = null

        viewModel.create(CategoryType.EXPENSE, 30.0, "2026-07-18", null, "acc1", null) { result = it }

        assertTrue(result!!.isFailure)
        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertTrue(state.transactions.isEmpty())
    }

    @Test
    fun `update success changes amount`() = runTest {
        val api = FakeTransactionApi().apply {
            transactions.add(TransactionResponse("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null))
        }
        val viewModel = newViewModel(api)
        var result: Result<Unit>? = null

        viewModel.update("t1", 80.0, "2026-07-18", null, null) { result = it }

        assertTrue(result!!.isSuccess)
        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertEquals(80.0, state.transactions.first().amount, 0.0)
    }

    @Test
    fun `delete success removes transaction`() = runTest {
        val api = FakeTransactionApi().apply {
            transactions.add(TransactionResponse("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null))
        }
        val viewModel = newViewModel(api)
        var result: Result<Unit>? = null

        viewModel.delete("t1") { result = it }

        assertTrue(result!!.isSuccess)
        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertTrue(state.transactions.isEmpty())
    }

    @Test
    fun `delete failure keeps transaction`() = runTest {
        val api = FakeTransactionApi().apply {
            transactions.add(TransactionResponse("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null))
            deleteError = RuntimeException("network down")
        }
        val viewModel = newViewModel(api)
        var result: Result<Unit>? = null

        viewModel.delete("t1") { result = it }

        assertTrue(result!!.isFailure)
        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertEquals(1, state.transactions.size)
    }

    @Test
    fun `applyFilter reloads list scoped to the selected account`() = runTest {
        val api = FakeTransactionApi().apply {
            transactions.add(TransactionResponse("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null))
            transactions.add(TransactionResponse("t2", CategoryType.INCOME, 20.0, "2026-07-18", null, "acc2", null))
        }
        val viewModel = newViewModel(api)

        viewModel.applyFilter(TransactionFilter(accountId = "acc1"))

        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertEquals(1, state.transactions.size)
        assertEquals("acc1", state.transactions.first().accountId)
        assertEquals("acc1", state.filter.accountId)
    }

    @Test
    fun `applyFilter with null accountId clears the filter`() = runTest {
        val api = FakeTransactionApi().apply {
            transactions.add(TransactionResponse("t1", CategoryType.EXPENSE, 50.0, "2026-07-18", null, "acc1", null))
            transactions.add(TransactionResponse("t2", CategoryType.INCOME, 20.0, "2026-07-18", null, "acc2", null))
        }
        val viewModel = newViewModel(api)

        viewModel.applyFilter(TransactionFilter(accountId = "acc1"))
        viewModel.applyFilter(TransactionFilter())

        val state = viewModel.uiState.value as TransactionsUiState.Success
        assertEquals(2, state.transactions.size)
    }
}
