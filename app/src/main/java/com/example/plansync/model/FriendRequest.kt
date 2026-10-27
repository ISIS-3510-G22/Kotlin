package com.example.plansync.model

data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val name: String,
    val lastName: String,
    val username: String,
    val email: String,
    val photoUrl: String = ""
)
