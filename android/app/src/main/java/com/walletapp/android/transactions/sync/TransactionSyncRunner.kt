package com.walletapp.android.transactions.sync

import android.util.Log
import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransactionSyncRunner"

// Lógica del worker de sincronización (feature 007, US3) separada de CoroutineWorker/Context para
// poder testearla con JUnit puro — TransactionSyncWorker es solo el envoltorio de WorkManager.
@Singleton
class TransactionSyncRunner @Inject constructor(private val transactionSyncEngine: TransactionSyncEngine) {

    suspend fun run(): Result =
        try {
            transactionSyncEngine.pull()
            transactionSyncEngine.push()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "run() -> Error, se reintenta más adelante: ${e.message}")
            Result.retry()
        }
}
