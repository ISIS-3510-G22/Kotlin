package com.example.plansync.data

import android.util.LruCache
import com.example.plansync.model.Participant

/**
 * Caching strategy — android.util.LruCache for user profiles fetched from Firestore.
 *
 * Key:     uid (String)
 * Value:   Pair<Participant, Long> — profile data + fetch timestamp (ms)
 * maxSize: 200 entries (each counted as 1 unit via the default sizeOf)
 *
 * TTL customization: entries are treated as stale after TTL_MS (10 min).
 * get() returns null for stale entries, forcing a fresh Firestore fetch.
 * This is distinct from a simple size-based eviction — staleness is time-driven.
 */
object UserProfileCache {

    private const val TTL_MS = 10 * 60 * 1000L

    private val cache = object : LruCache<String, Pair<Participant, Long>>(200) {
        override fun sizeOf(key: String, value: Pair<Participant, Long>) = 1
    }

    fun put(uid: String, participant: Participant) {
        cache.put(uid, Pair(participant, System.currentTimeMillis()))
    }

    fun get(uid: String): Participant? {
        val entry = cache.get(uid) ?: return null
        // TTL check — evict stale entries on access
        if (System.currentTimeMillis() - entry.second > TTL_MS) {
            cache.remove(uid)
            return null
        }
        return entry.first
    }
}
