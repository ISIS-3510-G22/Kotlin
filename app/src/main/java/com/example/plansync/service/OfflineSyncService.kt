package com.example.plansync.service

import android.content.Context
import com.example.plansync.data.ActivityRepository
import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.data.InviteRepository
import com.example.plansync.data.PendingActionRepository
import com.example.plansync.data.PlanRepository
import com.example.plansync.data.PlanSnapshotRepository
import com.example.plansync.data.ProfileRepository
import com.example.plansync.data.SplitRepository
import com.example.plansync.data.WriteResult
import com.example.plansync.data.planDateTime
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Expense
import com.example.plansync.model.PendingAction
import com.example.plansync.model.Plan
import com.example.plansync.model.Settlement
import com.example.plansync.model.User
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object OfflineSyncService {

    private val pendingActions = PendingActionRepository()
    private val activityRepository = ActivityRepository()
    private val analyticsRepository = AnalyticsRepository()
    private val profileRepository = ProfileRepository()
    private val planRepository = PlanRepository()
    private val inviteRepository = InviteRepository()
    private val splitRepository = SplitRepository()
    private val planSnapshots = PlanSnapshotRepository()
    private lateinit var locationService: LocationService
    private lateinit var appContext: Context
    private val mutex = Mutex()

    private val _syncErrors = Channel<String>(Channel.BUFFERED)
    val syncErrors: Flow<String> = _syncErrors.receiveAsFlow()

    private val _queuedNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val queuedNotices: SharedFlow<String> = _queuedNotices

    private val _queuedCount = MutableStateFlow(0)
    val queuedCount: StateFlow<Int> = _queuedCount.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        locationService = LocationService(context.applicationContext)
        scope.launch {
            _queuedCount.value = pendingActions.getAll().size
            val nm = NetworkMonitor.get(context)
            _isOnline.value = nm.isCurrentlyOnline()
            nm.isOnline.collect { online ->
                _isOnline.value = online
                if (online) {
                    syncNow()
                    _queuedCount.value = pendingActions.getAll().size
                }
            }
        }
    }

    fun notifyQueued(label: String) {
        _queuedCount.update { it + 1 }
        _queuedNotices.tryEmit("$label has been queued and will be sent when you're back online.")
    }

    private suspend fun queue(action: PendingAction) {
        pendingActions.add(action)
        if (!NetworkMonitor.get(appContext).isCurrentlyOnline()) {
            notifyQueued(queuedLabel(action))
        }
    }

    private fun queuedLabel(action: PendingAction): String = when (action) {
        is PendingAction.CreatePlan          -> "Your plan \"${action.name}\""
        is PendingAction.SaveActivity        -> "Your activity \"${action.name}\""
        is PendingAction.SetLiked            -> if (action.liked) "Your like" else "Your unlike"
        is PendingAction.DeleteActivity      -> "Your activity deletion"
        is PendingAction.UpdateProfile       -> "Your profile update"
        is PendingAction.AddPaymentMethod    -> "Your new payment method"
        is PendingAction.DeletePaymentMethod -> "Your payment method removal"
        is PendingAction.SetRsvp             -> "Your RSVP"
        is PendingAction.SendInvite          -> "Your invite"
        is PendingAction.AddExpense          -> "Your expense \"${action.name}\""
        is PendingAction.UpdateExpense       -> "Your changes to \"${action.name}\""
        is PendingAction.DeleteExpense       -> "Your expense deletion"
        is PendingAction.AddSettlement       -> "Your payment"
        is PendingAction.DeleteMySettlementsTo -> "Your payment change"
        is PendingAction.AddActivityToPlan   -> "Adding \"${action.activityName}\" to your plan"
    }

    suspend fun saveActivityOffline(action: PendingAction.SaveActivity) {
        queue(action)
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
        queue(PendingAction.SetLiked(activity.id, liked))
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
        if (!neverUploaded) queue(PendingAction.DeleteActivity(activity.id, activity.photoUrl))
        activityRepository.removeCachedActivity(activity.id)
    }

    suspend fun createPlanOffline(action: PendingAction.CreatePlan) {
        queue(action)
    }

    suspend fun enqueue(action: PendingAction) = queue(action)

    suspend fun addActivityToPlanOffline(action: PendingAction.AddActivityToPlan) {
        queue(action)
        val plans = planSnapshots.getPlans().getOrNull() ?: return
        planSnapshots.savePlans(plans.map { it.withActivity(action.planId, action.activityId) })
        // Also write to Room so Plan Detail shows the activity immediately offline.
        val entity = com.example.plansync.data.PlanActivityEntity(
            planId      = action.planId,
            id          = action.activityId,
            name        = action.activityName,
            category    = action.activityCategory,
            description = action.activityDescription,
            address     = action.activityAddress,
            iconType    = action.activityIconType,
            price       = action.activityPrice
        )
        com.example.plansync.data.PlanDatabase.get(appContext).planDao().upsertActivities(listOf(entity))
    }

    suspend fun loadPlansForAddToPlan(online: Boolean): Result<List<Plan>> {
        val plans = if (online) planRepository.getMyPlans() else planSnapshots.getPlans()
        val pending = pendingActions.getAll().filterIsInstance<PendingAction.AddActivityToPlan>()
        return plans.map { list ->
            pending.fold(list) { acc, action -> acc.map { it.withActivity(action.planId, action.activityId) } }
        }
    }

    private fun Plan.withActivity(planId: String, activityId: String): Plan =
        if (id == planId && activityId !in activityIds) copy(activityIds = activityIds + activityId) else this

    fun isOnlineNow(): Boolean = NetworkMonitor.get(appContext).isCurrentlyOnline()

    suspend fun deleteExpenseOffline(planId: String, expenseId: String) {
        val related = pendingActions.getAll().filter {
            (it is PendingAction.AddExpense && it.expenseId == expenseId) ||
                (it is PendingAction.UpdateExpense && it.expenseId == expenseId)
        }
        val neverUploaded = related.any { it is PendingAction.AddExpense }
        related.forEach { pendingActions.remove(it.id) }
        if (!neverUploaded) queue(PendingAction.DeleteExpense(planId, expenseId))
    }

    suspend fun loadExpenses(planId: String): Result<List<Expense>> {
        val actions = pendingActions.getAll()
        val base = splitRepository.getExpenses(planId)
        val hasPendingAdds = actions.any { it is PendingAction.AddExpense && it.planId == planId }
        val expenses = base.getOrElse { if (hasPendingAdds) emptyList() else return base }

        val result = LinkedHashMap(expenses.associateBy { it.id })
        actions.forEach { action ->
            when (action) {
                is PendingAction.AddExpense -> if (action.planId == planId) {
                    result[action.expenseId] = Expense(
                        id = action.expenseId,
                        name = action.name,
                        value = action.value,
                        paidById = action.paidById,
                        splitAmongIds = action.splitAmongIds,
                        createdAt = Date(action.createdAtMillis)
                    )
                }
                is PendingAction.UpdateExpense -> if (action.planId == planId) {
                    result[action.expenseId]?.let {
                        val updated = it.copy(name = action.name, value = action.value, paidById = action.paidById)
                        result[it.id] = if (action.resetSplit) updated.copy(splitMethod = "equal", customAmounts = emptyMap()) else updated
                    }
                }
                is PendingAction.DeleteExpense -> if (action.planId == planId) result.remove(action.expenseId)
                else -> Unit
            }
        }
        return Result.success(result.values.toList())
    }

    suspend fun loadSettlements(planId: String): Result<List<Settlement>> {
        val actions = pendingActions.getAll()
        val base = splitRepository.getSettlements(planId)
        val hasPending = actions.any { it is PendingAction.AddSettlement && it.planId == planId }
        val settlements = base.getOrElse { if (hasPending) emptyList() else return base }.toMutableList()

        actions.forEach { action ->
            when (action) {
                is PendingAction.AddSettlement -> if (action.planId == planId) {
                    settlements.add(Settlement(action.id, action.fromId, action.toId, action.amount))
                }
                is PendingAction.DeleteMySettlementsTo -> if (action.planId == planId) {
                    settlements.removeAll { it.fromId == action.fromId && it.toId == action.toId }
                }
                else -> Unit
            }
        }
        return Result.success(settlements)
    }

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
                _syncErrors.trySend("Sync failed: ${actionLabel(action)} was rejected by the server.")
                pendingActions.remove(action.id)
            } else {
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
        is PendingAction.AddExpense -> {
            val result = splitRepository.addExpense(
                planId = action.planId,
                expenseId = action.expenseId,
                name = action.name,
                value = action.value,
                paidById = action.paidById,
                splitAmongIds = action.splitAmongIds,
                createdAt = Date(action.createdAtMillis)
            )
            if (result.isSuccess) analyticsRepository.logEvent(AnalyticsRepository.EXPENSE_ADDED, action.planId)
            result.isSuccess
        }
        is PendingAction.UpdateExpense ->
            splitRepository.updateExpense(action.planId, action.expenseId, action.name, action.value, action.paidById, action.resetSplit).isSuccess
        is PendingAction.DeleteExpense ->
            splitRepository.deleteExpense(action.planId, action.expenseId).isSuccess
        is PendingAction.AddSettlement ->
            splitRepository.addSettlement(action.planId, action.toId, action.amount).isSuccess
        is PendingAction.DeleteMySettlementsTo ->
            splitRepository.deleteMySettlementsTo(action.planId, action.toId).isSuccess
        is PendingAction.AddActivityToPlan ->
            planRepository.addActivityToPlan(action.planId, action.activityId, action.tags).isSuccess
        is PendingAction.CreatePlan -> {
            val date = planDateTime(action.dateMillis, action.hour, action.minute)
            val result = planRepository.createPlan(
                planId      = action.planId,
                name        = action.name,
                date        = com.google.firebase.Timestamp(date),
                isPublic    = action.isPublic,
                activityIds = action.activityIds,
                tags        = action.tags,
                origin      = action.origin,
                sourceId    = action.sourceId
            )
            if (result.isSuccess) {
                analyticsRepository.logEvent(
                    type          = AnalyticsRepository.PLAN_CREATED,
                    planId        = action.planId,
                    origin        = action.origin,
                    sourceId      = action.sourceId
                )
            }
            result.isSuccess
        }
    }

    private fun actionLabel(action: PendingAction): String = when (action) {
        is PendingAction.CreatePlan   -> "Plan \"${action.name}\""
        is PendingAction.SendInvite   -> "Invite for plan ${action.planId}"
        is PendingAction.SetRsvp      -> "RSVP for plan ${action.planId}"
        is PendingAction.SaveActivity -> "Activity \"${action.name}\""
        is PendingAction.AddExpense   -> "Expense \"${action.name}\""
        is PendingAction.AddSettlement -> "Payment"
        is PendingAction.AddActivityToPlan -> "Activity \"${action.activityName}\" in plan"
        else                          -> "queued action"
    }

    private fun Activity.withLike(liked: Boolean): Activity {
        val uid = activityRepository.currentUserId ?: return this
        return copy(likedBy = if (liked) (likedBy + uid).distinct() else likedBy - uid)
    }
}
