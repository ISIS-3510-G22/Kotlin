package com.example.plansync.data

import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ProfileRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun updateProfile(name: String, lastName: String, phone: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid)
                .update(mapOf("name" to name, "lastName" to lastName, "phone" to phone))
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getPaymentMethods(): Result<List<PaymentMethod>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid).collection("paymentMethods")
                .orderBy("createdAt")
                .get()
                .addOnSuccessListener { snapshot ->
                    val methods = snapshot.documents.map { doc ->
                        PaymentMethod(
                            id = doc.id,
                            label = doc.getString("accountType").orEmpty(),
                            detail = doc.getString("account").orEmpty(),
                            type = runCatching { PaymentMethodType.valueOf(doc.getString("type").orEmpty()) }
                                .getOrDefault(PaymentMethodType.BANK),
                            isPrimary = doc.getBoolean("isPrimary") ?: false
                        )
                    }
                    continuation.resume(Result.success(methods))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun addPaymentMethod(accountType: String, account: String): Result<PaymentMethod> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val methods = Firebase.firestore.collection("users").document(uid).collection("paymentMethods")

        return suspendCancellableCoroutine { continuation ->
            methods.get()
                .addOnSuccessListener { existing ->
                    val isPrimary = existing.isEmpty
                    val type = if (accountType.contains("venmo", ignoreCase = true)) {
                        PaymentMethodType.VENMO
                    } else {
                        PaymentMethodType.BANK
                    }
                    val doc = methods.document()
                    val data = mapOf(
                        "accountType" to accountType,
                        "account" to account,
                        "type" to type.name,
                        "isPrimary" to isPrimary,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    doc.set(data)
                        .addOnSuccessListener {
                            continuation.resume(
                                Result.success(
                                    PaymentMethod(
                                        id = doc.id,
                                        label = accountType,
                                        detail = account,
                                        type = type,
                                        isPrimary = isPrimary
                                    )
                                )
                            )
                        }
                        .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }
}
