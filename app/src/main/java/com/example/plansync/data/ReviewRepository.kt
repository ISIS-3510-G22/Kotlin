package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Review
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ReviewRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val plans = db.collection("plans")

    suspend fun saveReview(planId: String, rating: Int, comment: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val data = mapOf(
            "rating" to rating,
            "comment" to comment
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
                            comment = doc.getString("comment").orEmpty()
                        )
                        continuation.resume(Result.success(review))
                    } else {
                        continuation.resume(Result.success(null))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getRatingsByTag(): Result<Map<String, List<Int>>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val planDocs = suspendCancellableCoroutine<Result<List<DocumentSnapshot>>> { continuation ->
            plans.whereArrayContains("participantsIds", uid).get()
                .addOnSuccessListener { continuation.resume(Result.success(it.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }.getOrElse { return Result.failure(it) }

        val ratingsByTag = mutableMapOf<String, MutableList<Int>>()
        for (planDoc in planDocs) {
            val date = planDoc.getTimestamp("date")?.toDate()
            if (date == null || !date.before(Date())) continue
            val tags = (planDoc.get("tags") as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }
            if (tags.isEmpty()) continue
            val ratings = getRatings(planDoc.id).getOrElse { return Result.failure(it) }
            if (ratings.isEmpty()) continue
            for (tag in tags) {
                ratingsByTag.getOrPut(tag) { mutableListOf() }.addAll(ratings)
            }
        }
        return Result.success(ratingsByTag)
    }

    private suspend fun getRatings(planId: String): Result<List<Int>> =
        suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("reviews").get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.mapNotNull { it.getLong("rating")?.toInt() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
}
