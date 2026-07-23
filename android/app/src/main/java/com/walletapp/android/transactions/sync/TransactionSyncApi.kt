package com.walletapp.android.transactions.sync

import com.walletapp.android.categories.CategoryType
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

// El cursor (`since`/`nextSince`) es un string opaco para el cliente (research.md #1 de la feature
// 007) — solo se guarda y se reenvía tal cual, nunca se parsea ni se interpreta en Android.
@Serializable
data class TransactionSyncItemResponse(
    val id: String,
    val type: CategoryType,
    val amount: Double,
    val date: String,
    val description: String? = null,
    val accountId: String,
    val categoryId: String? = null,
    val updatedAt: String
)

@Serializable
data class TransactionSyncResponse(
    val upserts: List<TransactionSyncItemResponse>,
    val deletedIds: List<String>,
    val nextSince: String,
    val hasMore: Boolean,
    // Total de cambios pendientes desde el `since` de este pedido (no solo esta página) — solo para
    // mostrar progreso ("258/1000"), research.md #8 de la feature 007.
    val totalRemaining: Long
)

interface TransactionSyncApi {

    @GET("api/transactions/sync")
    suspend fun sync(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int? = null
    ): TransactionSyncResponse
}
