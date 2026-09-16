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
    val activities: List<Activity>
) {
    /** Derived property — keeps the activity count in sync automatically. */
    val activityCount: Int get() = activities.size
}
