package com.example.plansync.data

import com.google.firebase.firestore.FieldPath
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


    /** Returns friends of the current user as suggested contacts, marking those already invited to [planId]. */
    suspend fun getSuggestedContacts(planId: String): List<Contact> {
        val uid = auth.currentUser?.uid ?: return emptyList()

        // Fetch existing invitations for this plan to mark already-invited contacts
        val invitedIds = suspendCancellableCoroutine<Set<String>> { cont ->
            db.collection("plans").document(planId).get()
                .addOnSuccessListener { doc ->
                    val raw = (doc.get("invitations") as? List<*>).orEmpty()
                    val ids = raw.mapNotNull { e ->
                        when (e) {
                            is Map<*, *> -> e["userId"] as? String
                            is String -> e
                            else -> null
                        }
                    }.toSet()
                    cont.resume(ids)
                }
                .addOnFailureListener { cont.resume(emptySet()) }
        }

        // Collect friend IDs from friendRequests (accepted requests in both directions)
        val friendIds = mutableSetOf<String>()
        suspendCancellableCoroutine<Unit> { cont ->
            db.collection("friendRequests")
                .whereEqualTo("fromUserId", uid).whereEqualTo("status", "accepted")
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc -> doc.getString("toUserId")?.let { friendIds.add(it) } }
                    cont.resume(Unit)
                }
                .addOnFailureListener { cont.resume(Unit) }
        }
        suspendCancellableCoroutine<Unit> { cont ->
            db.collection("friendRequests")
                .whereEqualTo("toUserId", uid).whereEqualTo("status", "accepted")
                .get()
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc -> doc.getString("fromUserId")?.let { friendIds.add(it) } }
                    cont.resume(Unit)
                }
                .addOnFailureListener { cont.resume(Unit) }
        }

        // Legacy: users/{uid}/friends subcollection
        val legacyIds = suspendCancellableCoroutine<List<String>> { cont ->
            db.collection("users").document(uid).collection("friends").get()
                .addOnSuccessListener { snap -> cont.resume(snap.documents.map { it.id }) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

        val allIds = (friendIds + legacyIds).toList()
        if (allIds.isEmpty()) return emptyList()

        // Fetch user documents in batches of 10
        val contacts = mutableListOf<Contact>()
        allIds.chunked(10).forEach { batch ->
            suspendCancellableCoroutine<Unit> { cont ->
                db.collection("users").whereIn(FieldPath.documentId(), batch).get()
                    .addOnSuccessListener { snap ->
                        snap.documents.forEach { doc ->
                            val name = doc.getString("name").orEmpty()
                            val lastName = doc.getString("lastName").orEmpty()
                            val email = doc.getString("email").orEmpty()
                            val displayName = listOf(name, lastName).filter { it.isNotBlank() }.joinToString(" ")
                            contacts.add(Contact(
                                id = doc.id,
                                name = displayName.ifBlank { email },
                                handle = if (email.isNotBlank()) "@${email.substringBefore("@")}" else null,
                                isGroup = false,
                                isInvited = doc.id in invitedIds
                            ))
                        }
                        cont.resume(Unit)
                    }
                    .addOnFailureListener { cont.resume(Unit) }
            }
        }
        return contacts
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
