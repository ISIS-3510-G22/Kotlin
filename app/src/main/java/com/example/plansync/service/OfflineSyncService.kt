package com.example.plansync.service

import android.content.Context
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.InviteRepository
import com.example.plansync.data.PendingActionRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.ProfileRepository
import com.example.plansync.data.WriteResult
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.PendingAction
import com.example.plansync.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OfflineSyncService {

    private val pendingActions = PendingActionRepository()
    private val activityRepository = ActivityRepository()
    private val profileRepository = ProfileRepository()
    private val planRepository = PlanRepository()
    private val inviteRepository = InviteRepository()
    private lateinit var locationService: LocationService
    private lateinit var appContext: Context
    private val mutex = Mutex()

    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
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

    suspend fun deleteActivityOffline(activity: Activity) {
        val related = pendingActions.getAll().filter {
            (it is PendingAction.SaveActivity && it.activityId == activity.id) ||
                (it is PendingAction.SetLiked && it.activityId == activity.id)
        }
        val neverUploaded = related.any { it is PendingAction.SaveActivity && it.isNew }
        related.forEach { pendingActions.remove(it.id) }
        if (!neverUploaded) pendingActions.add(PendingAction.DeleteActivity(activity.id, activity.photoUrl))
        activityRepository.removeCachedActivity(activity.id)
    }

    suspend fun createPlanOffline(action: PendingAction.CreatePlan) {
        pendingActions.add(action)
    }

    suspend fun enqueue(action: PendingAction) = pendingActions.add(action)

    suspend fun hasPendingActions(): Boolean = pendingActions.getAll().isNotEmpty()

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
                is PendingAction.DeleteActivity -> result.remove(action.activityId)
                else -> Unit
            }
        }
        return result.values.toList()
    }

    suspend fun applyPending(user: User): User = pendingActions.getAll().fold(user) { current, action ->
        when (action) {
            is PendingAction.UpdateProfile -> current.copy(name = action.name, lastName = action.lastName, phone = action.phone)
            is PendingAction.AddPaymentMethod -> current.copy(
                reimbursementMethods = (current.reimbursementMethods + action.method)
                    .mapIndexed { i, m -> m.copy(isPrimary = i == 0) }
            )
            is PendingAction.DeletePaymentMethod -> current.copy(
                reimbursementMethods = current.reimbursementMethods
                    .filter { it.id != action.method.id }
                    .mapIndexed { i, m -> m.copy(isPrimary = i == 0) }
            )
            else -> current
        }
    }

    suspend fun syncNow() = mutex.withLock {
        for (action in pendingActions.getAll()) {
            val ok = upload(action)
            if (ok) {
                pendingActions.remove(action.id)
            } else if (NetworkMonitor.get(appContext).isCurrentlyOnline()) {
                // Still online but write failed = permanent server rejection; drop it
                pendingActions.remove(action.id)
            } else {
                // Went offline mid-sync; stop and retry on next reconnect
                break
            }
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
        is PendingAction.DeleteActivity ->
            activityRepository.deleteActivity(action.activityId, action.photoUrl).isSuccess
        is PendingAction.UpdateProfile ->
            profileRepository.updateProfile(action.name, action.lastName, action.phone) == WriteResult.Committed
        is PendingAction.AddPaymentMethod ->
            profileRepository.addPaymentMethod(action.method) == WriteResult.Committed
        is PendingAction.DeletePaymentMethod ->
            profileRepository.deletePaymentMethod(action.method) == WriteResult.Committed
        is PendingAction.SetRsvp -> {
            val status = com.example.plansync.model.RsvpStatus.from(action.rsvp)
                ?: return false
            planRepository.setRsvp(action.planId, status).isSuccess
        }
        is PendingAction.SendInvite ->
            inviteRepository.inviteContact(action.planId, action.contactId).isSuccess
        is PendingAction.CreatePlan -> {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = action.dateMillis
                set(java.util.Calendar.HOUR_OF_DAY, action.hour)
                set(java.util.Calendar.MINUTE, action.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            planRepository.createPlan(
                planId      = action.planId,
                name        = action.name,
                date        = com.google.firebase.Timestamp(cal.time),
                isPublic    = action.isPublic,
                activityIds = action.activityIds,
                tags        = action.tags,
                origin      = action.origin,
                sourceId    = action.sourceId
            ).isSuccess
        }
    }

    private fun Activity.withLike(liked: Boolean): Activity {
        val uid = activityRepository.currentUserId ?: return this
        return copy(likedBy = if (liked) (likedBy + uid).distinct() else likedBy - uid)
    }
}
