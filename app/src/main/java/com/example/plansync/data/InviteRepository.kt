package com.example.plansync.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.example.plansync.model.Contact
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class InviteRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth,
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    private fun contactsFile() = auth.currentUser?.uid?.let { "contacts_$it.json" }

    // Local storage strategy — own JSON file per user for offline contact list.
    suspend fun getSuggestedContacts(planId: String): List<Contact> {
        val uid = auth.currentUser?.uid ?: return emptyList()

        val fresh: List<Contact>? = try {
            withTimeout(8_000) { fetchFromFirestore(uid, planId) }
        } catch (_: Exception) { null }

        if (fresh != null) {
            // Cache without isInvited — that's plan-specific, resolved on read
            contactsFile()?.let { file ->
                storage.write(file, fresh.map { it.copy(isInvited = false) }.toJsonString { it.toJson() })
            }
            return fresh
        }

        // Offline: serve from own JSON file
        val file = contactsFile() ?: return emptyList()
        return storage.read(file).toJsonList { it.toContact() }
    }

    // All reads use Source.SERVER so stale cache is never served.
    // Any network failure throws — getSuggestedContacts catches it and falls back
    // to contacts_<uid>.json, preventing the cache from being overwritten with an empty list.
    private suspend fun fetchFromFirestore(uid: String, planId: String): List<Contact> {
        val invitedIds = suspendCancellableCoroutine<Set<String>> { cont ->
            db.collection("plans").document(planId).get(Source.SERVER)
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
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        val friendIds = mutableSetOf<String>()
        suspendCancellableCoroutine<Unit> { cont ->
            db.collection("friendRequests")
                .whereEqualTo("fromUserId", uid).whereEqualTo("status", "accepted")
                .get(Source.SERVER)
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc -> doc.getString("toUserId")?.let { friendIds.add(it) } }
                    cont.resume(Unit)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        suspendCancellableCoroutine<Unit> { cont ->
            db.collection("friendRequests")
                .whereEqualTo("toUserId", uid).whereEqualTo("status", "accepted")
                .get(Source.SERVER)
                .addOnSuccessListener { snap ->
                    snap.documents.forEach { doc -> doc.getString("fromUserId")?.let { friendIds.add(it) } }
                    cont.resume(Unit)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        val allIds = friendIds.toList()
        if (allIds.isEmpty()) return emptyList()

        val contacts = mutableListOf<Contact>()
        allIds.chunked(10).forEach { batch ->
            suspendCancellableCoroutine<Unit> { cont ->
                db.collection("users").whereIn(FieldPath.documentId(), batch).get(Source.SERVER)
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
                                isInvited = doc.id in invitedIds,
                                photoUrl = doc.getString("photoUrl").orEmpty()
                            ))
                        }
                        cont.resume(Unit)
                    }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        }
        return contacts
    }

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
