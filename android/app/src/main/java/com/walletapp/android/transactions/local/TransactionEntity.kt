package com.walletapp.android.transactions.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.walletapp.android.categories.CategoryType

// Estado de sincronización de una fila local (feature 007, research.md #5): SYNCED = igual que el
// backend; las demás indican un cambio local todavía no confirmado por el servidor.
enum class SyncState { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE }

// Espejo local (Room) de TransactionResponse + metadatos propios de sincronización. Es la fuente de
// datos de la UI (feature 007) — Room persiste los enums como su nombre (String) automáticamente.
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: CategoryType,
    val amount: Double,
    val date: String,
    val description: String?,
    val accountId: String,
    val categoryId: String?,
    val updatedAt: String,
    val syncState: SyncState
)
