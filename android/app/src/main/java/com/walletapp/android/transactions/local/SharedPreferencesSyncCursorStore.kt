package com.walletapp.android.transactions.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// No es información sensible (a diferencia de TokenStore) — SharedPreferences plano, no encriptado.
@Singleton
class SharedPreferencesSyncCursorStore @Inject constructor(
    @ApplicationContext context: Context
) : SyncCursorStore {

    private val preferences = context.getSharedPreferences("wallet_sync_prefs", Context.MODE_PRIVATE)

    override fun getCursor(): String? = preferences.getString(KEY_CURSOR, null)

    override fun saveCursor(cursor: String) {
        preferences.edit { putString(KEY_CURSOR, cursor) }
    }

    override fun clear() {
        preferences.edit { remove(KEY_CURSOR) }
    }

    private companion object {
        const val KEY_CURSOR = "last_since"
    }
}
