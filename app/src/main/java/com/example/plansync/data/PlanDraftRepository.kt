package com.example.plansync.data

// Persists partially-filled Create Plan forms so users can resume after abandoning.
// Backed by Room (DraftDatabase) — replaces the previous single-draft DataStore.
class PlanDraftRepository {

    private val dao = DraftDatabase.dao

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

    suspend fun getAll(): List<Draft> = dao.getAll().map { it.toDraft() }

    suspend fun loadById(id: String): Draft? = dao.getById(id)?.toDraft()

    suspend fun loadLatest(): Draft? = dao.getLatest()?.toDraft()

    suspend fun save(draft: Draft) = dao.upsert(draft.toEntity())

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
