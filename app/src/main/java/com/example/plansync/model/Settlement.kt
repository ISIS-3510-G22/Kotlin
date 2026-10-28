package com.example.plansync.model

data class Settlement(
    val id: String,
    val fromId: String,
    val toId: String,
    val amount: Double
)
