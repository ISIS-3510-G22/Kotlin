package com.example.plansync.data

import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import kotlinx.coroutines.delay

class ExploreRepository {

    suspend fun getPlans(): Result<List<Plan>> {
        delay(800)

        fun placeholderActivities(count: Int, planId: String) = List(count) { index ->
            Activity(
                id = "$planId-a$index",
                name = "Activity ${index + 1}",
                category = "",
                description = "",
                address = "",
                iconType = ActivityIcon.DEFAULT
            )
        }

        return Result.success(
            listOf(
                Plan(
                    id = "p1",
                    title = "Downtown Art Walk & Cafe Crawl",
                    date = "Nov 8, 2025",
                    estimatedCostPerPerson = 45,
                    status = PlanStatus.CONFIRMED,
                    participants = emptyList(),
                    activities = placeholderActivities(3, "p1"),
                    rating = 4.8,
                    category = "Food & Drink",
                    planType = "Group"
                ),
                Plan(
                    id = "p2",
                    title = "Sunset Hike & Picnic",
                    date = "Nov 15, 2025",
                    estimatedCostPerPerson = 15,
                    status = PlanStatus.CONFIRMED,
                    participants = emptyList(),
                    activities = placeholderActivities(2, "p2"),
                    rating = 4.5,
                    category = "Outdoors",
                    planType = "Couple"
                ),
                Plan(
                    id = "p3",
                    title = "Weekend Getaway to the Coast",
                    date = "Nov 22, 2025",
                    estimatedCostPerPerson = 120,
                    status = PlanStatus.CONFIRMED,
                    participants = emptyList(),
                    activities = placeholderActivities(5, "p3"),
                    rating = 4.9,
                    category = "Weekend Getaways",
                    planType = "Family"
                ),
                Plan(
                    id = "p4",
                    title = "Solo Museum Afternoon",
                    date = "Nov 29, 2025",
                    estimatedCostPerPerson = 0,
                    status = PlanStatus.PENDING_INVITE,
                    participants = emptyList(),
                    activities = placeholderActivities(1, "p4"),
                    rating = 4.2,
                    category = "Culture",
                    planType = "Solo"
                )
            )
        )
    }
}
