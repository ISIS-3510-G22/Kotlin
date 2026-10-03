package com.example.plansync.data

import com.example.plansync.model.Review
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ReviewRepository {

    private val auth = FirebaseAuth.getInstance()
    private val plans = Firebase.firestore.collection("plans")

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
}
