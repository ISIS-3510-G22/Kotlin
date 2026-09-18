package com.example.plansync.data

import com.example.plansync.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Data layer — Repository pattern.
 * Single source of truth for all authentication operations.
 *
 * The ViewModel calls this class; UI composables never interact with it directly.
 * Swapping the auth backend (e.g. moving to a custom server) only requires
 * changing this file — LoginViewModel and LoginScreen remain untouched.
 */
class AuthRepository {

    // FirebaseAuth instance — entry point for all Firebase Auth operations
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /**
     * Attempts to sign in with the given [email] and [password] via Firebase Auth.
     *
     * Uses [suspendCancellableCoroutine] to bridge Firebase's callback-based
     * Task API into a suspend function compatible with Kotlin coroutines.
     * The coroutine is cancelled automatically if the caller's scope is cancelled
     * (e.g. user navigates away before the response arrives).
     *
     * @return [Result.success] wrapping a [User] on valid credentials,
     *         or [Result.failure] with a descriptive exception on error.
     */
    suspend fun login(email: String, password: String): Result<User> =
        suspendCancellableCoroutine { continuation ->
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val firebaseUser = authResult.user
                    if (firebaseUser != null) {
                        continuation.resume(
                            Result.success(
                                User(
                                    id = firebaseUser.uid,
                                    name = firebaseUser.displayName ?: "User",
                                    email = firebaseUser.email ?: email
                                )
                            )
                        )
                    } else {
                        continuation.resume(Result.failure(Exception("Login failed. Please try again.")))
                    }
                }
                .addOnFailureListener { exception ->
                    // Firebase provides descriptive messages for wrong password,
                    // user not found, network errors, etc.
                    continuation.resume(Result.failure(Exception(exception.message ?: "Authentication failed.")))
                }
        }

    /**
     * Signs out the currently authenticated user.
     * Called when navigating to a logout flow (future milestone).
     */
    fun signOut() {
        auth.signOut()
    }


    //Creates profile in Firebase

    suspend fun signUp(name: String, lastName:String, username: String, phone: String, email: String,
                        password: String): Result<User> = suspendCancellableCoroutine { continuation ->
                        auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener{
                            authResult -> val firebaseUser = authResult.user
                            if (firebaseUser == null){
                                continuation.resume(Result.failure(Exception("Sign up failed. Please try again.")))
                                return@addOnSuccessListener
                            }
                            val user = User(
                                id = firebaseUser.uid,
                                name = name,
                                lastName = lastName,
                                username = username,
                                phone = phone,
                                email = email
                            )
                            Firebase.firestore.collection("users").document(user.id).set(user)
                            .addOnSuccessListener{continuation.resume(Result.success(user))}
                            .addOnFailureListener{e -> continuation.resume(Result.failure(e))}
                        }
                        .addOnFailureListener{exception -> continuation.resume(Result.failure(Exception(exception.message?: "Sign up failed.")))
                        }
                    }

     //Returns the current user or null if there's no active session.
    fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            id = firebaseUser.uid,
            name = firebaseUser.displayName ?: "User",
            email = firebaseUser.email ?: ""
        )
    }
}
