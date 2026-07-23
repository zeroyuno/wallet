package com.walletapp.android.transactions.local

// Persiste el último `nextSince` recibido con éxito del feed de sincronización (feature 007,
// data-model.md). Interfaz aparte de su implementación (SharedPreferencesSyncCursorStore) para poder
// testear TransactionSyncEngine/TransactionViewModel sin depender de un Context de Android.
interface SyncCursorStore {
    fun getCursor(): String?
    fun saveCursor(cursor: String)
    fun clear()
}
