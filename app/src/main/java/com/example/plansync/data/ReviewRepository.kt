package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Review
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class ReviewRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val plans = db.collection("plans")
    private val users = db.collection("users")

    suspend fun saveReview(planId: String, rating: Int, comment: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val userName = getUserName(uid).getOrElse { return Result.failure(it) }

        val data = mapOf(
            "userName" to userName,
            "rating" to rating,
            "comment" to comment,
            "createdAt" to Timestamp.now()
        )

        return suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("reviews").document(uid)
                .set(data)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getMyReview(planId: String): Result<Review?> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("reviews").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val review = Review(
                            rating = doc.getLong("rating")?.toInt() ?: 0,
                            comment = doc.getString("comment").orEmpty(),
                            createdAt = doc.getTimestamp("createdAt")?.toDate()
                        )
                        continuation.resume(Result.success(review))
                    } else {
                        continuation.resume(Result.success(null))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    private suspend fun getUserName(uid: String): Result<String> =
        suspendCancellableCoroutine { continuation ->
            users.document(uid).get()
                .addOnSuccessListener { doc ->
                    val fullName = "${doc.getString("name").orEmpty()} ${doc.getString("lastName").orEmpty()}".trim()
                    continuation.resume(Result.success(fullName.ifBlank { doc.getString("username").orEmpty() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun getRatingsByTag(): Result<Map<String, List<Int>>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val planDocs = suspendCancellableCoroutine<Result<List<DocumentSnapshot>>> { continuation ->
            plans.whereArrayContains("participantsIds", uid).get()
                .addOnSuccessListener { continuation.resume(Result.success(it.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }.getOrElse { return Result.failure(it) }

        val goingPlans = planDocs.filter { planDoc ->
            val date = planDoc.getTimestamp("date")?.toDate()
            date != null && date.before(Date()) && isGoing(planDoc, uid) && tagsOf(planDoc).isNotEmpty()
        }

        val ratingsByPlan = coroutineScope {
            goingPlans.map { planDoc ->
                async(Dispatchers.IO) { getRatings(planDoc.id) }
            }.awaitAll()
        }

        return withContext(Dispatchers.Default) {
            val ratingsByTag = mutableMapOf<String, MutableList<Int>>()
            for ((index, planDoc) in goingPlans.withIndex()) {
                val ratings = ratingsByPlan[index].getOrElse { return@withContext Result.failure(it) }
                if (ratings.isEmpty()) continue
                for (tag in tagsOf(planDoc)) {
                    ratingsByTag.getOrPut(tag) { mutableListOf() }.addAll(ratings)
                }
            }
            Result.success(ratingsByTag)
        }
    }

    private fun tagsOf(planDoc: DocumentSnapshot): List<String> =
        (planDoc.get("tags") as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }

    private fun isGoing(planDoc: DocumentSnapshot, uid: String): Boolean =
        (planDoc.get("invitations") as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .any { it["userId"] == uid && it["rsvp"] == "going" }

    private suspend fun getRatings(planId: String): Result<List<Int>> =
        suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("reviews").get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.mapNotNull { it.getLong("rating")?.toInt() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
}
