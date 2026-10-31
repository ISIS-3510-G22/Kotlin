package com.example.plansync.data

import androidx.room.Entity

@Entity(tableName = "crew_groups", primaryKeys = ["cachedFor", "id"])
data class CrewGroupEntity(
    val cachedFor: String,
    val id: String,
    val name: String,
    val description: String,
    val position: Int
)

@Entity(tableName = "crew_friends", primaryKeys = ["cachedFor", "id"])
data class CrewFriendEntity(
    val cachedFor: String,
    val id: String,
    val name: String,
    val email: String,
    val avatarColorIndex: Int,
    val photoUrl: String,
    val position: Int
)

@Entity(tableName = "crew_members", primaryKeys = ["cachedFor", "groupId", "userId"])
data class CrewMemberEntity(
    val cachedFor: String,
    val groupId: String,
    val userId: String,
    val position: Int,
    val hasProfile: Boolean,
    val name: String,
    val lastName: String,
    val photoUrl: String
)
