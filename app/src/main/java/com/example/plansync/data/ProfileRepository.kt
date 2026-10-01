package com.example.plansync.data

import com.example.plansync.model.PaymentMethod
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
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
        return Result.success(emptyList())
    }
}
