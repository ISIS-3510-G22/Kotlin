package com.example.plansync.model

/**
 * Model layer — represents a participant in a Plan.
 *
 * [avatarColorIndex] drives the coral-to-white gradient on the participant
 * avatar row in the UI: 0 = darkest coral, higher values = progressively lighter.
 * The UI layer maps this index to an actual Color — the model stays color-agnostic.
 */
data class Participant(
    val id: String,
    val initials: String,
    val avatarColorIndex: Int
)
