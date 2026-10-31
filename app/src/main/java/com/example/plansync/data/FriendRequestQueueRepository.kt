package com.example.plansync.data

import com.example.plansync.model.PendingFriendAnswer
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class FriendRequestQueueRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private fun fileName(uid: String) = "pending_friend_answers_$uid.json"

    suspend fun getAll(): Result<List<PendingFriendAnswer>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching { storage.read(fileName(uid)).toJsonList { it.toAnswer() } }
    }

    suspend fun add(answer: PendingFriendAnswer): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            storage.update(fileName(uid)) { text ->
                val others = text.toJsonList { it.toAnswer() }.filter { it.requestId != answer.requestId }
                (others + answer).toJsonString { it.toJson() }
            }
        }
    }

    suspend fun remove(requestId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        return runCatching {
            storage.update(fileName(uid)) { text ->
                text.toJsonList { it.toAnswer() }.filter { it.requestId != requestId }.toJsonString { it.toJson() }
            }
        }
    }

    suspend fun flush(crewRepository: CrewRepository): Result<Unit> {
        val pending = getAll().getOrElse { return Result.failure(it) }
        for (answer in pending) {
            val result = if (answer.status == "accepted") {
                crewRepository.acceptFriendRequest(answer.requestId)
            } else {
                crewRepository.denyFriendRequest(answer.requestId)
            }
            result.getOrElse { return Result.failure(it) }
            remove(answer.requestId).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    private fun PendingFriendAnswer.toJson(): JSONObject = JSONObject()
        .put("requestId", requestId)
        .put("status", status)

    private fun JSONObject.toAnswer(): PendingFriendAnswer = PendingFriendAnswer(
        requestId = getString("requestId"),
        status = getString("status")
    )
}
