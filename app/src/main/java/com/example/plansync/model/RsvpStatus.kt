package com.example.plansync.model

/** Mirrors Flutter's rsvp string values stored in Firestore invitations maps. */
enum class RsvpStatus(val value: String) {
    GOING("going"),
    NOT_GOING("notGoing"),
    INVITED("invited");

    companion object {
        fun from(value: String?): RsvpStatus? = entries.find { it.value == value }
    }
}
