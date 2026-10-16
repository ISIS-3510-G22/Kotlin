package com.example.plansync.model

import java.util.Date

data class Plan(
    val id: String,
    val title: String,
    val date: String,
    val estimatedCostPerPerson: Int,
    val participants: List<Participant>,
    val activities: List<Activity>,
    val status: PlanStatus,
    val rating: Double = 0.0,
    val category: String = "",
    val planType: String = "",
    val activityIds: List<String> = emptyList(),
    val dateTime: Date? = null,
    val tags: List<String> = emptyList()
) {
    val activityCount: Int get() = maxOf(activities.size, activityIds.size)

    val priceTier: String get() = when {
        estimatedCostPerPerson <= 0 -> "Free"
        estimatedCostPerPerson <= 30_000 -> "$"
        estimatedCostPerPerson <= 80_000 -> "$$"
        else -> "$$$"
    }
}
