package com.example.plansync.data

import com.example.plansync.model.PendingReview
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class ReviewQueueRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private fun fileName(uid: String) = "pending_reviews_$uid.json"

    suspend fun getAll(): Result<List<PendingReview>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { storage.read(fileName(uid)).toJsonList { it.toPendingReview() } }
    }

    suspend fun add(review: PendingReview): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            storage.update(fileName(uid)) { text ->
                val others = text.toJsonList { it.toPendingReview() }.filter { it.planId != review.planId }
                (others + review).toJsonString { it.toJson() }
            }
        }
    }

    suspend fun remove(planId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            storage.update(fileName(uid)) { text ->
                text.toJsonList { it.toPendingReview() }.filter { it.planId != planId }.toJsonString { it.toJson() }
            }
        }
    }

    suspend fun flush(reviewRepository: ReviewRepository): Result<Unit> {
        val pending = getAll().getOrElse { return Result.failure(it) }
        for (review in pending) {
            reviewRepository.saveReview(review.planId, review.rating, review.comment)
                .getOrElse { return Result.failure(it) }
            remove(review.planId).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    private fun PendingReview.toJson(): JSONObject = JSONObject()
        .put("planId", planId)
        .put("rating", rating)
        .put("comment", comment)

    private fun JSONObject.toPendingReview(): PendingReview = PendingReview(
        planId = getString("planId"),
        rating = getInt("rating"),
        comment = getString("comment")
    )
}
