package com.walletapp.android.accounts

import com.walletapp.android.transactions.BalanceResponse
import com.walletapp.android.transactions.TransactionApi
import com.walletapp.android.transactions.TransactionRepository
import com.walletapp.android.transactions.TransactionRequest
import com.walletapp.android.transactions.TransactionResponse
import com.walletapp.android.transactions.TransactionUpdateRequest
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

    override suspend fun list(accountId: String?, categoryId: String?, dateFrom: String?, dateTo: String?): List<TransactionResponse> = emptyList()
    override suspend fun create(request: TransactionRequest): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun get(id: String): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun update(id: String, request: TransactionUpdateRequest): TransactionResponse = throw UnsupportedOperationException()
    override suspend fun delete(id: String) = throw UnsupportedOperationException()

    override suspend fun getBalance(accountId: String): BalanceResponse {
        errorsByAccount[accountId]?.let { throw it }
        return BalanceResponse(accountId, balancesByAccount[accountId] ?: 0.0)
    }
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
        val viewModel = AccountViewModel(AccountRepository(accountApi), TransactionRepository(transactionApi))

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
        val viewModel = AccountViewModel(AccountRepository(accountApi), TransactionRepository(transactionApi))

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
        val viewModel = AccountViewModel(AccountRepository(accountApi), TransactionRepository(transactionApi))
        var result: Result<Unit>? = null

        viewModel.create("Efectivo", AccountType.CASH, "USD", 0.0) { result = it }

        assertTrue(result!!.isSuccess)
        val state = viewModel.uiState.value as AccountsUiState.Success
        assertEquals(1, state.accounts.size)
    }
}
