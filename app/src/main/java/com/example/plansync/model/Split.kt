package com.example.plansync.model

data class Split(
    val personId: String,
    val amount: Double,
    val paidSoFar: Double,
    val paid: Boolean
)
