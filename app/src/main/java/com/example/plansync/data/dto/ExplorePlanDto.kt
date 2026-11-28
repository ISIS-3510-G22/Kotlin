package com.example.plansync.data.dto

import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import kotlinx.serialization.Serializable

@Serializable
data class ExplorePlanDto(
    val id: String,
    val title: String,
    val date: String = "",
    val estimatedCostPerPerson: Int = 0,
    val participantIds: List<String> = emptyList(),
    val category: String = "",
    val planType: String = "",
    val activityIds: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

fun ExplorePlanDto.toModel() = Plan(
    id = id,
    title = title,
    date = date,
    estimatedCostPerPerson = estimatedCostPerPerson,
    participants = participantIds.mapIndexed { i, id -> Participant(id = id, initials = "", avatarColorIndex = i % 5) },
    activities = emptyList(),
    status = PlanStatus.CONFIRMED,
    category = category,
    planType = planType,
    activityIds = activityIds,
    tags = tags
)

fun Plan.toExploreDto() = ExplorePlanDto(
    id = id,
    title = title,
    date = date,
    estimatedCostPerPerson = estimatedCostPerPerson,
    participantIds = participants.map { it.id },
    category = category,
    planType = planType,
    activityIds = activityIds,
    tags = tags
)
