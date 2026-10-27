package com.example.plansync.data

import com.example.plansync.model.Expense
import com.example.plansync.model.Settlement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class SplitRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val plans = db.collection("plans")

    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun getExpenses(planId: String): Result<List<Expense>> =
        suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("expenses").get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toExpense() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun getSettlements(planId: String): Result<List<Settlement>> =
        suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("settlements").get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toSettlement() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun addSettlement(planId: String, toId: String, amount: Double): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val data = mapOf(
            "fromId" to uid,
            "toId" to toId,
            "amount" to amount
        )

        return suspendCancellableCoroutine { continuation ->
            plans.document(planId).collection("settlements").add(data)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun deleteMySettlementsTo(planId: String, toId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val docs = suspendCancellableCoroutine<Result<List<DocumentSnapshot>>> { continuation ->
            plans.document(planId).collection("settlements")
                .whereEqualTo("fromId", uid)
                .whereEqualTo("toId", toId)
                .get()
                .addOnSuccessListener { snapshot -> continuation.resume(Result.success(snapshot.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }.getOrElse { return Result.failure(it) }

        val batch = db.batch()
        for (doc in docs) {
            batch.delete(doc.reference)
        }

        return suspendCancellableCoroutine { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    private fun DocumentSnapshot.toExpense() = Expense(
        id            = id,
        name          = getString("name").orEmpty(),
        value         = (get("value") as? Number)?.toDouble() ?: 0.0,
        paidById      = getString("paidById").orEmpty(),
        splitAmongIds = (get("splitAmongIds") as? List<*>).orEmpty().filterIsInstance<String>()
    )

    private fun DocumentSnapshot.toSettlement() = Settlement(
        id     = id,
        fromId = getString("fromId").orEmpty(),
        toId   = getString("toId").orEmpty(),
        amount = (get("amount") as? Number)?.toDouble() ?: 0.0
    )
}
