package com.example.plansync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.activityPreferencesStore by preferencesDataStore(name = "activity_preferences")

class ActivityPreferences(private val context: Context) {

    private val store get() = context.activityPreferencesStore

    private fun lastUpdatedKey(uid: String) = longPreferencesKey("last_updated_$uid")

    private fun recommendedKey(uid: String) = stringPreferencesKey("recommended_ids_$uid")

    suspend fun getLastUpdated(uid: String): Long? = store.data.first()[lastUpdatedKey(uid)]

    suspend fun setLastUpdated(uid: String, millis: Long) {
        store.edit { it[lastUpdatedKey(uid)] = millis }
    }

    suspend fun getRecommendedIds(uid: String): List<String> =
        store.data.first()[recommendedKey(uid)]?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    suspend fun setRecommendedIds(uid: String, ids: List<String>) {
        store.edit { it[recommendedKey(uid)] = ids.joinToString(",") }
    }
}
