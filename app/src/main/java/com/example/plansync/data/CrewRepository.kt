package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.example.plansync.model.Friend
import com.example.plansync.model.FriendRequest
import com.example.plansync.model.Group
import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.example.plansync.model.User
import android.util.LruCache
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class CrewRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val groups = db.collection("groups")
    private val users = db.collection("users")
    private val friendRequests = db.collection("friendRequests")
    private val groupMembers = db.collection("groupMembers")
    private val groupInvitations = db.collection("groupInvitations")

    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun getGroups(): Result<List<Group>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val memberDocs = getDocs(groupMembers.whereEqualTo("userId", uid)).getOrElse { return Result.failure(it) }
        val groupIds = memberDocs.map { it.getString("groupId").orEmpty() }.filter { it.isNotBlank() }.distinct()
        return getGroupsByIds(groupIds)
    }

    suspend fun getMemberIds(groupIds: List<String>): Result<Map<String, List<String>>> {
        val memberIds = mutableMapOf<String, MutableList<String>>()
        for (chunk in groupIds.chunked(30)) {
            val docs = getDocs(groupMembers.whereIn("groupId", chunk)).getOrElse { return Result.failure(it) }
            for (doc in docs) {
                val groupId = doc.getString("groupId").orEmpty()
                val userId = doc.getString("userId").orEmpty()
                if (groupId.isNotBlank() && userId.isNotBlank()) {
                    memberIds.getOrPut(groupId) { mutableListOf() }.add(userId)
                }
            }
        }
        return Result.success(memberIds)
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

        val groupRef = groups.document()
        val groupData = mapOf(
            "name" to name,
            "description" to description
        )
        val memberData = mapOf(
            "groupId" to groupRef.id,
            "userId" to uid
        )

        val batch = db.batch()
        batch.set(groupRef, groupData)
        batch.set(groupMembers.document("${groupRef.id}_$uid"), memberData)

        return suspendCancellableCoroutine { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(groupRef.id)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getUsersByIds(ids: List<String>): Result<List<User>> {
        val found = mutableMapOf<String, User>()
        for (id in ids.distinct()) {
            val cached = userCache.get(id)
            if (cached != null) found[id] = cached
        }

        val missingIds = ids.distinct().filter { it !in found }
        for (chunk in missingIds.chunked(30)) {
            val chunkUsers = getUsersChunk(chunk).getOrElse { return Result.failure(it) }
            for (user in chunkUsers) {
                found[user.id] = user
                userCache.put(user.id, user)
            }
        }
        return Result.success(ids.distinct().mapNotNull { found[it] })
    }

    suspend fun getUser(userId: String): Result<User> =
        suspendCancellableCoroutine { continuation ->
            users.document(userId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val user = doc.toUser()
                        userCache.put(userId, user)
                        continuation.resume(Result.success(user))
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

        val inviteDocs = getDocs(
            groupInvitations.whereEqualTo("userId", uid).whereEqualTo("status", "pending")
        ).getOrElse { return Result.failure(it) }
        val groupIds = inviteDocs.map { it.getString("groupId").orEmpty() }.filter { it.isNotBlank() }.distinct()
        return getGroupsByIds(groupIds)
    }

    suspend fun getPendingInviteeIds(groupId: String): Result<List<String>> {
        val docs = getDocs(
            groupInvitations.whereEqualTo("groupId", groupId).whereEqualTo("status", "pending")
        ).getOrElse { return Result.failure(it) }
        return Result.success(docs.map { it.getString("userId").orEmpty() }.filter { it.isNotBlank() })
    }

    suspend fun inviteToGroup(groupId: String, friendId: String): Result<Unit> {
        val isMember = isMember(groupId, friendId).getOrElse { return Result.failure(it) }
        if (isMember) return Result.failure(Exception("This friend is already in the group."))

        val pending = getPendingInvitations(groupId, friendId).getOrElse { return Result.failure(it) }
        if (pending.isNotEmpty()) return Result.failure(Exception("This friend already has a pending invitation."))

        val data = mapOf(
            "groupId" to groupId,
            "userId" to friendId,
            "sentOn" to Timestamp.now(),
            "status" to "pending"
        )

        return suspendCancellableCoroutine { continuation ->
            groupInvitations.add(data)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun joinGroup(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val pending = getPendingInvitations(groupId, uid).getOrElse { return Result.failure(it) }
        if (pending.isEmpty()) return Result.failure(Exception("Invitation not found."))

        val memberData = mapOf(
            "groupId" to groupId,
            "userId" to uid
        )

        val batch = db.batch()
        for (doc in pending) {
            batch.update(doc.reference, "status", "accepted")
        }
        batch.set(groupMembers.document("${groupId}_$uid"), memberData)

        return suspendCancellableCoroutine { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun denyGroupInvite(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val pending = getPendingInvitations(groupId, uid).getOrElse { return Result.failure(it) }
        if (pending.isEmpty()) return Result.failure(Exception("Invitation not found."))

        val batch = db.batch()
        for (doc in pending) {
            batch.update(doc.reference, "status", "denied")
        }

        return suspendCancellableCoroutine { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    private suspend fun getPendingInvitations(groupId: String, userId: String): Result<List<DocumentSnapshot>> =
        getDocs(
            groupInvitations
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "pending")
        )

    private suspend fun isMember(groupId: String, userId: String): Result<Boolean> =
        suspendCancellableCoroutine { continuation ->
            groupMembers.document("${groupId}_$userId").get()
                .addOnSuccessListener { doc -> continuation.resume(Result.success(doc.exists())) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    private suspend fun getGroupsByIds(ids: List<String>): Result<List<Group>> {
        val result = mutableListOf<Group>()
        for (chunk in ids.chunked(30)) {
            val docs = getDocs(groups.whereIn(FieldPath.documentId(), chunk)).getOrElse { return Result.failure(it) }
            result.addAll(docs.map { it.toGroup() })
        }
        return Result.success(result)
    }

    private suspend fun getDocs(query: Query): Result<List<DocumentSnapshot>> =
        suspendCancellableCoroutine { continuation ->
            query.get()
                .addOnSuccessListener { snapshot -> continuation.resume(Result.success(snapshot.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
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
        description = getString("description").orEmpty()
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

    companion object {
        private val userCache = LruCache<String, User>(25)
    }
}
