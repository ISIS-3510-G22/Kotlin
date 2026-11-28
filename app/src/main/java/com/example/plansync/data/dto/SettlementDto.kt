package com.example.plansync.data.dto

import com.example.plansync.model.Settlement
import kotlinx.serialization.Serializable

@Serializable
data class SettlementDto(
    val id: String,
    val fromId: String,
    val toId: String,
    val amount: Double
)

fun SettlementDto.toModel() = Settlement(
    id = id,
    fromId = fromId,
    toId = toId,
    amount = amount
)

fun Settlement.toDto() = SettlementDto(
    id = id,
    fromId = fromId,
    toId = toId,
    amount = amount
)
