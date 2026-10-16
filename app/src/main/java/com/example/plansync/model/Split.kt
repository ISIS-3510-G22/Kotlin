package com.example.plansync.model

data class Split(
    val id: String,
    val creditorId: String,
    val amount: Double,
    val paid: Boolean
)
