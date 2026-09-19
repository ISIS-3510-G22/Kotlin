package com.example.plansync.data

import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import kotlinx.coroutines.delay

class PlanRepository {

    suspend fun getPlan(planId: String): Result<Plan> {
        delay(800) // simulado pero backend

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
                status = PlanStatus.CONFIRMED,
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

    suspend fun getPlans(): Result<List<Plan>> {
        delay(800)

        val saturdayInBrooklyn = Plan(
            id = "plan-001",
            title = "Saturday in Brooklyn",
            date = "Oct 28, 2023",
            estimatedCostPerPerson = 85,
            participants = listOf(
                Participant(id = "u1", initials = "JD", avatarColorIndex = 0),
                Participant(id = "u2", initials = "SA", avatarColorIndex = 1),
                Participant(id = "u3", initials = "MK", avatarColorIndex = 2),
                Participant(id = "u4", initials = "BL", avatarColorIndex = 3)
            ),
            status = PlanStatus.CONFIRMED,
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

        val techConferenceSf = Plan(
            id = "plan-002",
            title = "Tech Conference SF",
            date = "Nov 2, 2023",
            estimatedCostPerPerson = 350,
            participants = emptyList(),
            status = PlanStatus.CONFIRMED,
            activities = listOf(
                Activity(
                    id = "b1",
                    name = "Opening Keynote",
                    category = "Conference",
                    description = "Kickoff keynote covering this year's biggest announcements.",
                    address = "Moscone Center, San Francisco",
                    iconType = ActivityIcon.DEFAULT
                ),
                Activity(
                    id = "b2",
                    name = "Networking Mixer",
                    category = "Conference",
                    description = "Evening mixer with drinks and appetizers for attendees.",
                    address = "Moscone Center, San Francisco",
                    iconType = ActivityIcon.DEFAULT
                )
            )
        )

        val wineTastingInBogota = Plan(
            id = "plan-003",
            title = "Wine tasting in Bogota",
            date = "Oct 28, 2023 · 10:00 PM",
            estimatedCostPerPerson = 60,
            participants = listOf(
                Participant(id = "u5", initials = "AN", avatarColorIndex = 0),
                Participant(id = "u6", initials = "BR", avatarColorIndex = 1),
                Participant(id = "u7", initials = "CL", avatarColorIndex = 2),
                Participant(id = "u8", initials = "DI", avatarColorIndex = 3)
            ),
            status = PlanStatus.PENDING_INVITE,
            activities = listOf(
                Activity(
                    id = "c1",
                    name = "Vineyard Tasting Room",
                    category = "Wine",
                    description = "Guided tasting of Colombian and imported wines.",
                    address = "Zona T, Bogota",
                    iconType = ActivityIcon.DEFAULT
                )
            )
        )

        val rooftopBrunch = Plan(
            id = "plan-004",
            title = "Rooftop Brunch",
            date = "Jun 28, 2023 · 10:00 PM",
            estimatedCostPerPerson = 40,
            participants = listOf(
                Participant(id = "u9", initials = "RB", avatarColorIndex = 0),
                Participant(id = "u10", initials = "JD", avatarColorIndex = 1)
            ),
            status = PlanStatus.PAST,
            activities = listOf(
                Activity(
                    id = "d1",
                    name = "Rooftop Brunch",
                    category = "Food",
                    description = "Brunch with a skyline view.",
                    address = "Rooftop Lounge",
                    iconType = ActivityIcon.FOOD
                )
            )
        )

        return Result.success(
            listOf(saturdayInBrooklyn, techConferenceSf, wineTastingInBogota, rooftopBrunch)
        )
    }
}
