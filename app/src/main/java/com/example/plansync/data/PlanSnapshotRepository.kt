package com.example.plansync.data

import com.example.plansync.model.Invitation
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.example.plansync.model.RsvpStatus
import com.google.firebase.auth.FirebaseAuth
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

class PlanSnapshotRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private fun fileName(uid: String) = "my_plans_$uid.json"

    suspend fun getPlans(): Result<List<Plan>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { storage.read(fileName(uid)).toJsonList { it.toPlan() } }
    }

    suspend fun savePlans(plans: List<Plan>): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { storage.write(fileName(uid), plans.toJsonString { it.toJson() }) }
    }

    fun withRsvp(plans: List<Plan>, planId: String, status: RsvpStatus): List<Plan> {
        val uid = auth.currentUser?.uid ?: return plans
        return plans.map { plan ->
            if (plan.id != planId) {
                plan
            } else {
                plan.copy(
                    status = PlanStatus.CONFIRMED,
                    invitations = plan.invitations.map { if (it.userId == uid) it.copy(rsvp = status) else it }
                )
            }
        }
    }

    private fun Plan.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("date", date)
        .put("status", status.name)
        .put("dateTime", dateTime?.time)
        .put("activityIds", JSONArray(activityIds))
        .put("participants", JSONArray(participants.toJsonString { it.toJson() }))
        .put("invitations", JSONArray(invitations.toJsonString { it.toJson() }))

    private fun JSONObject.toPlan(): Plan = Plan(
        id = getString("id"),
        title = getString("title"),
        date = getString("date"),
        estimatedCostPerPerson = 0,
        participants = getJSONArray("participants").toString().toJsonList { it.toParticipant() },
        activities = emptyList(),
        status = PlanStatus.valueOf(getString("status")),
        activityIds = getJSONArray("activityIds").toStringList(),
        dateTime = if (has("dateTime")) Date(getLong("dateTime")) else null,
        invitations = getJSONArray("invitations").toString().toJsonList { it.toInvitation() }
    )

    private fun Participant.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("initials", initials)
        .put("avatarColorIndex", avatarColorIndex)
        .put("photoUrl", photoUrl)

    private fun JSONObject.toParticipant(): Participant = Participant(
        id = getString("id"),
        initials = getString("initials"),
        avatarColorIndex = getInt("avatarColorIndex"),
        photoUrl = getString("photoUrl")
    )

    private fun Invitation.toJson(): JSONObject = JSONObject()
        .put("userId", userId)
        .put("rsvp", rsvp.value)
        .put("invitedAt", invitedAt?.time)

    private fun JSONObject.toInvitation(): Invitation = Invitation(
        userId = getString("userId"),
        rsvp = RsvpStatus.from(getString("rsvp")) ?: RsvpStatus.INVITED,
        invitedAt = if (has("invitedAt")) Date(getLong("invitedAt")) else null
    )
}
