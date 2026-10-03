package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Friend
import com.example.plansync.model.FriendRequest
import com.example.plansync.model.Group
import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.example.plansync.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class CrewRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val groups = db.collection("groups")
    private val users = db.collection("users")

    suspend fun getGroups(): Result<List<Group>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            groups
                .whereArrayContains("memberIds", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toGroup() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getFriends(): Result<List<Friend>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            users.document(uid).collection("friends")
                .get()
                .addOnSuccessListener { snapshot ->
                    val friends = snapshot.documents.mapIndexed { index, doc ->
                        Friend(
                            id               = doc.id,
                            name             = doc.getString("name").orEmpty(),
                            email            = doc.getString("email").orEmpty(),
                            avatarColorIndex = index
                        )
                    }
                    continuation.resume(Result.success(friends))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getGroup(groupId: String): Result<Group> =
        suspendCancellableCoroutine { continuation ->
            groups.document(groupId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        continuation.resume(Result.success(doc.toGroup()))
                    } else {
                        continuation.resume(Result.failure(Exception("Group not found.")))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun createGroup(name: String, description: String): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val data = mapOf(
            "name" to name,
            "description" to description,
            "memberIds" to listOf(uid),
            "invitedIds" to emptyList<String>()
        )

        return suspendCancellableCoroutine { continuation ->
            groups.add(data)
                .addOnSuccessListener { doc -> continuation.resume(Result.success(doc.id)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getUsersByIds(ids: List<String>): Result<List<User>> {
        val result = mutableListOf<User>()
        for (chunk in ids.chunked(30)) {
            val chunkUsers = getUsersChunk(chunk).getOrElse { return Result.failure(it) }
            result.addAll(chunkUsers)
        }
        return Result.success(result)
    }

    suspend fun getUser(userId: String): Result<User> =
        suspendCancellableCoroutine { continuation ->
            users.document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        continuation.resume(Result.success(doc.toUser()))
                    } else {
                        continuation.resume(Result.failure(Exception("User not found.")))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun searchUsers(query: String): Result<List<User>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            users.orderBy("username")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener { snapshot ->
                    val results = snapshot.documents.map { it.toUser() }.filter { it.id != uid }
                    continuation.resume(Result.success(results))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun sendFriendRequest(targetId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        val me = getUser(uid).getOrElse { return Result.failure(it) }

        val data = mapOf(
            "name" to me.name,
            "lastName" to me.lastName,
            "username" to me.username,
            "email" to me.email
        )

        return suspendCancellableCoroutine { continuation ->
            users.document(targetId).collection("friendRequests").document(uid)
                .set(data)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getFriendRequests(): Result<List<FriendRequest>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            users.document(uid).collection("friendRequests")
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toFriendRequest() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun acceptFriendRequest(request: FriendRequest): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        val me = getUser(uid).getOrElse { return Result.failure(it) }

        val friendForMe = mapOf(
            "name" to "${request.name} ${request.lastName}".trim(),
            "email" to request.email
        )
        val friendForThem = mapOf(
            "name" to "${me.name} ${me.lastName}".trim(),
            "email" to me.email
        )

        val batch = db.batch()
        batch.set(users.document(uid).collection("friends").document(request.id), friendForMe)
        batch.set(users.document(request.id).collection("friends").document(uid), friendForThem)
        batch.delete(users.document(uid).collection("friendRequests").document(request.id))

        return suspendCancellableCoroutine { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun denyFriendRequest(requestId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            users.document(uid).collection("friendRequests").document(requestId)
                .delete()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getGroupInvites(): Result<List<Group>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            groups
                .whereArrayContains("invitedIds", uid)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toGroup() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun inviteToGroup(groupId: String, friendId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            groups.document(groupId)
                .update("invitedIds", FieldValue.arrayUnion(friendId))
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun joinGroup(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val changes = mapOf(
            "invitedIds" to FieldValue.arrayRemove(uid),
            "memberIds" to FieldValue.arrayUnion(uid)
        )

        return suspendCancellableCoroutine { continuation ->
            groups.document(groupId)
                .update(changes)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun denyGroupInvite(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            groups.document(groupId)
                .update("invitedIds", FieldValue.arrayRemove(uid))
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getPhotoUrls(ids: List<String>): Result<Map<String, String>> =
        getUsersByIds(ids).map { users -> users.associate { it.id to it.photoUrl } }

    private suspend fun getUsersChunk(ids: List<String>): Result<List<User>> =
        suspendCancellableCoroutine { continuation ->
            users.whereIn(FieldPath.documentId(), ids)
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toUser() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    private fun DocumentSnapshot.toGroup() = Group(
        id          = id,
        name        = getString("name").orEmpty(),
        description = getString("description").orEmpty(),
        memberIds   = (get("memberIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        invitedIds  = (get("invitedIds") as? List<*>).orEmpty().filterIsInstance<String>()
    )

    private fun DocumentSnapshot.toUser() = User(
        id       = id,
        name     = getString("name").orEmpty(),
        email    = getString("email").orEmpty(),
        lastName = getString("lastName").orEmpty(),
        username = getString("username").orEmpty(),
        photoUrl = getString("photoUrl").orEmpty(),
        reimbursementMethods = (get("reimbursementMethods") as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .map { it.toPaymentMethod() }
    )

    private fun Map<*, *>.toPaymentMethod(): PaymentMethod {
        val type = this["type"] as? String ?: ""
        return PaymentMethod(
            id     = this["id"] as? String ?: "",
            label  = type,
            detail = this["account"] as? String ?: "",
            type   = if (type.contains("venmo", ignoreCase = true)) PaymentMethodType.VENMO else PaymentMethodType.BANK
        )
    }

    private fun DocumentSnapshot.toFriendRequest() = FriendRequest(
        id       = id,
        name     = getString("name").orEmpty(),
        lastName = getString("lastName").orEmpty(),
        username = getString("username").orEmpty(),
        email    = getString("email").orEmpty()
    )
}
