package com.example.plansync.data

class PasswordResetQueueRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    private val fileName = "pending_password_reset.txt"

    suspend fun getPending(): Result<String?> =
        runCatching { storage.read(fileName)?.trim()?.takeIf { it.isNotBlank() } }

    suspend fun save(email: String): Result<Unit> =
        runCatching { storage.write(fileName, email) }

    suspend fun clear(): Result<Unit> =
        runCatching { storage.write(fileName, "") }
}
