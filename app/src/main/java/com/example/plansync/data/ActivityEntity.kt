package com.example.plansync.data

import androidx.room.Entity
import androidx.room.ForeignKey
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.ActivityVisibility

@Entity(tableName = "activities", primaryKeys = ["cachedFor", "id"])
data class ActivityEntity(
    val cachedFor: String,
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val address: String,
    val iconType: String,
    val price: Int,
    val visibility: String,
    val ownerId: String,
    val photoUrl: String,
    val lat: Double?,
    val lng: Double?,
    val position: Int
)

@Entity(
    tableName = "activity_likes",
    primaryKeys = ["cachedFor", "activityId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["cachedFor", "id"],
            childColumns = ["cachedFor", "activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActivityLikeEntity(
    val cachedFor: String,
    val activityId: String,
    val userId: String
)

@Entity(
    tableName = "activity_tags",
    primaryKeys = ["cachedFor", "activityId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["cachedFor", "id"],
            childColumns = ["cachedFor", "activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ActivityTagEntity(
    val cachedFor: String,
    val activityId: String,
    val tag: String,
    val position: Int
)

internal fun Activity.toEntity(uid: String, position: Int) = ActivityEntity(
    cachedFor = uid,
    id = id,
    name = name,
    category = category,
    description = description,
    address = address,
    iconType = iconType.name,
    price = price,
    visibility = visibility.name,
    ownerId = ownerId,
    photoUrl = photoUrl,
    lat = lat,
    lng = lng,
    position = position
)

internal fun Activity.toLikeEntities(uid: String) = likedBy.distinct().map { ActivityLikeEntity(uid, id, it) }

internal fun Activity.toTagEntities(uid: String) =
    tags.distinct().mapIndexed { i, tag -> ActivityTagEntity(uid, id, tag, i) }

internal fun ActivityEntity.toActivity(likes: List<ActivityLikeEntity>, tags: List<ActivityTagEntity>) = Activity(
    id = id,
    name = name,
    category = category,
    description = description,
    address = address,
    iconType = ActivityIcon.valueOf(iconType),
    price = price,
    visibility = ActivityVisibility.valueOf(visibility),
    ownerId = ownerId,
    likedBy = likes.map { it.userId },
    tags = tags.sortedBy { it.position }.map { it.tag },
    photoUrl = photoUrl,
    lat = lat,
    lng = lng
)
