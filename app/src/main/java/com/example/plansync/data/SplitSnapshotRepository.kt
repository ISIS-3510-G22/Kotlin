package com.example.plansync.data

import com.example.plansync.data.dto.ExpenseDto
import com.example.plansync.data.dto.SettlementDto
import com.example.plansync.data.dto.toDto
import com.example.plansync.data.dto.toModel
import com.example.plansync.model.Expense
import com.example.plansync.model.Settlement
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.Json

class SplitSnapshotRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun expensesFile(planId: String) = auth.currentUser?.uid?.let { "expenses_${it}_$planId.json" }

    private fun settlementsFile(planId: String) = auth.currentUser?.uid?.let { "settlements_${it}_$planId.json" }

    suspend fun getExpenses(planId: String): List<Expense>? {
        val file = expensesFile(planId) ?: return null
        val content = storage.read(file) ?: return null
        return runCatching { json.decodeFromString<List<ExpenseDto>>(content).map { it.toModel() } }.getOrNull()
    }

    suspend fun saveExpenses(planId: String, expenses: List<Expense>) {
        val file = expensesFile(planId) ?: return
        storage.write(file, json.encodeToString(expenses.map { it.toDto() }))
    }

    suspend fun getSettlements(planId: String): List<Settlement>? {
        val file = settlementsFile(planId) ?: return null
        val content = storage.read(file) ?: return null
        return runCatching { json.decodeFromString<List<SettlementDto>>(content).map { it.toModel() } }.getOrNull()
    }

    suspend fun saveSettlements(planId: String, settlements: List<Settlement>) {
        val file = settlementsFile(planId) ?: return
        storage.write(file, json.encodeToString(settlements.map { it.toDto() }))
    }
}
