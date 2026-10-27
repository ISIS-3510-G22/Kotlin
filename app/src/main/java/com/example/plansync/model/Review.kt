package com.example.plansync.model

import java.util.Date

data class Review(
    val rating: Int,
    val comment: String,
    val createdAt: Date? = null
)
