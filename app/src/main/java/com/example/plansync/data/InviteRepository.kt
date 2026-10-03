package com.example.plansync.data

import com.example.plansync.model.Contact
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Data layer — Repository pattern.
 * Single source of truth for invite-related operations.
 */
class InviteRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore

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
                            isInvited = false,
                            initials = name.split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .take(2)
                                .joinToString("")
                        )
                    }
                    continuation.resume(contacts)
                }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
    }

    /**
     * Adds the contactId to the plan's invitations array in Firestore.
     */
    suspend fun inviteContact(planId: String, contactId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            db.collection("plans").document(planId)
                .set(mapOf("invitations" to FieldValue.arrayUnion(contactId)), SetOptions.merge())
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
}
