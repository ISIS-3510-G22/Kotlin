package com.example.plansync.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

/**
 * Data layer — Analytics pipeline.
 *
 * Logs user interaction events to Firestore's "events" collection.
 * Used to answer Type 2 Business Question:
 *   "What percentage of plan invites result in RSVP confirmation?"
 *
 * Silent on failure — analytics must never crash the app.
 * Each ViewModel injects this repository to log its own events.
 */
class AnalyticsRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    /** Logs a named event. All parameters beyond type are optional context. */
    fun logEvent(
        type: String,
        planId: String? = null,
        inviteeId: String? = null,
        step: Int? = null,
        flowSessionId: String? = null,
        origin: String? = null,
        sourceId: String? = null
    ) {
        val event = hashMapOf<String, Any?>(
            "type"          to type,
            "userId"        to auth.currentUser?.uid,
            "timestamp"     to Date()
        ).apply {
            planId?.let        { put("planId", it) }
            inviteeId?.let     { put("inviteeId", it) }
            step?.let          { put("step", it) }
            flowSessionId?.let { put("flowSessionId", it) }
            origin?.let        { put("origin", it) }
            sourceId?.let      { put("sourceId", it) }
        }
        db.collection("events")
            .add(event)
            .addOnFailureListener { /* silent — analytics must not interrupt UX */ }
    }

    companion object {
        // RSVP outcomes
        const val RSVP_CONFIRMED = "rsvp_confirmed"
        const val RSVP_DECLINED  = "rsvp_declined"
        const val RSVP_DEFERRED  = "rsvp_deferred"

        // Invite and plan creation events
        const val INVITE_SENT   = "invite_sent"
        const val PLAN_CREATED  = "plan_created"
        const val EXPENSE_ADDED = "expense_added"

        // Create-plan funnel — Type 2 BQ: where do users abandon?
        const val CREATE_PLAN_STEP     = "create_plan_step"
        const val CREATE_PLAN_ABANDONED = "create_plan_abandoned"
    }
}
