package com.example.plansync.service

import android.content.Context
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.PendingActionRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.PendingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OfflineSyncService {

    private val pendingActions = PendingActionRepository()
    private val activityRepository = ActivityRepository()
    private lateinit var locationService: LocationService
    private val mutex = Mutex()

    fun start(context: Context, scope: CoroutineScope) {
        locationService = LocationService(context.applicationContext)
        scope.launch {
            NetworkMonitor.get(context).isOnline.filter { it }.collect { syncNow() }
        }
    }

    suspend fun saveActivityOffline(action: PendingAction.SaveActivity) {
        pendingActions.add(action)
        val existing = activityRepository.getCachedActivity(action.activityId)
        activityRepository.cacheActivity(
            Activity(
                id = action.activityId,
                name = action.name,
                category = action.tags.firstOrNull().orEmpty(),
                description = action.notes,
                address = action.address,
                iconType = existing?.iconType ?: ActivityIcon.DEFAULT,
                price = action.price,
                visibility = action.visibility,
                ownerId = existing?.ownerId ?: activityRepository.currentUserId.orEmpty(),
                likedBy = existing?.likedBy.orEmpty(),
                tags = action.tags,
                photoUrl = existing?.photoUrl.orEmpty(),
                lat = action.lat ?: existing?.lat,
                lng = action.lng ?: existing?.lng
            )
        )
    }

    suspend fun setLikedOffline(activity: Activity, liked: Boolean): Activity {
        pendingActions.add(PendingAction.SetLiked(activity.id, liked))
        val updated = activity.withLike(liked)
        activityRepository.cacheActivity(updated)
        return updated
    }

    suspend fun pendingActivityIds(): Set<String> =
        pendingActions.getAll().filterIsInstance<PendingAction.SaveActivity>().map { it.activityId }.toSet()

    suspend fun applyPending(activities: List<Activity>): List<Activity> {
        val actions = pendingActions.getAll()
        if (actions.isEmpty()) return activities

        val cached = activityRepository.getCachedActivities().associateBy { it.id }
        val result = LinkedHashMap(activities.associateBy { it.id })
        actions.forEach { action ->
            when (action) {
                is PendingAction.SaveActivity -> cached[action.activityId]?.let { result[it.id] = it }
                is PendingAction.SetLiked -> result[action.activityId]?.let { result[it.id] = it.withLike(action.liked) }
            }
        }
        return result.values.toList()
    }

    suspend fun syncNow() = mutex.withLock {
        for (action in pendingActions.getAll()) {
            if (!upload(action)) break
            pendingActions.remove(action.id)
        }
    }

    private suspend fun upload(action: PendingAction): Boolean = when (action) {
        is PendingAction.SaveActivity -> {
            val geocoded = if (action.lat == null) locationService.coordinatesFor(action.address) else null
            activityRepository.saveActivity(
                activityId = action.activityId,
                name = action.name,
                address = action.address,
                price = action.price,
                tags = action.tags,
                newCustomTags = action.newCustomTags,
                notes = action.notes,
                visibility = action.visibility,
                lat = action.lat ?: geocoded?.latitude,
                lng = action.lng ?: geocoded?.longitude,
                isNew = action.isNew
            ).isSuccess
        }
        is PendingAction.SetLiked -> activityRepository.setLiked(action.activityId, action.liked).isSuccess
    }

    private fun Activity.withLike(liked: Boolean): Activity {
        val uid = activityRepository.currentUserId ?: return this
        return copy(likedBy = if (liked) (likedBy + uid).distinct() else likedBy - uid)
    }
}
