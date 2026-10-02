package com.example.plansync.data

import com.example.plansync.model.Friend
import com.example.plansync.model.Group
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Data layer — Repository pattern.
 *
 * Provides friends and groups data from Firestore:
 *  - Friends: users/{uid}/friends sub-collection
 *  - Groups:  top-level "groups" collection where memberIds array-contains uid
 */
class CrewRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = Firebase.firestore

    suspend fun getGroups(): Result<List<Group>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            db.collection("groups")
                .whereArrayContains("memberIds", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    val groups = snapshot.documents.map { doc ->
                        Group(
                            id          = doc.id,
                            name        = doc.getString("name").orEmpty(),
                            memberCount = (doc.get("memberIds") as? List<*>)?.size ?: 0
                        )
                    }
                    continuation.resume(Result.success(groups))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getFriends(): Result<List<Friend>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            db.collection("users").document(uid).collection("friends")
                .get()
                .addOnSuccessListener { snapshot ->
                    val friends = snapshot.documents.mapIndexed { index, doc ->
                        val name = doc.getString("name").orEmpty()
                        Friend(
                            id               = doc.id,
                            name             = name,
                            email            = doc.getString("email").orEmpty(),
                            initials         = name.split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .take(2)
                                .joinToString(""),
                            avatarColorIndex = index % 5
                        )
                    }
                    continuation.resume(Result.success(friends))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }
}
