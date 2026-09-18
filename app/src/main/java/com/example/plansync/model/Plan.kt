package com.example.plansync.model

/**
 * Model layer — represents a user's Plan with its full detail.
 * Aggregates [Activity] and [Participant] lists; no business logic lives here.
 */
data class Plan(
    val id: String,
    val title: String,
    val date: String,
    val estimatedCostPerPerson: Int,
    val participants: List<Participant>,
    val activities: List<Activity>,
    val rating: Double = 0.0,
    val category: String = "",
    val planType: String = ""
) {
    /** Derived property — keeps the activity count in sync automatically. */
    val activityCount: Int get() = activities.size

    /** Derived property — buckets [estimatedCostPerPerson] into a price tier chip label. */
    val priceTier: String get() = when {
        estimatedCostPerPerson <= 0 -> "Free"
        estimatedCostPerPerson <= 30 -> "$"
        estimatedCostPerPerson <= 80 -> "$$"
        else -> "$$$"
    }
}
