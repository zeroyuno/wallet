package com.walletapp.android.transactions.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

// Envoltorio de WorkManager (feature 007, US3): sobrevive al cierre de la app y reintenta con backoff
// propio de WorkManager ante un Result.retry() — la lógica real vive en TransactionSyncRunner,
// testeada aparte sin depender de Context/WorkerParameters.
@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: TransactionSyncRunner
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runner.run()
}
