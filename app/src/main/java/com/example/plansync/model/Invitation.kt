package com.example.plansync.model

import java.util.Date

/** One entry in a plan's invitations array — matches Flutter's schema. */
data class Invitation(
    val userId: String,
    val rsvp: RsvpStatus,
    val invitedAt: Date?
)
