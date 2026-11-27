package com.example.plansync.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PasswordResetRepository(
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    suspend fun sendResetEmail(email: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    continuation.resume(Result.success(Unit))
                }
                .addOnFailureListener { exception ->
                    val message = when (exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Enter a valid email address."
                        is FirebaseNetworkException -> "No internet connection. Please try again when online."
                        is FirebaseTooManyRequestsException -> "Too many requests. Please wait a moment and try again."
                        else -> exception.message ?: "Could not send the reset email."
                    }
                    continuation.resume(Result.failure(Exception(message)))
                }
        }
}
