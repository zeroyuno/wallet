package com.walletapp.android.accounts

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.walletapp.android.transactions.BalanceResponse
import com.walletapp.android.transactions.TransactionApi
import com.walletapp.android.transactions.TransactionRepository
import com.walletapp.android.transactions.TransactionRequest
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.TransactionUpdateRequest
import com.walletapp.android.transactions.local.SyncState
import com.walletapp.android.transactions.local.TransactionDao
import com.walletapp.android.transactions.local.TransactionEntity
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

private class FakeAccountApi : AccountApi {
    val accounts = mutableListOf<AccountResponse>()

    override suspend fun list(): List<AccountResponse> = accounts

    override suspend fun create(request: AccountRequest): AccountResponse {
        val response = AccountResponse("generated", request.name, request.type, request.currency, request.initialBalance)
        accounts.add(response)
        return response
    }

    override suspend fun update(id: String, request: AccountRequest): AccountResponse {
        val response = AccountResponse(id, request.name, request.type, request.currency, request.initialBalance)
        accounts[accounts.indexOfFirst { it.id == id }] = response
        return response
    }

    override suspend fun delete(id: String) {
        accounts.removeAll { it.id == id }
    }
}

private class FakeTransactionApi : TransactionApi {
    val balancesByAccount = mutableMapOf<String, Double>()
    val errorsByAccount = mutableMapOf<String, Throwable>()

    override suspend fun create(request: TransactionRequest): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun get(id: String): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun update(id: String, request: TransactionUpdateRequest): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun delete(id: String) = throw UnsupportedOperationException()

    override suspend fun getBalance(accountId: String): BalanceResponse {
        errorsByAccount[accountId]?.let { throw it }
        return BalanceResponse(accountId, balancesByAccount[accountId] ?: 0.0)
    }
}

// No se usa en estos tests (AccountViewModel solo llama a getBalance) — implementación mínima para
// satisfacer el constructor de TransactionRepository.
private class NoOpTransactionDao : TransactionDao {
    override fun pagingSource(accountId: String?, categoryId: String?, dateFrom: String?, dateTo: String?):
            PagingSource<Int, TransactionEntity> = object : PagingSource<Int, TransactionEntity>() {
        override fun getRefreshKey(state: PagingState<Int, TransactionEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransactionEntity> =
            LoadResult.Page(emptyList(), null, null)
    }

    override suspend fun getById(id: String): TransactionEntity? = null
    override suspend fun upsert(entity: TransactionEntity) = Unit
    override suspend fun upsertAll(entities: List<TransactionEntity>) = Unit
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteByIds(ids: List<String>) = Unit
    override suspend fun getPending(): List<TransactionEntity> = emptyList()
    override suspend fun updateSyncState(id: String, state: SyncState) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh resolves the balance for each account`() = runTest {
        val accountApi = FakeAccountApi().apply {
            accounts.add(AccountResponse("acc1", "Efectivo", AccountType.CASH, "USD", 100.0))
            accounts.add(AccountResponse("acc2", "Banco", AccountType.BANK, "USD", 200.0))
        }
        val transactionApi = FakeTransactionApi().apply {
            balancesByAccount["acc1"] = 50.0
            balancesByAccount["acc2"] = 250.0
        }
        val viewModel = AccountViewModel(AccountRepository(accountApi), TransactionRepository(transactionApi, NoOpTransactionDao()))

        val state = viewModel.uiState.value as AccountsUiState.Success
        val byId = state.accounts.associateBy { it.account.id }
        assertEquals(50.0, byId.getValue("acc1").balance, 0.0)
        assertEquals(250.0, byId.getValue("acc2").balance, 0.0)
    }

    @Test
    fun `error resolving one account balance falls back to initialBalance without breaking the list`() = runTest {
        val accountApi = FakeAccountApi().apply {
            accounts.add(AccountResponse("acc1", "Efectivo", AccountType.CASH, "USD", 100.0))
            accounts.add(AccountResponse("acc2", "Banco", AccountType.BANK, "USD", 200.0))
        }
        val transactionApi = FakeTransactionApi().apply {
            balancesByAccount["acc2"] = 250.0
            errorsByAccount["acc1"] = RuntimeException("network down")
        }
        val viewModel = AccountViewModel(AccountRepository(accountApi), TransactionRepository(transactionApi, NoOpTransactionDao()))

        val state = viewModel.uiState.value as AccountsUiState.Success
        assertEquals(2, state.accounts.size)
        val byId = state.accounts.associateBy { it.account.id }
        assertEquals(100.0, byId.getValue("acc1").balance, 0.0)
        assertEquals(250.0, byId.getValue("acc2").balance, 0.0)
    }

    @Test
    fun `create success refreshes the list`() = runTest {
        val accountApi = FakeAccountApi()
        val transactionApi = FakeTransactionApi()
        val viewModel = AccountViewModel(AccountRepository(accountApi), TransactionRepository(transactionApi, NoOpTransactionDao()))
        var result: Result<Unit>? = null

        viewModel.create("Efectivo", AccountType.CASH, "USD", 0.0) { result = it }

        assertTrue(result!!.isSuccess)
        val state = viewModel.uiState.value as AccountsUiState.Success
        assertEquals(1, state.accounts.size)
    }
}
