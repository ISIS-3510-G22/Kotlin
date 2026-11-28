package com.example.plansync.data.dto

import com.example.plansync.model.Expense
import java.util.Date
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseDto(
    val id: String,
    val name: String,
    val value: Double,
    val paidById: String,
    val splitAmongIds: List<String>,
    val splitMethod: String = "equal",
    val customAmounts: Map<String, Double> = emptyMap(),
    val createdAt: Long? = null
)

fun ExpenseDto.toModel() = Expense(
    id = id,
    name = name,
    value = value,
    paidById = paidById,
    splitAmongIds = splitAmongIds,
    splitMethod = splitMethod,
    customAmounts = customAmounts,
    createdAt = createdAt?.let(::Date)
)

fun Expense.toDto() = ExpenseDto(
    id = id,
    name = name,
    value = value,
    paidById = paidById,
    splitAmongIds = splitAmongIds,
    splitMethod = splitMethod,
    customAmounts = customAmounts,
    createdAt = createdAt?.time
)
