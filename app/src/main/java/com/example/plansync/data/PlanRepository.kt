package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.example.plansync.model.Activity
import com.example.plansync.model.ActivityIcon
import com.example.plansync.model.Invitation
import com.example.plansync.model.Participant
import com.example.plansync.model.Plan
import com.example.plansync.model.PlanStatus
import com.example.plansync.model.RsvpStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class PlanRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val auth: FirebaseAuth = FirebaseProvider.auth,
    private val planDao: PlanDao? = null
) {


    /**
     * Data layer — creates a new plan document in Firestore.
     * Matches the Flutter schema: name, date, isPublic, creatorId,
     * participantsIds, activityIds, tags, invitations.
     */
    suspend fun createPlan(
        planId: String = UUID.randomUUID().toString(),
        name: String,
        date: Timestamp,
        isPublic: Boolean,
        activityIds: List<String>,
        tags: List<String>,
        origin: String? = null,
        sourceId: String? = null
    ): Result<String> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            val data = hashMapOf<String, Any?>(
                "name"             to name,
                "date"             to date,
                "isPublic"         to isPublic,
                "creatorId"        to uid,
                "participantsIds"  to listOf(uid),
                "activityIds"      to activityIds,
                "tags"             to tags,
                "invitations"      to listOf(mapOf("userId" to uid, "rsvp" to "going"))
            ).apply {
                origin?.let   { put("origin", it) }
                sourceId?.let { put("sourceId", it) }
            }
            db.collection("plans").document(planId).set(data)
                .addOnSuccessListener { continuation.resume(Result.success(planId)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    suspend fun getPlan(planId: String): Result<Plan> {
        // IO: force server fetch, skip Firestore's local cache; 8s timeout then fall back to Room
        val planDoc: DocumentSnapshot? = try {
            withTimeout(8_000) {
                withContext(Dispatchers.IO) {
                    db.collection("plans").document(planId).get(Source.SERVER).await()
                }
            }
        } catch (_: Exception) { null }

        if (planDoc == null || !planDoc.exists()) {
            return roomFallback(planId)
        }

        val activityIds = (planDoc.get("activityIds") as? List<*>).orEmpty().filterIsInstance<String>()
        // Fetch all activities in parallel instead of one-by-one.
        val activities = coroutineScope {
            activityIds.map { actId -> async(Dispatchers.IO) { fetchActivity(actId) } }.awaitAll()
        }.filterNotNull()

        val uid = auth.currentUser?.uid
        val planTags = (planDoc.get("tags") as? List<*>).orEmpty().filterIsInstance<String>()
        val invitations = parseInvitations(planDoc)
        val planDate = planDoc.getTimestamp("date")?.toDate()
        val status = when {
            uid != null && invitations.find { it.userId == uid }?.rsvp == RsvpStatus.INVITED -> PlanStatus.PENDING_INVITE
            planDate != null && planDate.before(Date()) -> PlanStatus.PAST
            else -> PlanStatus.CONFIRMED
        }
        val goingIds = invitations.filter { it.rsvp == RsvpStatus.GOING }.map { it.userId }
        val participants = fetchGoingParticipants(goingIds)
        // CPU-bound aggregation runs on the computation thread pool.
        val costPerPerson = withContext(Dispatchers.Default) {
            val total = activities.sumOf { it.price.toDouble() }
            if (goingIds.isNotEmpty() && total > 0) (total / goingIds.size).toInt() else 0
        }
        val plan = Plan(
            id = planDoc.id,
            title = planDoc.getString("name").orEmpty(),
            date = planDate
                ?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it) }
                .orEmpty(),
            estimatedCostPerPerson = costPerPerson,
            participants = participants,
            activities = activities,
            status = status,
            activityIds = activityIds,
            category = planTags.firstOrNull().orEmpty(),
            invitations = invitations
        )

        // IO: write to Room so offline opens can serve cached data
        planDao?.let { dao ->
            withContext(Dispatchers.IO) {
                dao.upsertPlan(plan.toEntity())
                dao.deleteActivities(planDoc.id)
                dao.upsertActivities(activities.map { it.toEntity(planDoc.id) })
                dao.deleteParticipants(planDoc.id)
                dao.upsertParticipants(participants.map { it.toEntity(planDoc.id) })
            }
        }

        return Result.success(plan)
    }

    private suspend fun roomFallback(planId: String): Result<Plan> {
        val dao = planDao ?: return Result.failure(Exception("connect_to_load"))
        val entity = withContext(Dispatchers.IO) { dao.getPlan(planId) }
            ?: return Result.failure(Exception("connect_to_load"))
        val activities = withContext(Dispatchers.IO) { dao.getActivities(planId) }.map { it.toActivity() }
        val participants = withContext(Dispatchers.IO) { dao.getParticipants(planId) }.map { it.toParticipant() }
        return Result.success(entity.toPlan(activities, participants))
    }

    private suspend fun fetchActivity(activityId: String): Activity? =
        suspendCancellableCoroutine { continuation ->
            db.collection("activities").document(activityId).get(Source.SERVER)
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) { continuation.resume(null); return@addOnSuccessListener }
                    val actTags = (doc.get("tags") as? List<*>).orEmpty().filterIsInstance<String>()
                    continuation.resume(
                        Activity(
                            id = doc.id,
                            name = doc.getString("name").orEmpty(),
                            category = actTags.firstOrNull().orEmpty(),
                            description = doc.getString("notes").orEmpty(),
                            address = doc.getString("address").orEmpty(),
                            iconType = ActivityIcon.DEFAULT,
                            price = (doc.getDouble("expectedPrice") ?: 0.0).toInt(),
                            tags = actTags
                        )
                    )
                }
                .addOnFailureListener { continuation.resume(null) }
        }

    suspend fun getPlans(): Result<List<Plan>> {
        delay(800)

        val saturdayInBrooklyn = Plan(
            id = "plan-001",
            title = "Saturday in Brooklyn",
            date = "Oct 28, 2023",
            estimatedCostPerPerson = 85,
            participants = listOf(
                Participant(id = "u1", initials = "JD", avatarColorIndex = 0),
                Participant(id = "u2", initials = "SA", avatarColorIndex = 1),
                Participant(id = "u3", initials = "MK", avatarColorIndex = 2),
                Participant(id = "u4", initials = "BL", avatarColorIndex = 3)
            ),
            status = PlanStatus.CONFIRMED,
            activities = listOf(
                Activity(
                    id = "a1",
                    name = "Devoción",
                    category = "Coffee",
                    description = "Grab morning coffee in Williamsburg. Known for their lush interior and fresh Colombian beans.",
                    address = "69 Grand St, Brooklyn",
                    iconType = ActivityIcon.COFFEE
                ),
                Activity(
                    id = "a2",
                    name = "Domino Park",
                    category = "Outdoors",
                    description = "Walk along the waterfront. Great views of the Manhattan skyline and the Williamsburg Bridge.",
                    address = "15 River St, Brooklyn",
                    iconType = ActivityIcon.OUTDOORS
                ),
                Activity(
                    id = "a3",
                    name = "Juliana's Pizza",
                    category = "Food",
                    description = "Legendary coal-fired pizza under the Brooklyn Bridge. Arrive early to beat the line.",
                    address = "19 Old Fulton St, Brooklyn",
                    iconType = ActivityIcon.FOOD
                ),
                Activity(
                    id = "a4",
                    name = "Brooklyn Bowl",
                    category = "Music",
                    description = "End the night with live music and bowling in a legendary Williamsburg venue.",
                    address = "61 Wythe Ave, Brooklyn",
                    iconType = ActivityIcon.MUSIC
                )
            )
        )

        val techConferenceSf = Plan(
            id = "plan-002",
            title = "Tech Conference SF",
            date = "Nov 2, 2023",
            estimatedCostPerPerson = 350,
            participants = emptyList(),
            status = PlanStatus.CONFIRMED,
            activities = listOf(
                Activity(
                    id = "b1",
                    name = "Opening Keynote",
                    category = "Conference",
                    description = "Kickoff keynote covering this year's biggest announcements.",
                    address = "Moscone Center, San Francisco",
                    iconType = ActivityIcon.DEFAULT
                ),
                Activity(
                    id = "b2",
                    name = "Networking Mixer",
                    category = "Conference",
                    description = "Evening mixer with drinks and appetizers for attendees.",
                    address = "Moscone Center, San Francisco",
                    iconType = ActivityIcon.DEFAULT
                )
            )
        )

        val wineTastingInBogota = Plan(
            id = "plan-003",
            title = "Wine tasting in Bogota",
            date = "Oct 28, 2023 · 10:00 PM",
            estimatedCostPerPerson = 60,
            participants = listOf(
                Participant(id = "u5", initials = "AN", avatarColorIndex = 0),
                Participant(id = "u6", initials = "BR", avatarColorIndex = 1),
                Participant(id = "u7", initials = "CL", avatarColorIndex = 2),
                Participant(id = "u8", initials = "DI", avatarColorIndex = 3)
            ),
            status = PlanStatus.PENDING_INVITE,
            activities = listOf(
                Activity(
                    id = "c1",
                    name = "Vineyard Tasting Room",
                    category = "Wine",
                    description = "Guided tasting of Colombian and imported wines.",
                    address = "Zona T, Bogota",
                    iconType = ActivityIcon.DEFAULT
                )
            )
        )

        val rooftopBrunch = Plan(
            id = "plan-004",
            title = "Rooftop Brunch",
            date = "Jun 28, 2023 · 10:00 PM",
            estimatedCostPerPerson = 40,
            participants = listOf(
                Participant(id = "u9", initials = "RB", avatarColorIndex = 0),
                Participant(id = "u10", initials = "JD", avatarColorIndex = 1)
            ),
            status = PlanStatus.PAST,
            activities = listOf(
                Activity(
                    id = "d1",
                    name = "Rooftop Brunch",
                    category = "Food",
                    description = "Brunch with a skyline view.",
                    address = "Rooftop Lounge",
                    iconType = ActivityIcon.FOOD
                )
            )
        )

        return Result.success(
            listOf(saturdayInBrooklyn, techConferenceSf, wineTastingInBogota, rooftopBrunch)
        )
    }

    suspend fun getMyPlans(): Result<List<Plan>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        val docs = suspendCancellableCoroutine<Result<List<DocumentSnapshot>>> { continuation ->
            db.collection("plans")
                .whereArrayContains("participantsIds", uid)
                .get()
                .addOnSuccessListener { snapshot -> continuation.resume(Result.success(snapshot.documents)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }.getOrElse { return Result.failure(it) }

        val allGoingIds = docs.flatMap { doc ->
            parseInvitations(doc).filter { it.rsvp == RsvpStatus.GOING }.map { it.userId }
        }.distinct()
        val users = fetchUsersByIds(allGoingIds).getOrDefault(emptyMap())

        val plans = docs.mapNotNull { doc ->
            val dateTime = doc.getTimestamp("date")?.toDate()
            val invitations = parseInvitations(doc)
            val myRsvp = invitations.find { it.userId == uid }?.rsvp
            val isPast = dateTime != null && dateTime.before(Date())
            val status = when {
                isPast && myRsvp == RsvpStatus.GOING -> PlanStatus.PAST
                isPast -> return@mapNotNull null
                myRsvp == RsvpStatus.INVITED -> PlanStatus.PENDING_INVITE
                else -> PlanStatus.CONFIRMED
            }
            val goingIds = invitations.filter { it.rsvp == RsvpStatus.GOING }.map { it.userId }
            val participants = goingIds.mapIndexed { i, id ->
                val user = users[id]
                Participant(
                    id = id,
                    initials = if (user == null) "?" else buildInitials(user.getString("name").orEmpty(), user.getString("lastName").orEmpty()),
                    avatarColorIndex = i % 4,
                    photoUrl = user?.getString("photoUrl").orEmpty()
                )
            }
            Plan(
                id = doc.id,
                title = doc.getString("name").orEmpty(),
                date = dateTime
                    ?.let { SimpleDateFormat("MMM d", Locale.US).format(it) }
                    .orEmpty(),
                estimatedCostPerPerson = 0,
                participants = participants,
                activities = emptyList(),
                status = status,
                activityIds = (doc.get("activityIds") as? List<*>).orEmpty().filterIsInstance<String>(),
                dateTime = dateTime,
                invitations = invitations
            )
        }
        return Result.success(plans)
    }

    private suspend fun fetchUsersByIds(ids: List<String>): Result<Map<String, DocumentSnapshot>> {
        val users = mutableMapOf<String, DocumentSnapshot>()
        for (chunk in ids.chunked(10)) {
            val docs = suspendCancellableCoroutine<Result<List<DocumentSnapshot>>> { continuation ->
                db.collection("users")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .addOnSuccessListener { snapshot -> continuation.resume(Result.success(snapshot.documents)) }
                    .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
            }.getOrElse { return Result.failure(it) }
            for (doc in docs) {
                users[doc.id] = doc
            }
        }
        return Result.success(users)
    }

    /**
     * Tolerant parser: maps → parsed normally, plain strings → INVITED with null invitedAt,
     * unknown rsvp values or malformed entries are skipped.
     */
    private fun parseInvitations(doc: DocumentSnapshot): List<Invitation> {
        val raw = (doc.get("invitations") as? List<*>).orEmpty()
        return raw.mapNotNull { entry ->
            when {
                entry is Map<*, *> -> {
                    val userId = entry["userId"] as? String ?: return@mapNotNull null
                    val rsvp = RsvpStatus.from(entry["rsvp"] as? String) ?: return@mapNotNull null
                    val invitedAt = (entry["invitedAt"] as? Timestamp)?.toDate()
                    Invitation(userId = userId, rsvp = rsvp, invitedAt = invitedAt)
                }
                entry is String -> Invitation(userId = entry, rsvp = RsvpStatus.INVITED, invitedAt = null)
                else -> null
            }
        }
    }

    /** Mirrors Flutter's setRsvp — rewrites this user's entry in the invitations array. */
    suspend fun setRsvp(planId: String, status: RsvpStatus): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(Exception("No active session."))

        return suspendCancellableCoroutine { continuation ->
            val ref = db.collection("plans").document(planId)
            db.runTransaction { transaction ->
                val doc = transaction.get(ref)
                val raw = (doc.get("invitations") as? List<*>).orEmpty()
                val hasEntry = raw.any { e ->
                    (e is Map<*, *> && e["userId"] == uid) || (e is String && e == uid)
                }
                val updated = if (hasEntry) {
                    raw.map { entry ->
                        when {
                            entry is Map<*, *> && entry["userId"] == uid ->
                                hashMapOf("userId" to uid, "rsvp" to status.value,
                                    "invitedAt" to entry["invitedAt"])
                            entry is String && entry == uid ->
                                hashMapOf("userId" to uid, "rsvp" to status.value)
                            else -> entry
                        }
                    }
                } else {
                    raw + hashMapOf("userId" to uid, "rsvp" to status.value)
                }
                transaction.update(ref, "invitations", updated)
            }
            .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
            .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

    private suspend fun fetchGoingParticipants(goingIds: List<String>): List<Participant> {
        if (goingIds.isEmpty()) return emptyList()
        val participants = mutableListOf<Participant>()
        val uncachedIds = mutableListOf<String>()

        // LRU cache check — serve warm profiles without a Firestore round-trip
        goingIds.forEach { uid ->
            val cached = UserProfileCache.get(uid)
            if (cached != null) participants.add(cached) else uncachedIds.add(uid)
        }

        uncachedIds.chunked(10).forEach { batch ->
            suspendCancellableCoroutine<Unit> { cont ->
                db.collection("users")
                    .whereIn(FieldPath.documentId(), batch)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        snapshot.documents.forEach { doc ->
                            val name = doc.getString("name").orEmpty()
                            val lastName = doc.getString("lastName").orEmpty()
                            val p = Participant(
                                id = doc.id,
                                initials = buildInitials(name, lastName),
                                avatarColorIndex = participants.size % 4,
                                photoUrl = doc.getString("photoUrl").orEmpty()
                            )
                            UserProfileCache.put(doc.id, p)
                            participants.add(p)
                        }
                        cont.resume(Unit)
                    }
                    .addOnFailureListener {
                        batch.forEach { id ->
                            participants.add(Participant(id = id, initials = "?", avatarColorIndex = participants.size % 4))
                        }
                        cont.resume(Unit)
                    }
            }
        }
        return participants
    }

    private fun buildInitials(name: String, lastName: String): String {
        val f = name.trim().firstOrNull()?.uppercaseChar() ?: ' '
        val l = lastName.trim().firstOrNull()?.uppercaseChar() ?: ' '
        return "$f$l".trim().ifBlank { "?" }
    }

    suspend fun addActivityToPlan(planId: String, activityId: String, tags: List<String>): Result<Unit> {
        val changes = mutableMapOf<String, Any>("activityIds" to FieldValue.arrayUnion(activityId))
        if (tags.isNotEmpty()) changes["tags"] = FieldValue.arrayUnion(*tags.toTypedArray())

        return suspendCancellableCoroutine { continuation ->
            db.collection("plans").document(planId)
                .update(changes)
                .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                .addOnFailureListener { e -> continuation.resume(Result.failure(e)) }
        }
    }

}
