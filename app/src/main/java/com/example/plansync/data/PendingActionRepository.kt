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
            is PendingAction.DeleteActivity -> json
                .put("type", "delete_activity")
                .put("activityId", activityId)
                .put("photoUrl", photoUrl)
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
            is PendingAction.SetRsvp -> json
                .put("type", "set_rsvp")
                .put("planId", planId)
                .put("rsvp", rsvp)
            is PendingAction.SendInvite -> json
                .put("type", "send_invite")
                .put("planId", planId)
                .put("contactId", contactId)
            is PendingAction.AddExpense -> json
                .put("type", "add_expense")
                .put("planId", planId)
                .put("expenseId", expenseId)
                .put("name", name)
                .put("value", value)
                .put("paidById", paidById)
                .put("splitAmongIds", JSONArray(splitAmongIds))
                .put("createdAtMillis", createdAtMillis)
            is PendingAction.UpdateExpense -> json
                .put("type", "update_expense")
                .put("planId", planId)
                .put("expenseId", expenseId)
                .put("name", name)
                .put("value", value)
                .put("paidById", paidById)
                .put("resetSplit", resetSplit)
            is PendingAction.DeleteExpense -> json
                .put("type", "delete_expense")
                .put("planId", planId)
                .put("expenseId", expenseId)
            is PendingAction.AddSettlement -> json
                .put("type", "add_settlement")
                .put("planId", planId)
                .put("fromId", fromId)
                .put("toId", toId)
                .put("amount", amount)
            is PendingAction.DeleteMySettlementsTo -> json
                .put("type", "delete_my_settlements_to")
                .put("planId", planId)
                .put("fromId", fromId)
                .put("toId", toId)
            is PendingAction.AddActivityToPlan -> json
                .put("type", "add_activity_to_plan")
                .put("planId", planId)
                .put("activityId", activityId)
                .put("activityName", activityName)
                .put("tags", JSONArray(tags))
            is PendingAction.CreatePlan -> json
                .put("type", "create_plan")
                .put("planId", planId)
                .put("name", name)
                .put("dateMillis", dateMillis)
                .put("hour", hour)
                .put("minute", minute)
                .put("isPublic", isPublic)
                .put("activityIds", JSONArray(activityIds))
                .put("tags", JSONArray(tags))
                .apply {
                    origin?.let { put("origin", it) }
                    sourceId?.let { put("sourceId", it) }
                }
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
            "delete_activity" -> PendingAction.DeleteActivity(
                activityId = getString("activityId"),
                photoUrl = getString("photoUrl"),
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
            "set_rsvp" -> PendingAction.SetRsvp(
                planId = getString("planId"),
                rsvp   = getString("rsvp"),
                id     = id
            )
            "send_invite" -> PendingAction.SendInvite(
                planId    = getString("planId"),
                contactId = getString("contactId"),
                id        = id
            )
            "delete_payment_method" -> PendingAction.DeletePaymentMethod(
                method = getJSONObject("method").toPaymentMethod(),
                id = id
            )
            "add_expense" -> PendingAction.AddExpense(
                planId = getString("planId"),
                expenseId = getString("expenseId"),
                name = getString("name"),
                value = getDouble("value"),
                paidById = getString("paidById"),
                splitAmongIds = getJSONArray("splitAmongIds").toStringList(),
                createdAtMillis = getLong("createdAtMillis"),
                id = id
            )
            "update_expense" -> PendingAction.UpdateExpense(
                planId = getString("planId"),
                expenseId = getString("expenseId"),
                name = getString("name"),
                value = getDouble("value"),
                paidById = getString("paidById"),
                resetSplit = optBoolean("resetSplit", false),
                id = id
            )
            "delete_expense" -> PendingAction.DeleteExpense(
                planId = getString("planId"),
                expenseId = getString("expenseId"),
                id = id
            )
            "add_settlement" -> PendingAction.AddSettlement(
                planId = getString("planId"),
                fromId = getString("fromId"),
                toId = getString("toId"),
                amount = getDouble("amount"),
                id = id
            )
            "delete_my_settlements_to" -> PendingAction.DeleteMySettlementsTo(
                planId = getString("planId"),
                fromId = getString("fromId"),
                toId = getString("toId"),
                id = id
            )
            "add_activity_to_plan" -> PendingAction.AddActivityToPlan(
                planId = getString("planId"),
                activityId = getString("activityId"),
                activityName = getString("activityName"),
                tags = getJSONArray("tags").toStringList(),
                id = id
            )
            else -> PendingAction.CreatePlan(
                planId      = optString("planId").takeIf { it.isNotBlank() } ?: id,
                name        = getString("name"),
                dateMillis  = getLong("dateMillis"),
                hour        = getInt("hour"),
                minute      = getInt("minute"),
                isPublic    = getBoolean("isPublic"),
                activityIds = getJSONArray("activityIds").toStringList(),
                tags        = getJSONArray("tags").toStringList(),
                origin      = optString("origin").takeIf { it.isNotBlank() },
                sourceId    = optString("sourceId").takeIf { it.isNotBlank() },
                id          = id
            )
        }
    }
}
