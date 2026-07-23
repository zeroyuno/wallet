package com.walletapp.android.di

import android.content.Context
import androidx.room.Room
import com.walletapp.android.db.AppDatabase
import com.walletapp.android.transactions.local.SharedPreferencesSyncCursorStore
import com.walletapp.android.transactions.local.SyncCursorStore
import com.walletapp.android.transactions.local.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wallet.db").build()

    @Provides
    @Singleton
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    @Singleton
    fun provideSyncCursorStore(@ApplicationContext context: Context): SyncCursorStore =
        SharedPreferencesSyncCursorStore(context)
}
