package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.example.plansync.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.util.UUID
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ProfileRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth,
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    private fun cacheFile(): String? = auth.currentUser?.uid?.let { "profile_$it.json" }

    suspend fun getCachedUser(): User? {
        val file = cacheFile() ?: return null
        return storage.read(file)?.let { JSONObject(it).toUser() }
    }

    suspend fun cacheUser(user: User) {
        val file = cacheFile() ?: return
        storage.write(file, user.toJson().toString())
    }

    suspend fun updateProfile(name: String, lastName: String, phone: String): WriteResult {
        val uid = auth.currentUser?.uid
            ?: return WriteResult.Failed(Exception("No active session."))

        val task = db.collection("users").document(uid)
            .set(mapOf("name" to name, "lastName" to lastName, "phone" to phone), SetOptions.merge())
        return awaitWrite(task)
    }

    suspend fun uploadProfilePhoto(uri: Uri): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val photoUrl = StorageRepository().uploadImage("profile_photos/$uid.jpg", uri)
            .getOrElse { return Result.failure(it) }

        return suspendCancellableCoroutine { continuation ->
            db.collection("users").document(uid)
                .set(mapOf("photoUrl" to photoUrl), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(photoUrl)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    fun newPaymentMethod(accountType: String, account: String): PaymentMethod = PaymentMethod(
        id = UUID.randomUUID().toString(),
        label = accountType,
        detail = account,
        type = if (accountType.contains("venmo", ignoreCase = true)) PaymentMethodType.VENMO else PaymentMethodType.BANK
    )

    suspend fun addPaymentMethod(method: PaymentMethod): WriteResult {
        val uid = auth.currentUser?.uid
            ?: return WriteResult.Failed(Exception("No active session."))

        val data = mapOf("id" to method.id, "type" to method.label, "account" to method.detail)

        val task = db.collection("users").document(uid)
            .set(mapOf("reimbursementMethods" to FieldValue.arrayUnion(data)), SetOptions.merge())
        return awaitWrite(task)
    }

    suspend fun deletePaymentMethod(method: PaymentMethod): WriteResult {
        val uid = auth.currentUser?.uid
            ?: return WriteResult.Failed(Exception("No active session."))

        val data = mapOf("id" to method.id, "type" to method.label, "account" to method.detail)

        val task = db.collection("users").document(uid)
            .set(mapOf("reimbursementMethods" to FieldValue.arrayRemove(data)), SetOptions.merge())
        return awaitWrite(task)
    }

    private suspend fun awaitWrite(task: Task<Void>): WriteResult {
        return suspendCancellableCoroutine { continuation ->
            task
                .addOnSuccessListener { continuation.resume(WriteResult.Committed) }
                .addOnFailureListener { e -> continuation.resume(WriteResult.Failed(e)) }
        }
    }
}
