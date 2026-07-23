package com.walletapp.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.walletapp.android.transactions.sync.TransactionSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WalletApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var transactionSyncScheduler: TransactionSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Feature 007, US3: sync periódico en segundo plano + un disparo inmediato al abrir la app.
        transactionSyncScheduler.schedulePeriodic()
        transactionSyncScheduler.triggerImmediate()
    }
}
