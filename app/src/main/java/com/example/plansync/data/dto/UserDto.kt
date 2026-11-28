package com.example.plansync.data.dto

import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.example.plansync.model.User
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val lastName: String = "",
    val username: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val reimbursementMethods: List<PaymentMethodDto> = emptyList()
)

@Serializable
data class PaymentMethodDto(
    val id: String,
    val label: String,
    val detail: String,
    val type: String,
    val isPrimary: Boolean = false
)

fun UserDto.toModel() = User(
    id = id,
    name = name,
    email = email,
    lastName = lastName,
    username = username,
    phone = phone,
    photoUrl = photoUrl,
    reimbursementMethods = reimbursementMethods.map { it.toModel() }
)

fun User.toDto() = UserDto(
    id = id,
    name = name,
    email = email,
    lastName = lastName,
    username = username,
    phone = phone,
    photoUrl = photoUrl,
    reimbursementMethods = reimbursementMethods.map { it.toDto() }
)

fun PaymentMethodDto.toModel() = PaymentMethod(
    id = id,
    label = label,
    detail = detail,
    type = PaymentMethodType.entries.find { it.name == type } ?: PaymentMethodType.BANK,
    isPrimary = isPrimary
)

fun PaymentMethod.toDto() = PaymentMethodDto(
    id = id,
    label = label,
    detail = detail,
    type = type.name,
    isPrimary = isPrimary
)
