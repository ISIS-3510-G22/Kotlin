package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Friend
import com.example.plansync.model.FriendRequest
import com.example.plansync.model.Group
import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.example.plansync.model.User
import com.google.firebase.Timestamp
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
    private val friendRequests = db.collection("friendRequests")

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

        val sent = getRequests("fromUserId", uid, "accepted").getOrElse { return Result.failure(it) }
        val received = getRequests("toUserId", uid, "accepted").getOrElse { return Result.failure(it) }
        val friendIds = (sent.map { it.getString("toUserId").orEmpty() } + received.map { it.getString("fromUserId").orEmpty() })
            .filter { it.isNotBlank() }
            .distinct()

        val friendUsers = getUsersByIds(friendIds).getOrElse { return Result.failure(it) }
        val friends = friendUsers.mapIndexed { index, user ->
            Friend(
                id               = user.id,
                name             = "${user.name} ${user.lastName}".trim(),
                email            = user.email,
                avatarColorIndex = index,
                photoUrl         = user.photoUrl
            )
        }
        return Result.success(friends)
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
        if (targetId == uid) return Result.failure(Exception("You can't send a friend request to yourself."))

        val sent = getRequestsBetween(uid, targetId).getOrElse { return Result.failure(it) }
        val received = getRequestsBetween(targetId, uid).getOrElse { return Result.failure(it) }
        val alreadyLinked = (sent + received).any {
            val status = it.getString("status")
            status == "pending" || status == "accepted"
        }
        if (alreadyLinked) return Result.failure(Exception("There is already a request or friendship with this user."))

        val data = mapOf(
            "fromUserId" to uid,
            "toUserId" to targetId,
            "sentOn" to Timestamp.now(),
            "status" to "pending"
        )

        return suspendCancellableCoroutine { continuation ->
            friendRequests.add(data)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getFriendRequests(): Result<List<FriendRequest>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val docs = getRequests("toUserId", uid, "pending").getOrElse { return Result.failure(it) }
        val senderIds = docs.map { it.getString("fromUserId").orEmpty() }.filter { it.isNotBlank() }.distinct()
        val senders = getUsersByIds(senderIds).getOrElse { return Result.failure(it) }.associateBy { it.id }

        val requests = docs.mapNotNull { doc ->
            val sender = senders[doc.getString("fromUserId").orEmpty()] ?: return@mapNotNull null
            FriendRequest(
                id         = doc.id,
                fromUserId = sender.id,
                name       = sender.name,
                lastName   = sender.lastName,
                username   = sender.username,
                email      = sender.email,
                photoUrl   = sender.photoUrl
            )
        }
        return Result.success(requests)
    }

    suspend fun acceptFriendRequest(requestId: String): Result<Unit> = setRequestStatus(requestId, "accepted")

    suspend fun denyFriendRequest(requestId: String): Result<Unit> = setRequestStatus(requestId, "denied")

    private suspend fun setRequestStatus(requestId: String, status: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            friendRequests.document(requestId)
                .update("status", status)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    private suspend fun getRequests(field: String, uid: String, status: String): Result<List<DocumentSnapshot>> =
        suspendCancellableCoroutine { continuation ->
            friendRequests
                .whereEqualTo(field, uid)
                .whereEqualTo("status", status)
                .get()
                .addOnSuccessListener { snapshot -> continuation.resume(Result.success(snapshot.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    private suspend fun getRequestsBetween(fromUserId: String, toUserId: String): Result<List<DocumentSnapshot>> =
        suspendCancellableCoroutine { continuation ->
            friendRequests
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("toUserId", toUserId)
                .get()
                .addOnSuccessListener { snapshot -> continuation.resume(Result.success(snapshot.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
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
}
