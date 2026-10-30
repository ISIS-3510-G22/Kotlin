package com.example.plansync.service

import android.content.Context
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.PendingActionRepository
import com.example.plansync.model.Activity
import com.example.plansync.model.PendingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OfflineSyncService {

    private val pendingActions = PendingActionRepository()
    private val activityRepository = ActivityRepository()
    private val mutex = Mutex()

    fun start(context: Context, scope: CoroutineScope) {
        scope.launch {
            NetworkMonitor.get(context).isOnline.filter { it }.collect { syncNow() }
        }
    }

    suspend fun setLikedOffline(activity: Activity, liked: Boolean): Activity {
        pendingActions.add(PendingAction.SetLiked(activity.id, liked))
        val updated = activity.withLike(liked)
        activityRepository.cacheActivity(updated)
        return updated
    }

    suspend fun applyPending(activities: List<Activity>): List<Activity> {
        val actions = pendingActions.getAll()
        if (actions.isEmpty()) return activities

        val result = LinkedHashMap(activities.associateBy { it.id })
        actions.forEach { action ->
            when (action) {
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
        is PendingAction.SetLiked -> activityRepository.setLiked(action.activityId, action.liked).isSuccess
    }

    private fun Activity.withLike(liked: Boolean): Activity {
        val uid = activityRepository.currentUserId ?: return this
        return copy(likedBy = if (liked) (likedBy + uid).distinct() else likedBy - uid)
    }
}
