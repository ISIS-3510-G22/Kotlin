package com.example.plansync.data

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.net.URL
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class StorageRepository(
    private val storage: FirebaseStorage = FirebaseProvider.storage
) {

    suspend fun uploadImage(path: String, uri: Uri): Result<String> {
        val ref = storage.reference.child(path)

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

    suspend fun deleteImage(url: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        storage.getReferenceFromUrl(url).delete()
            .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
            .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
    }

    fun downloadToFile(url: String, file: File) {
        URL(url).openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
