package com.example.plansync.model

import java.util.Date

data class Expense(
    val id: String,
    val name: String,
    val value: Double,
    val paidById: String,
    val splitAmongIds: List<String>,
    val splitMethod: String = "equal",
    val customAmounts: Map<String, Double> = emptyMap(),
    val createdAt: Date? = null
)
