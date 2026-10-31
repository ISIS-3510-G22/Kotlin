package com.example.plansync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first

private val Context.crewPrefsStore by preferencesDataStore(name = "crew_prefs")

class CrewPreferencesRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private fun tabKey(uid: String) = stringPreferencesKey("tab_$uid")

    private fun friendRequestsKey(uid: String) = intPreferencesKey("friend_requests_count_$uid")

    private fun groupInvitesKey(uid: String) = intPreferencesKey("group_invites_count_$uid")

    suspend fun getTab(): Result<String?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { context.crewPrefsStore.data.first()[tabKey(uid)] }
    }

    suspend fun saveTab(tab: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            context.crewPrefsStore.edit { it[tabKey(uid)] = tab }
            Unit
        }
    }

    suspend fun getFriendRequestsCount(): Result<Int?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { context.crewPrefsStore.data.first()[friendRequestsKey(uid)] }
    }

    suspend fun saveFriendRequestsCount(count: Int): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            context.crewPrefsStore.edit { it[friendRequestsKey(uid)] = count }
            Unit
        }
    }

    suspend fun getGroupInvitesCount(): Result<Int?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { context.crewPrefsStore.data.first()[groupInvitesKey(uid)] }
    }

    suspend fun saveGroupInvitesCount(count: Int): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            context.crewPrefsStore.edit { it[groupInvitesKey(uid)] = count }
            Unit
        }
    }
}
