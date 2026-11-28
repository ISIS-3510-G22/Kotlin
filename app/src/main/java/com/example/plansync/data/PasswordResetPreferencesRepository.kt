package com.example.plansync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.passwordResetPrefsStore by preferencesDataStore(name = "password_reset_prefs")

class PasswordResetPreferencesRepository(private val context: Context) {

    private val key = longPreferencesKey("last_reset_sent_at")

    suspend fun getLastSentAt(): Result<Long?> {
        return runCatching { context.passwordResetPrefsStore.data.first()[key] }
    }

    suspend fun saveLastSentAt(time: Long): Result<Unit> {
        return runCatching {
            context.passwordResetPrefsStore.edit { it[key] = time }
            Unit
        }
    }
}
