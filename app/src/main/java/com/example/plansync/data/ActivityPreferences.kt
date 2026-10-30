package com.example.plansync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.activityPreferencesStore by preferencesDataStore(name = "activity_preferences")

class ActivityPreferences(private val context: Context) {

    private val store get() = context.activityPreferencesStore

    private fun lastUpdatedKey(uid: String) = longPreferencesKey("last_updated_$uid")

    suspend fun getLastUpdated(uid: String): Long? = store.data.first()[lastUpdatedKey(uid)]

    suspend fun setLastUpdated(uid: String, millis: Long) {
        store.edit { it[lastUpdatedKey(uid)] = millis }
    }
}
