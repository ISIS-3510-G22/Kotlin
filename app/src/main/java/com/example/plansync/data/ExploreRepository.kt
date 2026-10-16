package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.google.firebase.firestore.FieldPath
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.tasks.await

class ExploreRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore
) {

    suspend fun getPlans(): Result<List<Plan>> = runCatching {
        val docs = db.collection("plans")
            .whereEqualTo("isPublic", true)
            .get()
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
                    .get()
                    .await()
                    .documents
                    .map { it.id to (it.getDouble("expectedPrice") ?: 0.0).toInt() }
            }.getOrDefault(emptyList())
        }.toMap()
}
