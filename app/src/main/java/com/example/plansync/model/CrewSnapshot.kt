package com.example.plansync.model

data class CrewSnapshot(
    val groups: List<Group>,
    val friends: List<Friend>,
    val memberIdsByGroup: Map<String, List<String>>,
    val groupMembers: Map<String, User>
)
