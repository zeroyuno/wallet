package com.walletapp.android.accounts

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(private val accountApi: AccountApi) {

    suspend fun list(): Result<List<AccountResponse>> = runCatching { accountApi.list() }

    suspend fun create(name: String, type: AccountType, currency: String, initialBalance: Double): Result<AccountResponse> =
        runCatching { accountApi.create(AccountRequest(name, type, currency, initialBalance)) }

    suspend fun update(
        id: String,
        name: String,
        type: AccountType,
        currency: String,
        initialBalance: Double
    ): Result<AccountResponse> =
        runCatching { accountApi.update(id, AccountRequest(name, type, currency, initialBalance)) }

    suspend fun delete(id: String): Result<Unit> = runCatching { accountApi.delete(id) }
}
