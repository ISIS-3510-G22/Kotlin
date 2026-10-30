package com.example.plansync.model

data class PendingReview(
    val planId: String,
    val rating: Int,
    val comment: String
)
