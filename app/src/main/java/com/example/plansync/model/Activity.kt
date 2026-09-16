package com.example.plansync.model

/**
 * Model layer — represents a single activity inside a Plan.
 * Plain data class with no Android or framework dependencies.
 */
data class Activity(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val address: String,
    val iconType: ActivityIcon
)

/**
 * Model layer — categorises the icon shown on the timeline beside each activity.
 * Adding a new icon type here is the only change needed to support new categories.
 */
enum class ActivityIcon {
    COFFEE,
    OUTDOORS,
    FOOD,
    MUSIC,
    DEFAULT
}
