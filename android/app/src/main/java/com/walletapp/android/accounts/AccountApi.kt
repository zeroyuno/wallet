package com.walletapp.android.accounts

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
enum class AccountType { CASH, BANK, CREDIT_CARD, OTHER }

fun AccountType.displayLabel(): String = when (this) {
    AccountType.CASH -> "Efectivo"
    AccountType.BANK -> "Banco"
    AccountType.CREDIT_CARD -> "Tarjeta de crédito"
    AccountType.OTHER -> "Otro"
}

// Sin material-icons-extended (research.md #4 de la feature 008) — un emoji por tipo de cuenta
// cumple el mismo rol visual que un ícono vectorial para esta escala de app.
fun AccountType.emoji(): String = when (this) {
    AccountType.CASH -> "💵"
    AccountType.BANK -> "🏦"
    AccountType.CREDIT_CARD -> "💳"
    AccountType.OTHER -> "📦"
}

@Serializable
data class AccountRequest(
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalance: Double
)

@Serializable
data class AccountResponse(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val initialBalance: Double
)

interface AccountApi {

    @GET("api/accounts")
    suspend fun list(): List<AccountResponse>

    @POST("api/accounts")
    suspend fun create(@Body request: AccountRequest): AccountResponse

    @PUT("api/accounts/{id}")
    suspend fun update(@Path("id") id: String, @Body request: AccountRequest): AccountResponse

    @DELETE("api/accounts/{id}")
    suspend fun delete(@Path("id") id: String)
}
