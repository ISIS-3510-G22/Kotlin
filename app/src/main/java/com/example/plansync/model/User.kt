package com.example.plansync.model

/**
 * Model layer — plain data class representing an authenticated user.
 * No Android or framework dependencies; can be unit-tested without a device.
 */
data class User(
    val id: String,
    val name: String,
    val email: String
)
