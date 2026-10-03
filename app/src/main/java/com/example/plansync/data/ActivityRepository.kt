package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import android.net.Uri
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.ActivityVisibility
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.SetOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

class ActivityRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth
) {

    private val activities = db.collection("activities")
    private val recommendationRuns = db.collection("transferConfigs")
        .document("6ad7a48c-0000-2678-8aba-fc4116908b71")
        .collection("runs")

    val currentUserId: String? get() = auth.currentUser?.uid

    suspend fun getActivities(): Result<List<Activity>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            activities
                .where(
                    Filter.or(
                        Filter.equalTo("visibility", "public"),
                        Filter.equalTo("ownerId", uid),
                        Filter.arrayContains("likedBy", uid)
                    )
                )
                .get()
                .addOnSuccessListener { snapshot ->
                    continuation.resume(Result.success(snapshot.documents.map { it.toActivity() }))
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getActivity(activityId: String): Result<Activity> =
        suspendCancellableCoroutine { continuation ->
            activities.document(activityId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        continuation.resume(Result.success(doc.toActivity()))
                    } else {
                        continuation.resume(Result.failure(Exception("Activity not found.")))
                    }
                }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }

    suspend fun getRecommended(): Result<List<Activity>> = runCatching {
        val uid = auth.currentUser?.uid ?: throw Exception("No active session.")
        val runId = recommendationRuns.document("latest").get().await().getString("latestRunId")
            ?: return@runCatching emptyList()
        val output = recommendationRuns.document(runId).collection("output")
            .whereEqualTo("uid", uid)
            .limit(1)
            .get()
            .await()
        val idsMap = output.documents.firstOrNull()?.get("activity_ids") as? Map<*, *>
            ?: return@runCatching emptyList()
        val ids = List(idsMap.size) { i -> idsMap[i.toString()] as String }
        if (ids.isEmpty()) return@runCatching emptyList()
        activities.whereIn(FieldPath.documentId(), ids).get().await()
            .documents.map { it.toActivity() }
            .sortedBy { ids.indexOf(it.id) }
    }

    suspend fun setLiked(activityId: String, liked: Boolean): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val change = if (liked) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)

        return suspendCancellableCoroutine { continuation ->
            activities.document(activityId).update("likedBy", change)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun updatePhoto(activityId: String, uri: Uri): Result<String> {
        val photoUrl = StorageRepository().uploadImage("activity_photos/$activityId.jpg", uri)
            .getOrElse { return Result.failure(it) }

        return suspendCancellableCoroutine { continuation ->
            activities.document(activityId).update("photoUrl", photoUrl)
                .addOnSuccessListener { continuation.resume(Result.success(photoUrl)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun saveActivity(
        activityId: String?,
        name: String,
        address: String,
        price: Int,
        tags: List<String>,
        newCustomTags: List<String>,
        notes: String,
        visibility: ActivityVisibility,
        lat: Double?,
        lng: Double?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))
        val batch = db.batch()
        val data = mutableMapOf<String, Any>(
            "name" to name,
            "address" to address,
            "expectedPrice" to price.toDouble(),
            "notes" to notes,
            "visibility" to visibility.name.lowercase(),
            "tags" to tags
        )
        if (lat != null && lng != null) {
            data["lat"] = lat
            data["lng"] = lng
        }
        if (activityId == null) {
            data["ownerId"] = uid
            data["likedBy"] = emptyList<String>()
        }
        val doc = if (activityId == null) activities.document() else activities.document(activityId)
        batch.set(doc, data, SetOptions.merge())
        newCustomTags.forEach { tag ->
            batch.set(
                db.collection("tags").document(tag),
                mapOf("name" to tag, "count" to FieldValue.increment(1)),
                SetOptions.merge()
            )
        }

        return suspendCancellableCoroutine { continuation ->
            batch.commit()
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    private fun DocumentSnapshot.toActivity(): Activity {
        val tags = (get("tags") as? List<*>).orEmpty().filterIsInstance<String>()
        return Activity(
            id = id,
            name = getString("name").orEmpty(),
            category = tags.firstOrNull().orEmpty(),
            description = getString("notes").orEmpty(),
            address = getString("address").orEmpty(),
            iconType = iconForCategory(tags.firstOrNull().orEmpty()),
            price = (getDouble("expectedPrice") ?: 0.0).toInt(),
            visibility = if (getString("visibility") == "public") ActivityVisibility.PUBLIC else ActivityVisibility.PRIVATE,
            ownerId = getString("ownerId").orEmpty(),
            likedBy = (get("likedBy") as? List<*>).orEmpty().filterIsInstance<String>(),
            tags = tags,
            photoUrl = getString("photoUrl").orEmpty(),
            lat = getDouble("lat"),
            lng = getDouble("lng")
        )
    }

    private fun iconForCategory(category: String): ActivityIcon = when (category.lowercase()) {
        "food" -> ActivityIcon.FOOD
        "outdoors" -> ActivityIcon.OUTDOORS
        else -> ActivityIcon.DEFAULT
    }
}
