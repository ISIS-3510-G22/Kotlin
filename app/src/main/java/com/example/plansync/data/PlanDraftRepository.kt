package com.example.plansync.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Persists partially-filled Create Plan forms so users can resume after abandoning.
// Backed by Room (DraftDatabase) — replaces the previous single-draft DataStore.
class PlanDraftRepository(
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    companion object {
        // Outlives the ViewModel — used to flush a pending draft write in onCleared().
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val dao = DraftDatabase.dao
    private val currentUid: String get() = auth.currentUser?.uid.orEmpty()

    data class Draft(
        val id: String,
        val planName: String,
        val selectedDateMillis: Long?,
        val selectedHour: Int,
        val selectedMinute: Int,
        val locationText: String,
        val participantCount: Int,
        val isPublic: Boolean,
        val selectedActivityIds: List<String>,
        val origin: String?,
        val sourceId: String?,
        val lastStep: Int,
        val updatedAt: Long = System.currentTimeMillis()
    )

    suspend fun getAll(): List<Draft> = dao.getAll(currentUid).map { it.toDraft() }

    suspend fun loadById(id: String): Draft? = dao.getById(id, currentUid)?.toDraft()

    suspend fun loadLatest(): Draft? = dao.getLatest(currentUid)?.toDraft()

    suspend fun save(draft: Draft) = dao.upsert(draft.toEntity())

    // Launches an immediate write on the process-lifetime scope — safe to call from onCleared().
    fun saveNow(draft: Draft) { scope.launch { save(draft) } }

    suspend fun delete(id: String) = dao.delete(id)

    private fun DraftEntity.toDraft() = Draft(
        id                  = id,
        planName            = planName,
        selectedDateMillis  = selectedDateMillis.takeIf { it > 0 },
        selectedHour        = selectedHour,
        selectedMinute      = selectedMinute,
        locationText        = locationText,
        participantCount    = participantCount,
        isPublic            = isPublic,
        selectedActivityIds = activityIds.split(",").filter { it.isNotBlank() },
        origin              = origin.ifBlank { null },
        sourceId            = sourceId.ifBlank { null },
        lastStep            = lastStep,
        updatedAt           = updatedAt
    )

    private fun Draft.toEntity() = DraftEntity(
        id                 = id,
        uid                = currentUid,
        planName           = planName,
        selectedDateMillis = selectedDateMillis ?: -1L,
        selectedHour       = selectedHour,
        selectedMinute     = selectedMinute,
        locationText       = locationText,
        participantCount   = participantCount,
        isPublic           = isPublic,
        activityIds        = selectedActivityIds.joinToString(","),
        origin             = origin.orEmpty(),
        sourceId           = sourceId.orEmpty(),
        lastStep           = lastStep,
        updatedAt          = updatedAt
    )
}
