package com.example.plansync.data

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class StorageRepository {

    suspend fun uploadImage(path: String, uri: Uri): Result<String> {
        val ref = Firebase.storage.reference.child(path)

        return suspendCancellableCoroutine { continuation ->
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl
                        .addOnSuccessListener { continuation.resume(Result.success(it.toString())) }
                        .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }
}
