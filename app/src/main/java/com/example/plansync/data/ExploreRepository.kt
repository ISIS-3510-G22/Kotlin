package com.example.plansync.data

import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ExploreRepository {

    suspend fun getPlans(): Result<List<Plan>> =
        suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("plans")
                .whereEqualTo("isPublic", true)
                .get()
                .addOnSuccessListener { snapshot ->
                    val plans = snapshot.documents.map { doc ->
                        val tags = (doc.get("tags") as? List<*>).orEmpty().filterIsInstance<String>()
                        Plan(
                            id = doc.id,
                            title = doc.getString("name").orEmpty(),
                            date = doc.getTimestamp("date")
                                ?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it.toDate()) }
                                .orEmpty(),
                            estimatedCostPerPerson = 0,
                            participants = (doc.get("participantsIds") as? List<*>).orEmpty()
                                .filterIsInstance<String>()
                                .mapIndexed { i, id -> Participant(id = id, initials = "", avatarColorIndex = i % 5) },
                            activities = emptyList(),
                            status = PlanStatus.CONFIRMED,
                            category = tags.firstOrNull().orEmpty(),
                            planType = "Group",
                            activityIds = (doc.get("activityIds") as? List<*>).orEmpty().filterIsInstance<String>()
                        )
                    }
                    continuation.resume(Result.success(plans))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
}
