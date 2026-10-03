package com.example.plansync.model

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val memberIds: List<String>,
    val invitedIds: List<String> = emptyList()
)
