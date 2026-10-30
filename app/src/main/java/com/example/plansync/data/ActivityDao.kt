package com.example.plansync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities WHERE cachedFor = :uid ORDER BY position")
    suspend fun getActivities(uid: String): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE cachedFor = :uid AND id = :activityId")
    suspend fun getActivity(uid: String, activityId: String): ActivityEntity?

    @Query("SELECT * FROM activity_likes WHERE cachedFor = :uid")
    suspend fun getLikes(uid: String): List<ActivityLikeEntity>

    @Query("SELECT * FROM activity_likes WHERE cachedFor = :uid AND activityId = :activityId")
    suspend fun getLikes(uid: String, activityId: String): List<ActivityLikeEntity>

    @Query("SELECT * FROM activity_tags WHERE cachedFor = :uid")
    suspend fun getTags(uid: String): List<ActivityTagEntity>

    @Query("SELECT * FROM activity_tags WHERE cachedFor = :uid AND activityId = :activityId")
    suspend fun getTags(uid: String, activityId: String): List<ActivityTagEntity>

    @Query("SELECT MIN(position) FROM activities WHERE cachedFor = :uid")
    suspend fun firstPosition(uid: String): Int?

    @Upsert
    suspend fun upsertActivities(activities: List<ActivityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikes(likes: List<ActivityLikeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<ActivityTagEntity>)

    @Query("DELETE FROM activities WHERE cachedFor = :uid")
    suspend fun deleteActivities(uid: String)

    @Query("DELETE FROM activities WHERE cachedFor = :uid AND id = :activityId")
    suspend fun deleteActivity(uid: String, activityId: String)

    @Query("DELETE FROM activity_likes WHERE cachedFor = :uid AND activityId = :activityId")
    suspend fun deleteLikes(uid: String, activityId: String)

    @Query("DELETE FROM activity_tags WHERE cachedFor = :uid AND activityId = :activityId")
    suspend fun deleteTags(uid: String, activityId: String)

    @Transaction
    suspend fun replaceAll(
        uid: String,
        activities: List<ActivityEntity>,
        likes: List<ActivityLikeEntity>,
        tags: List<ActivityTagEntity>
    ) {
        deleteActivities(uid)
        upsertActivities(activities)
        insertLikes(likes)
        insertTags(tags)
    }

    @Transaction
    suspend fun save(activity: ActivityEntity, likes: List<ActivityLikeEntity>, tags: List<ActivityTagEntity>) {
        upsertActivities(listOf(activity))
        deleteLikes(activity.cachedFor, activity.id)
        deleteTags(activity.cachedFor, activity.id)
        insertLikes(likes)
        insertTags(tags)
    }
}
