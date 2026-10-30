package com.example.plansync.data

import com.example.plansync.model.PaymentMethod
import com.example.plansync.model.PaymentMethodType
import com.example.plansync.model.User
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

internal fun <T> String?.toJsonList(parse: (JSONObject) -> T): List<T> {
    val array = JSONArray(this ?: "[]")
    return List(array.length()) { parse(array.getJSONObject(it)) }
}

internal fun <T> List<T>.toJsonString(serialize: (T) -> JSONObject): String =
    JSONArray(map(serialize)).toString()

internal fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key)) getDouble(key) else null

internal fun PaymentMethod.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("label", label)
    .put("detail", detail)
    .put("type", type.name)
    .put("isPrimary", isPrimary)

internal fun JSONObject.toPaymentMethod(): PaymentMethod = PaymentMethod(
    id = getString("id"),
    label = getString("label"),
    detail = getString("detail"),
    type = PaymentMethodType.valueOf(getString("type")),
    isPrimary = getBoolean("isPrimary")
)

internal fun com.example.plansync.model.Contact.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("handle", handle ?: "")
    .put("isGroup", isGroup)
    .put("photoUrl", photoUrl)

internal fun JSONObject.toContact(): com.example.plansync.model.Contact =
    com.example.plansync.model.Contact(
        id = getString("id"),
        name = getString("name"),
        handle = getString("handle").ifBlank { null },
        isGroup = getBoolean("isGroup"),
        photoUrl = optString("photoUrl")
    )

internal fun User.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("email", email)
    .put("lastName", lastName)
    .put("username", username)
    .put("phone", phone)
    .put("photoUrl", photoUrl)
    .put("reimbursementMethods", JSONArray(reimbursementMethods.map { it.toJson() }))

internal fun JSONObject.toUser(): User = User(
    id = getString("id"),
    name = getString("name"),
    email = getString("email"),
    lastName = getString("lastName"),
    username = getString("username"),
    phone = getString("phone"),
    photoUrl = getString("photoUrl"),
    reimbursementMethods = getJSONArray("reimbursementMethods").toString().toJsonList { it.toPaymentMethod() }
)
