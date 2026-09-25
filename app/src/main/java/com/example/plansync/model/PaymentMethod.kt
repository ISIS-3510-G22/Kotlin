package com.example.plansync.model

data class PaymentMethod(
    val id: String,
    val label: String,
    val detail: String,
    val type: PaymentMethodType,
    val isPrimary: Boolean = false
)

enum class PaymentMethodType { BANK, VENMO }
