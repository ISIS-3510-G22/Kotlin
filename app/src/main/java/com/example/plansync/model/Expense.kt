package com.example.plansync.model

data class Expense(
    val id: String,
    val name: String,
    val value: Double,
    val paidById: String,
    val splitAmongIds: List<String>
)
