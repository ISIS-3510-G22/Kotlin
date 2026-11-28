package com.example.plansync.data

import com.example.plansync.model.Expense
import com.example.plansync.model.Settlement
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class SplitRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth,
    private val snapshots: SplitSnapshotRepository = SplitSnapshotRepository()
) {

    private val plans = db.collection("plans")

    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun getExpenses(planId: String, online: Boolean = true): Result<List<Expense>> {
        SplitMemoryCache.getExpenses(planId)?.let { return Result.success(it) }
        val fresh = if (!online) null else runCatching {
            withTimeout(8_000) {
                plans.document(planId).collection("expenses").get(Source.SERVER).await()
                    .documents.map { it.toExpense() }
            }
        }.getOrNull()
        if (fresh != null) {
            SplitMemoryCache.putExpenses(planId, fresh)
            snapshots.saveExpenses(planId, fresh)
            return Result.success(fresh)
        }
        return snapshots.getExpenses(planId)?.let { Result.success(it) }
            ?: Result.failure(Exception(CONNECT_TO_LOAD))
    }

    suspend fun addExpense(
        planId: String,
        expenseId: String,
        name: String,
        value: Double,
        paidById: String,
        splitAmongIds: List<String>,
        createdAt: Date = Date()
    ): Result<Unit> = runCatching {
        plans.document(planId).collection("expenses").document(expenseId).set(
            mapOf(
                "name" to name,
                "value" to firestoreNumber(value),
                "paidById" to paidById,
                "splitAmongIds" to splitAmongIds,
                "splitMethod" to "equal",
                "customAmounts" to emptyMap<String, Double>(),
                "createdAt" to Timestamp(createdAt)
            )
        ).await()
        Unit
    }.also { SplitMemoryCache.invalidate(planId) }

    suspend fun updateExpense(
        planId: String,
        expenseId: String,
        name: String,
        value: Double,
        paidById: String,
        resetSplit: Boolean = false
    ): Result<Unit> = runCatching {
        val fields = mutableMapOf<String, Any>("name" to name, "value" to firestoreNumber(value), "paidById" to paidById)
        if (resetSplit) {
            fields["splitMethod"] = "equal"
            fields["customAmounts"] = emptyMap<String, Double>()
        }
        plans.document(planId).collection("expenses").document(expenseId)
            .update(fields)
            .await()
        Unit
    }.also { SplitMemoryCache.invalidate(planId) }

    suspend fun deleteExpense(planId: String, expenseId: String): Result<Unit> = runCatching {
        plans.document(planId).collection("expenses").document(expenseId).delete().await()
        Unit
    }.also { SplitMemoryCache.invalidate(planId) }

    suspend fun getSettlements(planId: String, online: Boolean = true): Result<List<Settlement>> {
        SplitMemoryCache.getSettlements(planId)?.let { return Result.success(it) }
        val fresh = if (!online) null else runCatching {
            withTimeout(8_000) {
                plans.document(planId).collection("settlements").get(Source.SERVER).await()
                    .documents.map { it.toSettlement() }
            }
        }.getOrNull()
        if (fresh != null) {
            SplitMemoryCache.putSettlements(planId, fresh)
            snapshots.saveSettlements(planId, fresh)
            return Result.success(fresh)
        }
        return snapshots.getSettlements(planId)?.let { Result.success(it) }
            ?: Result.failure(Exception(CONNECT_TO_LOAD))
    }

    suspend fun addSettlement(planId: String, toId: String, amount: Double): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val data = mapOf(
            "fromId" to uid,
            "toId" to toId,
            "amount" to Math.round(amount * 100) / 100.0
        )

        return suspendCancellableCoroutine<Result<Unit>> { continuation ->
            plans.document(planId).collection("settlements").add(data)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }.also { SplitMemoryCache.invalidate(planId) }
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

        return suspendCancellableCoroutine<Result<Unit>> { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }.also { SplitMemoryCache.invalidate(planId) }
    }

    private fun DocumentSnapshot.toExpense() = Expense(
        id            = id,
        name          = getString("name").orEmpty(),
        value         = (get("value") as? Number)?.toDouble() ?: 0.0,
        paidById      = getString("paidById").orEmpty(),
        splitAmongIds = (get("splitAmongIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        splitMethod   = getString("splitMethod") ?: "equal",
        customAmounts = (get("customAmounts") as? Map<*, *>).orEmpty()
            .mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.let { key to it.toDouble() } } }
            .toMap(),
        createdAt     = getTimestamp("createdAt")?.toDate()
    )

    private fun firestoreNumber(value: Double): Number =
        if (value % 1.0 == 0.0) value.toLong() else value

    companion object {
        const val CONNECT_TO_LOAD = "connect_to_load"
    }

    private fun DocumentSnapshot.toSettlement() = Settlement(
        id     = id,
        fromId = getString("fromId").orEmpty(),
        toId   = getString("toId").orEmpty(),
        amount = (get("amount") as? Number)?.toDouble() ?: 0.0
    )
}
