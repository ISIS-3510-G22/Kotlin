package com.example.plansync.data

import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.ActivityVisibility
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

internal fun Activity.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("category", category)
    .put("description", description)
    .put("address", address)
    .put("iconType", iconType.name)
    .put("price", price)
    .put("visibility", visibility.name)
    .put("ownerId", ownerId)
    .put("likedBy", JSONArray(likedBy))
    .put("tags", JSONArray(tags))
    .put("photoUrl", photoUrl)
    .apply {
        lat?.let { put("lat", it) }
        lng?.let { put("lng", it) }
    }

internal fun JSONObject.toActivity(): Activity = Activity(
    id = getString("id"),
    name = getString("name"),
    category = getString("category"),
    description = getString("description"),
    address = getString("address"),
    iconType = ActivityIcon.valueOf(getString("iconType")),
    price = getInt("price"),
    visibility = ActivityVisibility.valueOf(getString("visibility")),
    ownerId = getString("ownerId"),
    likedBy = getJSONArray("likedBy").toStringList(),
    tags = getJSONArray("tags").toStringList(),
    photoUrl = getString("photoUrl"),
    lat = optDoubleOrNull("lat"),
    lng = optDoubleOrNull("lng")
)

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
