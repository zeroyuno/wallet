package com.walletapp.android.transactions

import com.walletapp.android.categories.CategoryType
import javax.inject.Inject
import javax.inject.Singleton

data class TransactionFilter(
    val accountId: String? = null,
    val categoryId: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null
)

@Singleton
class TransactionRepository @Inject constructor(private val transactionApi: TransactionApi) {

    suspend fun list(filter: TransactionFilter = TransactionFilter()): Result<List<TransactionResponse>> =
        runCatching {
            transactionApi.list(
                accountId = filter.accountId,
                categoryId = filter.categoryId,
                dateFrom = filter.dateFrom,
                dateTo = filter.dateTo
            )
        }

    suspend fun create(
        type: CategoryType,
        amount: Double,
        date: String,
        description: String?,
        accountId: String,
        categoryId: String?
    ): Result<TransactionResponse> =
        runCatching {
            transactionApi.create(
                TransactionRequest(
                    type = type,
                    amount = amount,
                    date = date,
                    description = description,
                    accountId = accountId,
                    categoryId = categoryId
                )
            )
        }

    suspend fun update(
        id: String,
        amount: Double,
        date: String,
        description: String?,
        categoryId: String?
    ): Result<TransactionResponse> =
        runCatching {
            transactionApi.update(id, TransactionUpdateRequest(amount, date, description, categoryId))
        }

    suspend fun delete(id: String): Result<Unit> = runCatching { transactionApi.delete(id) }

    suspend fun getBalance(accountId: String): Result<BalanceResponse> =
        runCatching { transactionApi.getBalance(accountId) }
}
