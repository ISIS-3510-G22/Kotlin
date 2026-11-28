package com.example.plansync.model

data class OverdueDebt(
    val id: String,
    val planId: String,
    val planName: String,
    val amount: Double,
    val daysOverdue: Int,
    val fromId: String,
    val toId: String,
    val toName: String,
    val updatedAt: Long
)
