# Eventual Connectivity — Decision Matrix

## Sprint 3 — PlanSync Android (Kotlin)

### What is eventual connectivity?
A design approach where the app remains functional without a live network connection. Writes are persisted locally and synced to the server once connectivity is restored. Reads serve cached data when offline.

---

## Screens × Operations Matrix

| Screen | Operation | Online behaviour | Offline behaviour | Strategy |
|--------|-----------|-----------------|-------------------|----------|
| Create Plan | Save plan | Writes to Firestore, navigates back | Firestore SDK queues write locally; user sees `WriteResult.Queued` toast | Firestore offline persistence (SDK) |
| Create Plan | Load activities | Fetches from Firestore | Serves Firestore SDK cache | Firestore offline persistence (SDK) |
| Plan Detail | Load plan | Real-time fetch from Firestore | Serves Firestore SDK cache | Firestore offline persistence (SDK) |
| Plan Detail | RSVP (going/can't make) | Writes response to Firestore | Queued by SDK | Firestore offline persistence (SDK) |
| Invite | Send invite | Writes `invitations` array union | Queued by SDK | Firestore offline persistence (SDK) |
| Login | Sign in | Firebase Auth network call | Fails with error; session token cache via `auth.currentUser` | Firebase Auth token cache |
| Explore | Load public plans | Fetches `isPublic==true` plans | Serves Firestore SDK cache | Firestore offline persistence (SDK) |
| My Plans | Load user plans | Fetches user's plans | Serves Firestore SDK cache | Firestore offline persistence (SDK) |

---

## NetworkMonitor usage

`NetworkMonitor.get(context).isOnline` is a `Flow<Boolean>` that ViewModels can collect to:
1. Show the `OfflineBanner` composable
2. Switch between `WriteResult.Committed` and `WriteResult.Queued` feedback

---

## Firestore offline persistence

Enabled in `MainActivity.onCreate()` via `PersistentCacheSettings` with unlimited cache size. This means:
- All reads transparently fall back to local cache when offline
- All writes (`.set()`, `.update()`, `.arrayUnion()`) are queued locally and auto-synced
- No extra code needed in repositories — SDK handles it

---

## Strategies not used (and why)

| Strategy | Reason not used |
|----------|----------------|
| Room / SQLite | Redundant given Firestore's own persistence layer |
| WorkManager sync | Firestore SDK handles re-sync automatically |
| Manual retry loops | Anti-pattern; SDK retries on its own |
