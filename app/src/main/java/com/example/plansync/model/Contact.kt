package com.example.plansync.model

/**
 * Model layer — represents a friend or group that can be invited to a Plan.
 *
 * [handle] is null for groups (e.g. "College Reunion") since groups
 * don't have @handles, only individual users do.
 *
 * [isGroup] drives the UI label and dialog copy:
 *   true  → "Every member of the group will be notified"
 *   false → "[name] is going to be notified"
 *
 * [isInvited] is the invite state shown in the list:
 *   true  → outlined "Invited" chip
 *   false → filled black "Invite" button
 */
data class Contact(
    val id: String,
    val name: String,
    val handle: String?,
    val isGroup: Boolean,
    val isInvited: Boolean = false
) {
    /** Initials shown inside the avatar — first two letters of each word. */
    val initials: String
        get() = name.split(" ")
            .take(2)
            .joinToString("") { it.first().uppercase() }
}
