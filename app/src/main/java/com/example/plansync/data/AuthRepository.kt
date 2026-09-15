package com.example.plansync.data

import com.example.plansync.model.User
import kotlinx.coroutines.delay

/**
 * Data layer — Repository pattern.
 * Single source of truth for all authentication operations.
 *
 * The ViewModel calls this class; UI composables never interact with it directly.
 *
 * TODO: Replace stub implementation with real Firebase Auth calls once
 *       Firebase is configured in the project. No Firebase dependencies are
 *       included yet — this is a placeholder only.
 */
class AuthRepository {

    /**
     * Attempts to sign in with the given credentials.
     *
     * @return [Result.success] wrapping a [User] on valid credentials,
     *         or [Result.failure] with a descriptive exception otherwise.
     *
     * Stub behaviour:
     *  - Simulates a network round-trip with a 1-second delay.
     *  - Accepts one hard-coded credential pair for demo/testing purposes.
     */
    suspend fun login(email: String, password: String): Result<User> {
        delay(1_000) // simulate network latency — remove when wiring real Firebase

        return if (email == "user@plansync.com" && password == "password123") {
            Result.success(
                User(
                    id = "stub-uid-001",
                    name = "Demo User",
                    email = email
                )
            )
        } else {
            Result.failure(Exception("Invalid email or password."))
        }
    }
}
