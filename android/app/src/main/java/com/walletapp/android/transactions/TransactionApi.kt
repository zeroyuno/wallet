package com.walletapp.android.transactions

import com.walletapp.android.categories.CategoryType
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class TransactionRequest(
    val id: String? = null,
    val type: CategoryType,
    val amount: Double,
    val date: String,
    val description: String? = null,
    val accountId: String,
    val categoryId: String? = null
)

@Serializable
data class TransactionUpdateRequest(
    val amount: Double,
    val date: String,
    val description: String? = null,
    val categoryId: String? = null
)

@Serializable
data class TransactionResponse(
    val id: String,
    val type: CategoryType,
    val amount: Double,
    val date: String,
    val description: String? = null,
    val accountId: String,
    val categoryId: String? = null
)

@Serializable
data class BalanceResponse(val accountId: String, val balance: Double)

interface TransactionApi {

    @POST("api/transactions")
    suspend fun create(@Body request: TransactionRequest): TransactionResponse

    @GET("api/transactions/{id}")
    suspend fun get(@Path("id") id: String): TransactionResponse

    @PUT("api/transactions/{id}")
    suspend fun update(@Path("id") id: String, @Body request: TransactionUpdateRequest): TransactionResponse

    @DELETE("api/transactions/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("api/accounts/{id}/balance")
    suspend fun getBalance(@Path("id") accountId: String): BalanceResponse
}
