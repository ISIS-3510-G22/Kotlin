package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Contact
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Data layer — Repository pattern.
 * Single source of truth for invite-related operations.
 */
class InviteRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {


    /**
     * Returns the current user's friends from Firestore as suggested contacts.
     */
    suspend fun getSuggestedContacts(): List<Contact> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        return suspendCancellableCoroutine { continuation ->
            db.collection("users").document(uid).collection("friends")
                .get()
                .addOnSuccessListener { snapshot ->
                    val contacts = snapshot.documents.map { doc ->
                        val name = doc.getString("name").orEmpty()
                        val email = doc.getString("email").orEmpty()
                        Contact(
                            id = doc.id,
                            name = name,
                            handle = if (email.isNotBlank()) "@${email.substringBefore("@")}" else null,
                            isGroup = false,
                            isInvited = false
                        )
                    }
                    continuation.resume(contacts)
                }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
    }

    /** Adds the invitee to participantsIds and writes a {userId, rsvp, invitedAt} map entry. */
    suspend fun inviteContact(planId: String, contactId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val invitationEntry = hashMapOf(
                "userId"    to contactId,
                "rsvp"      to "invited",
                "invitedAt" to Timestamp.now()
            )
            db.collection("plans").document(planId)
                .update(mapOf(
                    "participantsIds" to FieldValue.arrayUnion(contactId),
                    "invitations"     to FieldValue.arrayUnion(invitationEntry)
                ))
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
}
