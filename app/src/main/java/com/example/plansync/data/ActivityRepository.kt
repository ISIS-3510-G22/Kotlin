package com.example.plansync.data

import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.ActivityVisibility
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ActivityRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun getActivities(): Result<List<Activity>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            Firebase.firestore.collection("users").document(uid).collection("activities")
                .orderBy("createdAt")
                .get()
                .addOnSuccessListener { snapshot ->
                    val activities = snapshot.documents.map { doc ->
                        val category = doc.getString("category").orEmpty()
                        Activity(
                            id = doc.id,
                            name = doc.getString("name").orEmpty(),
                            category = category,
                            description = doc.getString("description").orEmpty(),
                            address = doc.getString("address").orEmpty(),
                            iconType = iconForCategory(category),
                            price = (doc.getLong("price") ?: 0L).toInt(),
                            visibility = runCatching {
                                ActivityVisibility.valueOf(doc.getString("visibility").orEmpty())
                            }.getOrDefault(ActivityVisibility.PRIVATE)
                        )
                    }
                    continuation.resume(Result.success(activities))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun addActivity(
        name: String,
        address: String,
        price: Int,
        category: String,
        notes: String,
        visibility: ActivityVisibility
    ): Result<Activity> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val activities = Firebase.firestore.collection("users").document(uid).collection("activities")
        val doc = activities.document()
        val data = mapOf(
            "name" to name,
            "address" to address,
            "price" to price,
            "category" to category,
            "description" to notes,
            "visibility" to visibility.name,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return suspendCancellableCoroutine { continuation ->
            doc.set(data)
                .addOnSuccessListener {
                    continuation.resume(
                        Result.success(
                            Activity(
                                id = doc.id,
                                name = name,
                                category = category,
                                description = notes,
                                address = address,
                                iconType = iconForCategory(category),
                                price = price,
                                visibility = visibility
                            )
                        )
                    )
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    private fun iconForCategory(category: String): ActivityIcon = when (category.lowercase()) {
        "food" -> ActivityIcon.FOOD
        "outdoors" -> ActivityIcon.OUTDOORS
        else -> ActivityIcon.DEFAULT
    }
}
