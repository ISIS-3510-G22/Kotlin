package com.example.plansync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.planDraftStore by preferencesDataStore(name = "plan_draft")

// Persists a partially filled Create Plan form so the user can resume after leaving the screen.
class PlanDraftRepository(private val context: Context) {

    private val store get() = context.planDraftStore

    data class Draft(
        val planName: String,
        val selectedDateMillis: Long?,
        val selectedHour: Int,
        val selectedMinute: Int,
        val locationText: String,
        val participantCount: Int,
        val isPublic: Boolean,
        val selectedActivityIds: List<String>,
        val origin: String?,
        val sourceId: String?
    )

    suspend fun load(): Draft {
        val prefs = store.data.first()
        return Draft(
            planName           = prefs[KEY_NAME].orEmpty(),
            selectedDateMillis = prefs[KEY_DATE]?.takeIf { it > 0 },
            selectedHour       = prefs[KEY_HOUR] ?: 10,
            selectedMinute     = prefs[KEY_MINUTE] ?: 0,
            locationText       = prefs[KEY_LOCATION].orEmpty(),
            participantCount   = prefs[KEY_COUNT] ?: 1,
            isPublic           = prefs[KEY_PUBLIC] ?: false,
            selectedActivityIds = prefs[KEY_ACTIVITY_IDS].orEmpty()
                .split(",").filter { it.isNotBlank() },
            origin             = prefs[KEY_ORIGIN],
            sourceId           = prefs[KEY_SOURCE_ID]
        )
    }

    suspend fun save(draft: Draft) {
        store.edit { prefs ->
            prefs[KEY_NAME]         = draft.planName
            prefs[KEY_DATE]         = draft.selectedDateMillis ?: -1L
            prefs[KEY_HOUR]         = draft.selectedHour
            prefs[KEY_MINUTE]       = draft.selectedMinute
            prefs[KEY_LOCATION]     = draft.locationText
            prefs[KEY_COUNT]        = draft.participantCount
            prefs[KEY_PUBLIC]       = draft.isPublic
            prefs[KEY_ACTIVITY_IDS] = draft.selectedActivityIds.joinToString(",")
            prefs[KEY_ORIGIN]       = draft.origin.orEmpty()
            prefs[KEY_SOURCE_ID]    = draft.sourceId.orEmpty()
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val KEY_NAME         = stringPreferencesKey("name")
        val KEY_DATE         = longPreferencesKey("date")
        val KEY_HOUR         = intPreferencesKey("hour")
        val KEY_MINUTE       = intPreferencesKey("minute")
        val KEY_LOCATION     = stringPreferencesKey("location")
        val KEY_COUNT        = intPreferencesKey("count")
        val KEY_PUBLIC       = booleanPreferencesKey("public")
        val KEY_ACTIVITY_IDS = stringPreferencesKey("activity_ids")
        val KEY_ORIGIN       = stringPreferencesKey("origin")
        val KEY_SOURCE_ID    = stringPreferencesKey("source_id")
    }
}
