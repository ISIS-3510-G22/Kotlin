package com.example.plansync.data

import com.example.plansync.model.ActivityVisibility
import com.example.plansync.model.PendingAction
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
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
            is PendingAction.SaveActivity -> json
                .put("type", "save_activity")
                .put("activityId", activityId)
                .put("isNew", isNew)
                .put("name", name)
                .put("address", address)
                .put("price", price)
                .put("tags", JSONArray(tags))
                .put("newCustomTags", JSONArray(newCustomTags))
                .put("notes", notes)
                .put("visibility", visibility.name)
                .apply {
                    lat?.let { put("lat", it) }
                    lng?.let { put("lng", it) }
                }
            is PendingAction.SetLiked -> json
                .put("type", "set_liked")
                .put("activityId", activityId)
                .put("liked", liked)
            is PendingAction.UpdateProfile -> json
                .put("type", "update_profile")
                .put("name", name)
                .put("lastName", lastName)
                .put("phone", phone)
            is PendingAction.AddPaymentMethod -> json
                .put("type", "add_payment_method")
                .put("method", method.toJson())
            is PendingAction.DeletePaymentMethod -> json
                .put("type", "delete_payment_method")
                .put("method", method.toJson())
        }
    }

    private fun JSONObject.toPendingAction(): PendingAction {
        val id = getString("id")
        return when (getString("type")) {
            "save_activity" -> PendingAction.SaveActivity(
                activityId = getString("activityId"),
                isNew = getBoolean("isNew"),
                name = getString("name"),
                address = getString("address"),
                price = getInt("price"),
                tags = getJSONArray("tags").toStringList(),
                newCustomTags = getJSONArray("newCustomTags").toStringList(),
                notes = getString("notes"),
                visibility = ActivityVisibility.valueOf(getString("visibility")),
                lat = optDoubleOrNull("lat"),
                lng = optDoubleOrNull("lng"),
                id = id
            )
            "set_liked" -> PendingAction.SetLiked(
                activityId = getString("activityId"),
                liked = getBoolean("liked"),
                id = id
            )
            "update_profile" -> PendingAction.UpdateProfile(
                name = getString("name"),
                lastName = getString("lastName"),
                phone = getString("phone"),
                id = id
            )
            "add_payment_method" -> PendingAction.AddPaymentMethod(
                method = getJSONObject("method").toPaymentMethod(),
                id = id
            )
            else -> PendingAction.DeletePaymentMethod(
                method = getJSONObject("method").toPaymentMethod(),
                id = id
            )
        }
    }
}
