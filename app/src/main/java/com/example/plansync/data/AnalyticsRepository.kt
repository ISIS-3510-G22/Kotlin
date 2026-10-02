package com.example.plansync.data

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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
class AnalyticsRepository {

    private val db = Firebase.firestore

    /**
     * Logs a named event with an optional [planId] and current timestamp.
     * Written to Firestore collection "events" asynchronously.
     */
    fun logEvent(type: String, planId: String? = null) {
        val event = hashMapOf(
            "type"      to type,
            "planId"    to planId,
            "timestamp" to Date()
        )
        db.collection("events")
            .add(event)
            .addOnFailureListener { /* silent — analytics must not interrupt UX */ }
    }

    companion object {
        // RSVP outcomes — answers the Type 2 BQ on confirmation rate
        const val RSVP_CONFIRMED = "rsvp_confirmed"
        const val RSVP_DECLINED  = "rsvp_declined"
        const val RSVP_DEFERRED  = "rsvp_deferred"

        // Invite and plan creation events
        const val INVITE_SENT  = "invite_sent"
        const val PLAN_CREATED = "plan_created"
    }
}
