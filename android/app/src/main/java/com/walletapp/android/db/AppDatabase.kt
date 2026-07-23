package com.walletapp.android.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.walletapp.android.transactions.local.TransactionDao
import com.walletapp.android.transactions.local.TransactionEntity

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
