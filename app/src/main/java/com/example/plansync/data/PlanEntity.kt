package com.example.plansync.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus

// Local cache of plan metadata — one row per plan.
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val date: String,
    val costPerPerson: Int,
    val status: String,
    val category: String
)

// Each activity belonging to a plan — keyed by activity id + planId.
@Entity(tableName = "plan_activities")
data class PlanActivityEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val name: String,
    val category: String,
    val description: String,
    val address: String,
    val iconType: String,
    val price: Int
)

// Going participants for a plan.
@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val planId: String,
    val initials: String,
    val avatarColorIndex: Int,
    val photoUrl: String
)

// Mappers

fun Plan.toEntity() = PlanEntity(
    id = id,
    name = title,
    date = date,
    costPerPerson = estimatedCostPerPerson,
    status = status.name,
    category = category
)

fun PlanEntity.toPlan(activities: List<Activity>, participants: List<Participant>) = Plan(
    id = id,
    title = name,
    date = date,
    estimatedCostPerPerson = costPerPerson,
    participants = participants,
    activities = activities,
    status = runCatching { PlanStatus.valueOf(status) }.getOrDefault(PlanStatus.CONFIRMED),
    category = category
)

fun Activity.toEntity(planId: String) = PlanActivityEntity(
    id = id,
    planId = planId,
    name = name,
    category = category,
    description = description,
    address = address,
    iconType = iconType.name,
    price = price
)

fun PlanActivityEntity.toActivity() = Activity(
    id = id,
    name = name,
    category = category,
    description = description,
    address = address,
    iconType = runCatching { ActivityIcon.valueOf(iconType) }.getOrDefault(ActivityIcon.DEFAULT),
    price = price
)

fun Participant.toEntity(planId: String) = ParticipantEntity(
    id = id,
    planId = planId,
    initials = initials,
    avatarColorIndex = avatarColorIndex,
    photoUrl = photoUrl
)

fun ParticipantEntity.toParticipant() = Participant(
    id = id,
    initials = initials,
    avatarColorIndex = avatarColorIndex,
    photoUrl = photoUrl
)
