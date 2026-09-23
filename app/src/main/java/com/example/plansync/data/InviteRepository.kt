package com.example.plansync.data

import com.example.plansync.model.Contact
import kotlinx.coroutines.delay

/**
 * Data layer — Repository pattern.
 * Single source of truth for invite-related operations.
 *
 * TODO: Replace stub with real Firestore/backend calls once the
 *       social graph data model is set up. No remote dependencies added yet.
 */
class InviteRepository {

    /**
     * Returns a list of suggested friends and groups for the current user.
     * Stubbed with hardcoded data matching the Figma mockup.
     */
    suspend fun getSuggestedContacts(): List<Contact> {
        delay(500) // simulate network latency
        return listOf(
            Contact(id = "u1", name = "Sarah Jenkins", handle = "@sarahj",  isGroup = false, isInvited = true),
            Contact(id = "u2", name = "Marcus Koh",    handle = "@marcusk", isGroup = false, isInvited = false),
            Contact(id = "u3", name = "John Doe",      handle = "@johnd",   isGroup = false, isInvited = false),
            Contact(id = "u4", name = "Billy Low",     handle = "@billyl",  isGroup = false, isInvited = true),
            Contact(id = "u5", name = "Diana Prince",  handle = "@diana",   isGroup = false, isInvited = false),
            Contact(id = "g1", name = "College Reunion", handle = null,     isGroup = true,  isInvited = false)
        )
    }

    /**
     * Marks a contact as invited for the given [planId].
     * Stub — always succeeds after a short delay.
     */
    suspend fun inviteContact(planId: String, contactId: String): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }
}
