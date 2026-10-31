package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.tasks.await

class ExploreRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    private val cacheFile = "explore_plans.json"

    suspend fun getCachedPlans(): List<Plan> =
        runCatching { storage.read(cacheFile).toJsonList { it.toPlan() } }.getOrDefault(emptyList())

    suspend fun cachePlans(plans: List<Plan>) {
        storage.write(cacheFile, plans.toJsonString { it.toJson() })
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

    private fun Plan.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("date", date)
        .put("estimatedCostPerPerson", estimatedCostPerPerson)
        .put("participantIds", JSONArray(participants.map { it.id }))
        .put("category", category)
        .put("planType", planType)
        .put("activityIds", JSONArray(activityIds))
        .put("tags", JSONArray(tags))

    private fun JSONObject.toPlan(): Plan = Plan(
        id = getString("id"),
        title = getString("title"),
        date = getString("date"),
        estimatedCostPerPerson = getInt("estimatedCostPerPerson"),
        participants = getJSONArray("participantIds").toStringList()
            .mapIndexed { i, id -> Participant(id = id, initials = "", avatarColorIndex = i % 5) },
        activities = emptyList(),
        status = PlanStatus.CONFIRMED,
        category = getString("category"),
        planType = getString("planType"),
        activityIds = getJSONArray("activityIds").toStringList(),
        tags = getJSONArray("tags").toStringList()
    )
}
