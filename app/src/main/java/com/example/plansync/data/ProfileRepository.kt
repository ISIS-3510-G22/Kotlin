package com.example.plansync.data

import android.net.Uri
import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ProfileRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun updateProfile(name: String, lastName: String, phone: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid)
                .set(mapOf("name" to name, "lastName" to lastName, "phone" to phone), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun uploadProfilePhoto(uri: Uri): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val photoUrl = StorageRepository().uploadImage("profile_photos/$uid.jpg", uri)
            .getOrElse { return Result.failure(it) }

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid)
                .set(mapOf("photoUrl" to photoUrl), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(photoUrl)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun addPaymentMethod(accountType: String, account: String): Result<PaymentMethod> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val method = PaymentMethod(
            id = UUID.randomUUID().toString(),
            label = accountType,
            detail = account,
            type = if (accountType.contains("venmo", ignoreCase = true)) PaymentMethodType.VENMO else PaymentMethodType.BANK
        )
        val data = mapOf("id" to method.id, "type" to method.label, "account" to method.detail)

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid)
                .set(mapOf("reimbursementMethods" to FieldValue.arrayUnion(data)), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(method)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun deletePaymentMethod(method: PaymentMethod): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val data = mapOf("id" to method.id, "type" to method.label, "account" to method.detail)

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid)
                .set(mapOf("reimbursementMethods" to FieldValue.arrayRemove(data)), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }
}
