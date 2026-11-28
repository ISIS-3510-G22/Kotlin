package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source
import com.example.plansync.data.dto.ExplorePlanDto
import com.example.plansync.data.dto.toExploreDto
import com.example.plansync.data.dto.toModel
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.tasks.await

class ExploreRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    private val cacheFile = "explore_plans.json"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCachedPlans(): List<Plan> = runCatching {
        json.decodeFromString<List<ExplorePlanDto>>(storage.read(cacheFile) ?: "[]").map { it.toModel() }
    }.getOrDefault(emptyList())

    suspend fun cachePlans(plans: List<Plan>) {
        storage.write(cacheFile, json.encodeToString(plans.map { it.toExploreDto() }))
    }

    suspend fun getPlans(): Result<List<Plan>> = runCatching {
        val docs = db.collection("plans")
            .whereEqualTo("isPublic", true)
            .get(Source.SERVER)
            .await()
            .documents
        val activityIdsByPlan = docs.associate { doc ->
            doc.id to (doc.get("activityIds") as? List<*>).orEmpty().filterIsInstance<String>()
        }
        val prices = activityPrices(activityIdsByPlan.values.flatten().distinct())

        docs.map { doc ->
            val tags = (doc.get("tags") as? List<*>).orEmpty().filterIsInstance<String>()
            val activityIds = activityIdsByPlan[doc.id].orEmpty()
            Plan(
                id = doc.id,
                title = doc.getString("name").orEmpty(),
                date = doc.getTimestamp("date")
                    ?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it.toDate()) }
                    .orEmpty(),
                estimatedCostPerPerson = activityIds.sumOf { prices[it] ?: 0 },
                participants = (doc.get("participantsIds") as? List<*>).orEmpty()
                    .filterIsInstance<String>()
                    .mapIndexed { i, id -> Participant(id = id, initials = "", avatarColorIndex = i % 5) },
                activities = emptyList(),
                status = PlanStatus.CONFIRMED,
                category = tags.firstOrNull().orEmpty(),
                planType = "Group",
                activityIds = activityIds,
                tags = tags
            )
        }
    }

    private suspend fun activityPrices(ids: List<String>): Map<String, Int> =
        ids.chunked(30).flatMap { chunk ->
            runCatching {
                db.collection("activities")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get(Source.SERVER)
                    .await()
                    .documents
                    .map { it.id to (it.getDouble("expectedPrice") ?: 0.0).toInt() }
            }.getOrDefault(emptyList())
        }.toMap()
}
