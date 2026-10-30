package com.example.plansync.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PlanDao {

    @Query("SELECT * FROM plans WHERE id = :planId")
    suspend fun getPlan(planId: String): PlanEntity?

    @Query("SELECT * FROM plan_activities WHERE planId = :planId")
    suspend fun getActivities(planId: String): List<PlanActivityEntity>

    @Query("SELECT * FROM participants WHERE planId = :planId")
    suspend fun getParticipants(planId: String): List<ParticipantEntity>

    @Upsert
    suspend fun upsertPlan(plan: PlanEntity)

    @Upsert
    suspend fun upsertActivities(activities: List<PlanActivityEntity>)

    @Upsert
    suspend fun upsertParticipants(participants: List<ParticipantEntity>)

    @Query("DELETE FROM plan_activities WHERE planId = :planId")
    suspend fun deleteActivities(planId: String)

    @Query("DELETE FROM participants WHERE planId = :planId")
    suspend fun deleteParticipants(planId: String)
}
