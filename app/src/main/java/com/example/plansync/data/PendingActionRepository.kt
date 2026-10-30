package com.example.plansync.data

import com.example.plansync.model.PendingAction
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class PendingActionRepository(
    private val storage: LocalStorageProvider = LocalStorageProvider,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private fun fileName(): String? = auth.currentUser?.uid?.let { "pending_actions_$it.json" }

    suspend fun getAll(): List<PendingAction> {
        val file = fileName() ?: return emptyList()
        return storage.read(file).toJsonList { it.toPendingAction() }
    }

    suspend fun add(action: PendingAction) {
        val file = fileName() ?: return
        storage.update(file) { json ->
            (json.toJsonList { it.toPendingAction() } + action).toJsonString { it.toJson() }
        }
    }

    suspend fun remove(actionId: String) {
        val file = fileName() ?: return
        storage.update(file) { json ->
            json.toJsonList { it.toPendingAction() }.filter { it.id != actionId }.toJsonString { it.toJson() }
        }
    }

    private fun PendingAction.toJson(): JSONObject {
        val json = JSONObject().put("id", id)
        return when (this) {
            is PendingAction.SetLiked -> json
                .put("type", "set_liked")
                .put("activityId", activityId)
                .put("liked", liked)
        }
    }

    private fun JSONObject.toPendingAction(): PendingAction = PendingAction.SetLiked(
        activityId = getString("activityId"),
        liked = getBoolean("liked"),
        id = getString("id")
    )
}
