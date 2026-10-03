package com.example.plansync.data

import com.example.plansync.model.Split
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class SplitRepository {

    private val auth = FirebaseAuth.getInstance()
    private val plans = Firebase.firestore.collection("plans")

    suspend fun getMySplits(planId: String): Result<List<Split>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("splits")
                .whereEqualTo("debtorId", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toSplit() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun setSplitPaid(planId: String, splitId: String, paid: Boolean): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("splits").document(splitId)
                .update("paid", paid)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    private fun DocumentSnapshot.toSplit() = Split(
        id         = id,
        creditorId = getString("creditorId").orEmpty(),
        amount     = getDouble("amount") ?: 0.0,
        paid       = getBoolean("paid") ?: false
    )
}
