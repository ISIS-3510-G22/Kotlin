package com.example.plansync.data

import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import kotlinx.coroutines.delay

class CrewRepository {

    suspend fun getGroups(): Result<List<Group>> {
        delay(800)

        return Result.success(
            listOf(
                Group(id = "g1", name = "Weekend Hikers", memberCount = 7), //fig
                Group(id = "g2", name = "Dinner Club", memberCount = 3),
                Group(id = "g3", name = "College Reunion", memberCount = 13)
            )
        )
    }

    suspend fun getFriends(): Result<List<Friend>> {
        delay(800)

        return Result.success(emptyList())
    }
}
