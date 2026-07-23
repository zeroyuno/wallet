package com.walletapp.android.di

import android.content.Context
import androidx.work.WorkManager
import com.walletapp.android.transactions.sync.TransactionSyncScheduler
import com.walletapp.android.transactions.sync.WorkManagerTransactionSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideTransactionSyncScheduler(workManager: WorkManager): TransactionSyncScheduler =
        WorkManagerTransactionSyncScheduler(workManager)
}
