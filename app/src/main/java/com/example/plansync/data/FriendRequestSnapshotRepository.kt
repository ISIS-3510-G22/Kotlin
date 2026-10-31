package com.example.plansync.data

import com.example.plansync.model.FriendRequest
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class FriendRequestSnapshotRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private fun fileName(uid: String) = "friend_requests_$uid.json"

    suspend fun getRequests(): Result<List<FriendRequest>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { storage.read(fileName(uid)).toJsonList { it.toFriendRequest() } }
    }

    suspend fun saveRequests(requests: List<FriendRequest>): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { storage.write(fileName(uid), requests.toJsonString { it.toJson() }) }
    }

    private fun FriendRequest.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fromUserId", fromUserId)
        .put("name", name)
        .put("lastName", lastName)
        .put("username", username)
        .put("email", email)
        .put("photoUrl", photoUrl)

    private fun JSONObject.toFriendRequest(): FriendRequest = FriendRequest(
        id = getString("id"),
        fromUserId = getString("fromUserId"),
        name = getString("name"),
        lastName = getString("lastName"),
        username = getString("username"),
        email = getString("email"),
        photoUrl = getString("photoUrl")
    )
}
