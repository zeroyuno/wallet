package com.walletapp.android.transactions.sync

// Interfaz aparte de su implementación (WorkManagerTransactionSyncScheduler) para poder testear
// TransactionViewModel sin depender de WorkManager/Context (feature 007, US3).
interface TransactionSyncScheduler {
    // Sincronización periódica en segundo plano, agendada una vez al iniciar la app.
    fun schedulePeriodic()

    // Disparo inmediato (research.md #6/#7) — al abrir la app y tras cada escritura local pendiente.
    fun triggerImmediate()
}
