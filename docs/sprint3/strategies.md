# Sprint 3 — Connectivity & Resilience Strategies

## PlanSync Android (Kotlin)

---

## 1. Eventual Connectivity

**What**: App continues to work and accept user input when there is no network.

**How**: Firestore SDK offline persistence (`PersistentCacheSettings`, enabled in `MainActivity`). All Firestore reads and writes work transparently offline; the SDK queues writes and resolves them when connectivity returns.

**Files**:
- `MainActivity.kt` — enables offline persistence on app start
- `service/NetworkMonitor.kt` — exposes `Flow<Boolean>` for reactive UI
- `data/WriteResult.kt` — sealed class returned by repos to signal Committed / Queued / Failed
- `ui/OfflineBanner.kt` — animated red banner shown when `isOnline == false`

---

## 2. Concurrency

**What**: Heavy data fetching does not block the main thread or create redundant serial calls.

**How**: `async/awaitAll` on `Dispatchers.IO` to fetch plan activities in parallel inside `PlanRepository`. Each activity document is fetched concurrently instead of sequentially.

**Files**:
- `data/PlanRepository.kt` — `fetchActivitiesParallel()` replaces sequential loop

---

## 3. Local Storage — Draft Persistence

**What**: If the user starts filling in Create Plan and navigates away (or the app is killed), their draft is not lost.

**How**: Jetpack DataStore (Preferences) writes each field change as the user types. On re-entry the form restores from the saved draft.

**Files**:
- `data/CreatePlanDraftStore.kt` — DataStore read/write helpers
- `viewmodel/CreatePlanViewModel.kt` — reads draft on init, saves on each field change

---

## 4. BigQuery Analytics — Create Plan Funnel

**What**: Track conversion through the Create Plan funnel (screen opened → date set → activity added → saved).

**How**: Firebase Analytics events sent at each funnel step. An `origin` parameter records whether the user arrived from "Explore" or "Activity Detail" so we can compare funnel completion rates by entry point.

**Events**:
| Event | Trigger | Params |
|-------|---------|--------|
| `create_plan_opened` | Screen enters composition | `origin: String` |
| `create_plan_date_set` | User confirms date | — |
| `create_plan_activity_added` | Activity toggled on | `activity_id: String` |
| `create_plan_saved` | `onSavePlan()` success | `activity_count: Int` |
| `create_plan_abandoned` | Back pressed before save | `filled_fields: Int` |

---

## 5. Caching — Screen-level Offline State

**What**: Login, Plan Detail, Invite, and RSVP screens show meaningful content or messages when offline, instead of blank states or crash-inducing null data.

**How**:
- Login: check `FirebaseAuth.currentUser` — if non-null, skip login and go straight to main app (session token cached by Firebase Auth SDK)
- Plan Detail / Invite / RSVP: Firestore cache serves last-known data; `OfflineBanner` informs user; write actions show `Queued` snackbar instead of error
