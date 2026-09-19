package com.example.plansync.model

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
    val planType: String = ""
) {
    val activityCount: Int get() = activities.size

    val priceTier: String get() = when {
        estimatedCostPerPerson <= 0 -> "Free"
        estimatedCostPerPerson <= 30 -> "$"
        estimatedCostPerPerson <= 80 -> "$$"
        else -> "$$$"
    }
}
