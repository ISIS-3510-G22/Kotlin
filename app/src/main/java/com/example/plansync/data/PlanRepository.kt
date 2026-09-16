package com.example.plansync.data

import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import kotlinx.coroutines.delay

/**
 * Data layer — Repository pattern.
 * Single source of truth for Plan data.
 *
 * The ViewModel calls this class; UI composables never interact with it directly.
 *
 * TODO: Replace stub implementation with real backend/Firestore calls once
 *       the database is configured. No remote dependencies are included yet —
 *       this is a placeholder only.
 */
class PlanRepository {

    /**
     * Returns the full detail of a single Plan by [planId].
     *
     * Stub behaviour:
     *  - Simulates a network round-trip with a 800ms delay.
     *  - Returns hardcoded "Saturday in Brooklyn" data regardless of [planId].
     *  - Returns [Result.failure] for any id prefixed with "error" (for testing).
     */
    suspend fun getPlan(planId: String): Result<Plan> {
        delay(800) // simulate network latency — remove when wiring real data source

        if (planId.startsWith("error")) {
            return Result.failure(Exception("Plan not found."))
        }

        return Result.success(
            Plan(
                id = planId,
                title = "Saturday in Brooklyn",
                date = "Oct 28, 2023",
                estimatedCostPerPerson = 85,
                participants = listOf(
                    Participant(id = "u1", initials = "JD", avatarColorIndex = 0),
                    Participant(id = "u2", initials = "SA", avatarColorIndex = 1),
                    Participant(id = "u3", initials = "MK", avatarColorIndex = 2),
                    Participant(id = "u4", initials = "BL", avatarColorIndex = 3)
                ),
                activities = listOf(
                    Activity(
                        id = "a1",
                        name = "Devoción",
                        category = "Coffee",
                        description = "Grab morning coffee in Williamsburg. Known for their lush interior and fresh Colombian beans.",
                        address = "69 Grand St, Brooklyn",
                        iconType = ActivityIcon.COFFEE
                    ),
                    Activity(
                        id = "a2",
                        name = "Domino Park",
                        category = "Outdoors",
                        description = "Walk along the waterfront. Great views of the Manhattan skyline and the Williamsburg Bridge.",
                        address = "15 River St, Brooklyn",
                        iconType = ActivityIcon.OUTDOORS
                    ),
                    Activity(
                        id = "a3",
                        name = "Juliana's Pizza",
                        category = "Food",
                        description = "Legendary coal-fired pizza under the Brooklyn Bridge. Arrive early to beat the line.",
                        address = "19 Old Fulton St, Brooklyn",
                        iconType = ActivityIcon.FOOD
                    ),
                    Activity(
                        id = "a4",
                        name = "Brooklyn Bowl",
                        category = "Music",
                        description = "End the night with live music and bowling in a legendary Williamsburg venue.",
                        address = "61 Wythe Ave, Brooklyn",
                        iconType = ActivityIcon.MUSIC
                    )
                )
            )
        )
    }
}
